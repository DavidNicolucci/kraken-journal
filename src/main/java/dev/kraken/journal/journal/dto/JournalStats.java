package dev.kraken.journal.journal.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * I contatori e i totali riguardano il periodo richiesto. Le posizioni e il
 * costo di carico no: sono ricostruiti su tutto lo storico del conto, perche'
 * il costo di una vendita di oggi dipende da un acquisto che puo' essere
 * precedente al periodo.
 *
 * @param totalOperations esecuzioni del periodo, conversioni comprese.
 * @param tradingTrades esecuzioni di trading. Per contare le decisioni e non
 *        i pezzi in cui Kraken le ha eseguite, usare {@code ordini}.
 * @param tradingDays giorni con operazioni, nel fuso del lavoro giornaliero.
 * @param tradingFees commissioni sui soli trade. @param fxFees commissioni
 *        sulle conversioni valutarie: sono un costo separato, non legato alle
 *        scelte di mercato.
 * @param feesToVolumePercent tradingFees sul volume di trading. Numeratore e
 *        denominatore devono avere la stessa base: usare totalFees qui
 *        gonfierebbe il rapporto con costi estranei al trading.
 * @param realizedPnlComplete somma del P&L delle sole coppie con costo di
 *        carico ricostruibile per intero. Le altre sono escluse, non sommate
 *        a zero.
 * @param mixedQuoteCurrencies true se le coppie hanno valute di quotazione
 *        diverse: in quel caso volumi, commissioni e P&L qui sopra sommano
 *        importi in valute diverse e vanno letti come ordine di grandezza. I
 *        numeri esatti sono in {@code totaliPerValuta}.
 * @param ledgerLetto false se la chiave non ha potuto leggere il ledger:
 *        mancano le operazioni fatte dall'app.
 * @param ordini gli ordini del periodo, conversioni comprese.
 * @param totaliPerValuta i totali di trading, una riga per valuta.
 * @param operazioniChiuse i giri completi chiusi nel periodo.
 * @param posizioniAperte quello che e' in carico adesso.
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
        List<PairStats> byPair,
        boolean ledgerLetto,
        List<Ordine> ordini,
        List<TotaliValuta> totaliPerValuta,
        List<OperazioneChiusa> operazioniChiuse,
        List<PosizioneAperta> posizioniAperte
) {
}
