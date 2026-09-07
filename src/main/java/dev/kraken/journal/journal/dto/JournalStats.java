package dev.kraken.journal.journal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @param tradingFees commissioni sui soli trade. @param fxFees commissioni
 *        sulle conversioni valutarie: sono un costo separato, non legato alle
 *        scelte di mercato.
 * @param feesToVolumePercent tradingFees sul volume di trading. Numeratore e
 *        denominatore devono avere la stessa base: usare totalFees qui
 *        gonfierebbe il rapporto con costi estranei al trading.
 * @param realizedPnlComplete somma del P&L delle sole coppie con costo di
 *        carico ricostruibile. Le altre sono escluse, non sommate a zero.
 * @param mixedQuoteCurrencies true se le coppie hanno valute di quotazione
 *        diverse: in quel caso volumi, commissioni e P&L aggregati sommano
 *        importi in valute diverse e vanno letti come ordine di grandezza.
 */
public record JournalStats(
        Instant periodStart,
        Instant periodEnd,
        int totalOperations,
        int tradingTrades,
        int fxConversions,
        int tradingDays,
        BigDecimal tradesPerActiveDay,
        BigDecimal tradingVolumeQuote,
        BigDecimal totalFees,
        BigDecimal tradingFees,
        BigDecimal fxFees,
        BigDecimal feesToVolumePercent,
        BigDecimal realizedPnlComplete,
        int pairsWithIncompletePnl,
        boolean mixedQuoteCurrencies,
        List<String> quoteCurrencies,
        List<PairStats> byPair
) {
}
