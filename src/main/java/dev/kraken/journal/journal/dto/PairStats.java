package dev.kraken.journal.journal.dto;

import java.math.BigDecimal;

/**
 * Metriche per singola coppia, calcolate in Java. Nessuno di questi numeri
 * viene prodotto dal modello.
 *
 * @param realizedPnl   null quando il costo di carico non e' ricostruibile.
 *                      Un null qui significa "non calcolabile", non "zero":
 *                      riportare un numero inventato sarebbe peggio che non
 *                      riportarne nessuno.
 * @param unmatchedSellVolume quantita' venduta senza acquisto corrispondente
 *                      nel periodo. Se > 0 la posizione esisteva gia' prima.
 * @param fxConversion  true per i cambi valuta (EUR/USD): non sono trade e
 *                      vanno esclusi dalle statistiche operative.
 */
public record PairStats(
        String pair,
        String displayName,
        String quoteCurrency,
        boolean fxConversion,
        int tradeCount,
        int buyCount,
        int sellCount,
        BigDecimal volumeQuote,
        BigDecimal feesPaid,
        BigDecimal feesToVolumePercent,
        BigDecimal realizedPnl,
        boolean pnlComplete,
        BigDecimal unmatchedSellVolume,
        BigDecimal averageBuyPrice,
        BigDecimal averageSellPrice,
        BigDecimal openPosition
) {
}
