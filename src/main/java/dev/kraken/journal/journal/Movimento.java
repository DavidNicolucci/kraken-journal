package dev.kraken.journal.journal;

import java.math.BigDecimal;
import java.time.Instant;

import dev.kraken.journal.kraken.PairResolver;

/**
 * Un acquisto o una vendita, da qualunque strada sia passato.
 *
 * Esiste perche' le operazioni arrivano da due fonti con forme diverse: gli
 * ordini di Kraken Pro da TradesHistory, quelle fatte dall'app dal ledger. Il
 * calcolo ragiona su questa forma sola e non deve sapere da dove viene un dato.
 *
 * @param idOrdine uguale per tutte le esecuzioni dello stesso ordine: un ordine
 *        limite riempito in tre pezzi sono tre movimenti e un ordine solo.
 * @param maker true se l'ordine era in coda sul book, false se e' stato
 *        eseguito subito, null quando non si sa (app, dati vecchi).
 * @param quantita nella moneta scambiata, al netto di una commissione pagata
 *        in quella moneta.
 * @param controvalore lordo, nella valuta di quotazione.
 * @param commissione nella valuta di quotazione.
 */
public record Movimento(
        Instant istante,
        String idOrdine,
        PairResolver.Pair coppia,
        boolean acquisto,
        Origine origine,
        Boolean maker,
        BigDecimal quantita,
        BigDecimal controvalore,
        BigDecimal commissione) {

    public Movimento {
        quantita = zeroSeManca(quantita);
        controvalore = zeroSeManca(controvalore);
        commissione = zeroSeManca(commissione);
    }

    private static BigDecimal zeroSeManca(BigDecimal valore) {
        return valore == null ? BigDecimal.ZERO : valore;
    }
}
