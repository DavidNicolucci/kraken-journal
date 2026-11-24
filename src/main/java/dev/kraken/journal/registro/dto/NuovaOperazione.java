package dev.kraken.journal.registro.dto;

import dev.kraken.journal.registro.Trigger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Quello che va scritto PRIMA di aprire un'operazione.
 *
 * Non esiste un campo facoltativo fra stop, obiettivo e motivo. Il punto non e'
 * la validazione in se': e' che il momento in cui si salta lo stop e' sempre
 * quello in cui si sta agendo di fretta, e in quel momento un campo
 * obbligatorio e' l'unica cosa che regge.
 *
 * Il rapporto non si manda: lo calcola il registro da entrata, stop e
 * obiettivo, come la scansione. Un valore scritto dal chiamante poteva non
 * avere niente a che fare con i tre prezzi accanto.
 *
 * @param coppia come la si scrive: XBTEUR, XBT/EUR o BTC/EUR finiscono tutte
 *        nel registro come XBTEUR, il nome della watchlist.
 * @param quantita obbligatoria per un'operazione reale: e' quella con cui si
 *        calcola il rischio, e quindi il risultato in R.
 * @param reale false per la carta. Predefinito, e va lasciato tale finche' il
 *        trigger non ha abbastanza registrazioni alle spalle.
 */
public record NuovaOperazione(
        @NotBlank String coppia,
        @NotNull Trigger trigger,
        @Positive double entrata,
        @Positive double stop,
        @Positive double obiettivo,
        @PositiveOrZero double quantita,
        @NotBlank @Size(max = 1000) String motivo,
        boolean reale) {
}
