package dev.kraken.journal.journal.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una posizione in carico adesso, qualunque sia il periodo guardato: una
 * moneta comprata sei mesi fa e mai venduta e' aperta oggi anche se nella
 * finestra di trenta giorni non c'e' nessuna sua operazione.
 *
 * @param costo costo di carico della quantita' rimasta, commissioni comprese.
 *        Non e' il valore di oggi: quello dipende dal prezzo di mercato.
 * @param apertaDal la prima entrata dell'operazione in corso.
 */
public record PosizioneAperta(
        String coppia,
        String moneta,
        String valuta,
        BigDecimal quantita,
        BigDecimal costo,
        Instant apertaDal) {
}
