package dev.kraken.journal.scansione.dto;

import dev.kraken.journal.scansione.Controllo;
import dev.kraken.journal.scansione.Esito;

/**
 * Esito di un controllo, con accanto il numero che lo ha prodotto.
 *
 * Il valore e la soglia viaggiano insieme all'esito apposta: un "BOCCIATO"
 * senza il numero costringe a fidarsi, e il punto di questo programma e'
 * esattamente il contrario.
 */
public record EsitoControllo(Controllo controllo, Esito esito, String valore, String soglia) {

    public static EsitoControllo di(Controllo controllo, boolean superato, String valore, String soglia) {
        return new EsitoControllo(controllo, Esito.di(superato), valore, soglia);
    }
}
