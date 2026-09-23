package dev.kraken.journal.scansione.dto;

import java.time.Instant;
import java.util.List;

/**
 * Esito di una scansione completa.
 *
 * @param esaminate quante coppie sono state valutate davvero. Se e' minore
 *        della watchlist, qualcuna ha fallito: il numero lo rende evidente
 *        invece di lasciare che una coppia sparisca in silenzio.
 * @param candidati ordinati per controlli superati, poi per controvalore. Chi
 *        e' in cima NON e' il migliore: e' quello che oggi soddisfa piu'
 *        condizioni.
 */
public record RispostaScansione(
        Instant istante,
        int esaminate,
        int inWatchlist,
        List<Candidato> candidati) {
}
