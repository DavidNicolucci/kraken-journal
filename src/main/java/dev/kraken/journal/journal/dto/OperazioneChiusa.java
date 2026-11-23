package dev.kraken.journal.journal.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un giro completo: dalla prima entrata su una moneta a quando ne resta solo
 * polvere. E' l'unita' su cui ha senso misurare un metodo, la stessa del
 * registro: una posizione costruita con due acquisti e chiusa con tre vendite
 * e' un'operazione, non cinque.
 *
 * @param giorni giorni interi fra apertura e chiusura, da confrontare con il
 *        time stop della guida.
 * @param investito quanto e' costata la posizione, commissioni comprese.
 * @param risultato al netto delle commissioni. Null quando una parte e' stata
 *        venduta in una valuta diversa da quella di acquisto: senza un cambio
 *        il risultato non e' calcolabile, e non diventa zero.
 */
public record OperazioneChiusa(
        String coppia,
        String valuta,
        Instant apertura,
        Instant chiusura,
        long giorni,
        BigDecimal investito,
        BigDecimal risultato) {
}
