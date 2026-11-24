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
 * @param pairsWithIncompletePnl coppie con vendite senza un costo di carico:
 *        il loro risultato per valuta e' parziale.
 * @param mixedQuoteCurrencies true se le coppie hanno valute di quotazione
 *        diverse. Volumi, commissioni e risultato sono solo in
 *        {@code totaliPerValuta}, una riga per valuta: i totali che sommavano
 *        EUR e USD sono stati tolti, perche' un numero sbagliato con l'aria
 *        giusta e' peggio di nessun numero.
 * @param storicoCompleto false se Kraken ha restituito solo le voci piu'
 *        recenti (tetto di pagine raggiunto): le vendite di monete comprate
 *        prima risultano senza costo di carico.
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
        int pairsWithIncompletePnl,
        boolean mixedQuoteCurrencies,
        List<String> quoteCurrencies,
        List<PairStats> byPair,
        boolean ledgerLetto,
        boolean storicoCompleto,
        List<Ordine> ordini,
        List<TotaliValuta> totaliPerValuta,
        List<OperazioneChiusa> operazioniChiuse,
        List<PosizioneAperta> posizioniAperte
) {
}
