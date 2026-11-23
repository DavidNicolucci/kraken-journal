package dev.kraken.journal.journal.dto;

import java.math.BigDecimal;
import java.time.Instant;

import dev.kraken.journal.journal.Origine;

/**
 * Un ordine del periodo, con tutte le sue esecuzioni sommate.
 *
 * E' l'unita' giusta per parlare di comportamento: decidere di comprare e'
 * una decisione sola anche se Kraken la esegue in tre pezzi. Contare le
 * esecuzioni gonfiava il numero di operazioni e la frequenza.
 *
 * @param taker true se almeno una parte e' stata eseguita subito, false se
 *        tutto l'ordine era in coda sul book, null quando non si sa (app).
 * @param quantita nella moneta scambiata.
 * @param controvalore lordo, nella valuta di quotazione.
 */
public record Ordine(
        String id,
        Instant istante,
        String coppia,
        String moneta,
        String valuta,
        boolean acquisto,
        boolean conversione,
        Origine origine,
        Boolean taker,
        int esecuzioni,
        BigDecimal quantita,
        BigDecimal controvalore,
        BigDecimal commissione) {
}
