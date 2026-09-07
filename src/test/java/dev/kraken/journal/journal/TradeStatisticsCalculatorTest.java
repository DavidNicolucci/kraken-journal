package dev.kraken.journal.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.journal.dto.JournalStats;
import dev.kraken.journal.journal.dto.PairStats;
import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.kraken.dto.KrakenTrade;

class TradeStatisticsCalculatorTest {

    private final PairResolver resolver = new PairResolver();
    private final TradeStatisticsCalculator calculator = new TradeStatisticsCalculator(resolver);

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-04-01T00:00:00Z");

    private static KrakenTrade trade(String pair, String type, String price,
                                     String cost, String fee, String vol, long dayOffset) {
        BigDecimal time = BigDecimal.valueOf(
                FROM.plus(dayOffset, ChronoUnit.DAYS).getEpochSecond());
        return new KrakenTrade("O1", pair, time, type, "limit",
                new BigDecimal(price), new BigDecimal(cost), new BigDecimal(fee),
                new BigDecimal(vol), BigDecimal.ZERO);
    }

    private static PairStats find(JournalStats stats, String pair) {
        return stats.byPair().stream().filter(p -> p.pair().equals(pair)).findFirst().orElseThrow();
    }

    @Test
    void venditaSenzaAcquistoNonProduceUnPnlInventato() {
        var stats = calculator.compute(
                List.of(trade("LIGHTERUSD", "sell", "3.53", "14.122", "0.05649", "4.0", 1)),
                FROM, TO);

        PairStats p = find(stats, "LIGHTERUSD");
        assertNull(p.realizedPnl(), "senza costo di carico il P&L deve essere non calcolabile");
        assertFalse(p.pnlComplete());
        assertEquals(0, p.unmatchedSellVolume().compareTo(new BigDecimal("4.0")));
        assertEquals(1, stats.pairsWithIncompletePnl());
    }

    @Test
    void acquistoEVenditaCompletiDannoUnPnlReale() {
        var stats = calculator.compute(List.of(
                trade("SOLUSD", "buy", "100", "100.00", "0.00", "1.0", 1),
                trade("SOLUSD", "sell", "120", "120.00", "0.00", "1.0", 2)),
                FROM, TO);

        PairStats p = find(stats, "SOLUSD");
        assertTrue(p.pnlComplete());
        assertEquals(0, p.realizedPnl().compareTo(new BigDecimal("20")));
        assertEquals(0, p.openPosition().compareTo(BigDecimal.ZERO));
    }

    @Test
    void leConversioniValutarieNonContanoComeTrade() {
        var stats = calculator.compute(List.of(
                trade("ZEURZUSD", "sell", "1.17", "117.00", "0.23", "100.0", 1),
                trade("SOLUSD", "buy", "100", "100.00", "0.20", "1.0", 1)),
                FROM, TO);

        assertEquals(2, stats.totalOperations());
        assertEquals(1, stats.tradingTrades(), "solo SOLUSD e' un trade");
        assertEquals(1, stats.fxConversions());
        assertTrue(find(stats, "ZEURZUSD").fxConversion());
        assertFalse(find(stats, "SOLUSD").fxConversion());
    }

    @Test
    void iPrezziSottoUnCentesimoNonVengonoArrotondatiAZero() {
        var stats = calculator.compute(
                List.of(trade("AMIUSD", "sell", "0.00039", "4.20", "0.0168", "10590.2", 1)),
                FROM, TO);

        BigDecimal avg = find(stats, "AMIUSD").averageSellPrice();
        assertNotNull(avg);
        assertTrue(avg.compareTo(BigDecimal.ZERO) > 0, "prezzo arrotondato a zero: " + avg);
    }

    @Test
    void leCommissioniSulVolumeSonoSempreCalcolabili() {
        var stats = calculator.compute(
                List.of(trade("SOLUSD", "buy", "100", "100.00", "1.00", "1.0", 1)),
                FROM, TO);

        assertEquals(0, stats.feesToVolumePercent().compareTo(new BigDecimal("1.00")));
    }

    /**
     * Il rapporto commissioni/volume deve confrontare grandezze omogenee.
     * Includere le fee di cambio al numeratore mentre il denominatore contiene
     * solo il volume di trading gonfia il risultato con costi estranei.
     */
    @Test
    void leFeeDiCambioNonEntranoNelRapportoSulVolumeDiTrading() {
        var stats = calculator.compute(List.of(
                trade("ZEURZUSD", "sell", "1.17", "117.00", "0.30", "100.0", 1),
                trade("SOLUSD", "buy", "100", "100.00", "1.00", "1.0", 1)),
                FROM, TO);

        assertEquals(0, stats.fxFees().compareTo(new BigDecimal("0.30")));
        assertEquals(0, stats.tradingFees().compareTo(new BigDecimal("1.00")));
        assertEquals(0, stats.totalFees().compareTo(new BigDecimal("1.30")));
        assertEquals(0, stats.feesToVolumePercent().compareTo(new BigDecimal("1.00")),
                "il rapporto deve usare le sole fee di trading");
    }

    /** stripTrailingZeros trasforma 3000 in 3E+3, illeggibile una volta serializzato. */
    @Test
    void iNumeriInteriGrandiRestanoInFormaPiana() {
        var stats = calculator.compute(
                List.of(trade("AMIUSD", "sell", "0.0014", "4.20", "0.0168", "3000", 1)),
                FROM, TO);

        BigDecimal unmatched = find(stats, "AMIUSD").unmatchedSellVolume();
        assertTrue(unmatched.scale() >= 0, "notazione esponenziale: " + unmatched);
        assertEquals("3000", unmatched.toString());
    }
}
