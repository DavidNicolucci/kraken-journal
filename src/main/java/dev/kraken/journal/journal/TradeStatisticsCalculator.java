package dev.kraken.journal.journal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.kraken.journal.journal.dto.JournalStats;
import dev.kraken.journal.journal.dto.PairStats;
import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.kraken.dto.KrakenTrade;

/**
 * Calcolo deterministico delle metriche del journal.
 *
 * Tre scelte che vale la pena conoscere prima di leggere i numeri:
 *
 * 1. P&L a costo medio ponderato. NON e' il criterio fiscale italiano, che per
 *    le cripto indica il LIFO. Serve a capire come hai operato, non a compilare
 *    la dichiarazione.
 *
 * 2. Quando si vende senza un acquisto corrispondente nel periodo, il costo di
 *    carico e' ignoto e il P&L di quella coppia viene marcato non calcolabile
 *    (null) invece di essere approssimato. Una versione precedente sottraeva
 *    solo le commissioni, producendo un P&L che sembrava reale ed era invece
 *    un artefatto: quel numero portava a leggere "ho perso esattamente le fee",
 *    che non era mai stato vero.
 *
 * 3. Le conversioni valutarie (EUR/USD) sono contate a parte. Non sono trade
 *    e includerle gonfia frequenza e conteggi.
 */
@Component
public class TradeStatisticsCalculator {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final MathContext PRICE_MC = new MathContext(6, RoundingMode.HALF_UP);
    private static final int SCALE = 8;

    private final PairResolver pairResolver;

    public TradeStatisticsCalculator(PairResolver pairResolver) {
        this.pairResolver = pairResolver;
    }

    public JournalStats compute(List<KrakenTrade> trades, Instant from, Instant to) {
        Map<String, List<KrakenTrade>> grouped = trades.stream()
                .collect(Collectors.groupingBy(KrakenTrade::pair, LinkedHashMap::new, Collectors.toList()));

        List<PairStats> pairStats = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            pairStats.add(computePair(entry.getKey(), entry.getValue()));
        }
        pairStats.sort(Comparator.comparing(PairStats::volumeQuote).reversed());

        List<PairStats> tradingOnly = pairStats.stream().filter(p -> !p.fxConversion()).toList();

        int tradingTrades = tradingOnly.stream().mapToInt(PairStats::tradeCount).sum();
        int fxConversions = pairStats.stream().filter(PairStats::fxConversion)
                .mapToInt(PairStats::tradeCount).sum();

        List<PairStats> fxOnly = pairStats.stream().filter(PairStats::fxConversion).toList();

        BigDecimal tradingVolume = sum(tradingOnly, PairStats::volumeQuote);
        BigDecimal tradingFees = sum(tradingOnly, PairStats::feesPaid);
        BigDecimal fxFees = sum(fxOnly, PairStats::feesPaid);
        BigDecimal totalFees = tradingFees.add(fxFees);

        BigDecimal completePnl = tradingOnly.stream()
                .filter(PairStats::pnlComplete)
                .map(PairStats::realizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int incomplete = (int) tradingOnly.stream().filter(p -> !p.pnlComplete()).count();

        // Solo i giorni con operativita' vera: le conversioni valutarie non
        // sono giornate di trading.
        Set<?> activeDays = trades.stream()
                .filter(t -> !pairResolver.resolve(t.pair()).fxConversion())
                .map(t -> t.timestamp().atZone(ZoneOffset.UTC).toLocalDate())
                .collect(Collectors.toSet());

        BigDecimal perDay = activeDays.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(tradingTrades)
                    .divide(BigDecimal.valueOf(activeDays.size()), 2, RoundingMode.HALF_UP);

        Set<String> quotes = pairStats.stream()
                .map(PairStats::quoteCurrency)
                .collect(Collectors.toCollection(TreeSet::new));

        return new JournalStats(
                from, to,
                trades.size(),
                tradingTrades,
                fxConversions,
                activeDays.size(),
                perDay,
                scaled(tradingVolume),
                scaled(totalFees),
                scaled(tradingFees),
                scaled(fxFees),
                percentOf(tradingFees, tradingVolume),
                incomplete == tradingOnly.size() && incomplete > 0 ? null : scaled(completePnl),
                incomplete,
                quotes.size() > 1,
                List.copyOf(quotes),
                pairStats
        );
    }

    private PairStats computePair(String rawPair, List<KrakenTrade> trades) {
        PairResolver.Pair pair = pairResolver.resolve(rawPair);

        BigDecimal position = BigDecimal.ZERO;   // quantita' a carico
        BigDecimal costBasis = BigDecimal.ZERO;  // costo della posizione
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal unmatchedSells = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;

        BigDecimal buyVol = BigDecimal.ZERO, buyValue = BigDecimal.ZERO;
        BigDecimal sellVol = BigDecimal.ZERO, sellValue = BigDecimal.ZERO;
        int buys = 0, sells = 0;

        for (KrakenTrade t : trades) {
            fees = fees.add(nz(t.fee()));
            volume = volume.add(nz(t.cost()));

            if (t.isBuy()) {
                buys++;
                buyVol = buyVol.add(nz(t.vol()));
                buyValue = buyValue.add(nz(t.cost()));
                position = position.add(nz(t.vol()));
                costBasis = costBasis.add(nz(t.cost())).add(nz(t.fee()));
            } else {
                sells++;
                BigDecimal soldQty = nz(t.vol());
                sellVol = sellVol.add(soldQty);
                sellValue = sellValue.add(nz(t.cost()));

                BigDecimal matched = soldQty.min(position.max(BigDecimal.ZERO));

                if (matched.signum() > 0) {
                    BigDecimal avgCost = costBasis.divide(position, MC);
                    BigDecimal costOfSold = avgCost.multiply(matched, MC);
                    BigDecimal proceeds = nz(t.cost())
                            .multiply(matched.divide(soldQty, MC), MC)
                            .subtract(nz(t.fee()).multiply(matched.divide(soldQty, MC), MC));
                    realized = realized.add(proceeds.subtract(costOfSold));
                    position = position.subtract(matched);
                    costBasis = costBasis.subtract(costOfSold);
                }
                // La parte non coperta non produce P&L: il costo di carico e'
                // ignoto. La registro per poterlo dichiarare esplicitamente.
                unmatchedSells = unmatchedSells.add(soldQty.subtract(matched));
            }
        }

        boolean pnlComplete = unmatchedSells.signum() == 0;

        return new PairStats(
                rawPair,
                pair.display(),
                pair.quote(),
                pair.fxConversion(),
                trades.size(), buys, sells,
                scaled(volume),
                scaled(fees),
                percentOf(fees, volume),
                pnlComplete ? scaled(realized) : null,
                pnlComplete,
                scaled(unmatchedSells),
                price(buyValue, buyVol),
                price(sellValue, sellVol),
                scaled(position)
        );
    }

    /**
     * Scala dinamica: un token che vale frazioni di centesimo arrotondato a due
     * decimali diventa 0.00, che si legge come "prezzo zero" invece che come
     * "arrotondamento". Sotto l'unita' uso cifre significative.
     */
    private static BigDecimal price(BigDecimal value, BigDecimal qty) {
        if (qty == null || qty.signum() == 0) return null;
        BigDecimal p = value.divide(qty, MC);
        if (p.abs().compareTo(BigDecimal.ONE) >= 0) {
            return p.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal rounded = p.round(PRICE_MC).stripTrailingZeros();
        return rounded.scale() < 0 ? rounded.setScale(0, RoundingMode.UNNECESSARY) : rounded;
    }

    private static BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return null;
        return part.divide(whole, 6, RoundingMode.HALF_UP)
                   .multiply(BigDecimal.valueOf(100))
                   .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<PairStats> stats,
                                  java.util.function.Function<PairStats, BigDecimal> f) {
        return stats.stream().map(f).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal scaled(BigDecimal v) {
        if (v == null) return null;
        BigDecimal s = v.setScale(SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
        if (s.signum() == 0) return BigDecimal.ZERO;
        // scale negativa significa notazione esponenziale in JSON (3E+3): la
        // riporto a zero cosi' il numero resta leggibile in forma piana.
        return s.scale() < 0 ? s.setScale(0, RoundingMode.UNNECESSARY) : s;
    }
}
