package dev.kraken.journal.journal.dto;

import java.math.BigDecimal;
import java.util.Set;

import dev.kraken.journal.journal.Provenienza;

/**
 * Metriche per singola coppia, calcolate in Java. Nessuno di questi numeri
 * viene prodotto dal modello.
 *
 * Una coppia qui e' moneta piu' valuta (SOL/EUR), da qualunque strada siano
 * passate le operazioni: un acquisto dall'app e una vendita su Kraken Pro
 * della stessa moneta nella stessa valuta stanno sulla stessa riga.
 *
 * @param tradeCount    esecuzioni, non ordini: vedi {@code ordini}.
 * @param realizedPnl   risultato delle vendite che hanno trovato il loro
 *                      acquisto, nella stessa valuta o convertito al cambio
 *                      del giorno. Zero se nel periodo non si e' venduto; null
 *                      se si e' venduto ma nessuna parte ha un acquisto
 *                      corrispondente. Un null qui significa
 *                      "non calcolabile", non "zero": riportare un numero
 *                      inventato sarebbe peggio che non riportarne nessuno.
 * @param pnlComplete   false se una parte delle vendite non ha un costo di
 *                      carico: in quel caso realizedPnl copre solo il resto.
 * @param unmatchedSellVolume quantita' venduta senza che in tutto lo storico
 *                      ci sia un acquisto: vedi {@code provenienze}.
 * @param fxConversion  true per i cambi valuta (EUR/USD): non sono trade e
 *                      vanno esclusi dalle statistiche operative.
 * @param openPosition  quantita' in carico adesso, non a fine periodo.
 * @param ordini        ordini distinti del periodo.
 * @param operazioniDaApp quante delle esecuzioni sono passate dall'app.
 * @param volumeTaker   volume eseguito subito, alla tariffa piu' alta.
 * @param quantitaInAltraValuta quantita' venduta qui ma comprata in un'altra
 *                      valuta, in un giorno senza cambio: esce dal carico, ma
 *                      non ha un risultato.
 * @param quantitaConvertita quantita' venduta qui e comprata in un'altra
 *                      valuta, con il costo convertito al cambio del giorno
 *                      della vendita: e' nel risultato.
 * @param costoResiduo  costo di carico della quantita' rimasta, commissioni
 *                      comprese.
 * @param provenienze   come sono arrivate, secondo il ledger, le quantita'
 *                      vendute senza acquisto. Vuoto se il ledger non lo dice.
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
        BigDecimal openPosition,
        int ordini,
        int operazioniDaApp,
        BigDecimal volumeTaker,
        BigDecimal quantitaInAltraValuta,
        BigDecimal quantitaConvertita,
        BigDecimal costoResiduo,
        Set<Provenienza> provenienze
) {
}
