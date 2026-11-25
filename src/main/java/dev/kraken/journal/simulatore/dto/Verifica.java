package dev.kraken.journal.simulatore.dto;

import java.time.Instant;

import dev.kraken.journal.simulatore.Verdetto;

/**
 * Come sarebbe andato il simulatore nell'ultimo anno, giorno per giorno,
 * usando ogni volta solo i dati di prima (walk-forward).
 *
 * Il confronto e' con la frequenza di base: "sale X% delle volte", detta
 * sempre, senza guardare lo stato. Un modello che non la batte non sa niente
 * piu' di chi conosce solo la media.
 *
 * @param casi giorni per coppia stimati
 * @param giorni giorni senza sovrapposizione usati per il margine: uno ogni
 *        orizzonte, con la media delle coppie di quel giorno
 * @param brierModello errore quadratico medio delle probabilita' del modello:
 *        0 e' perfetto, 0,25 e' dire sempre 50%
 * @param brierBase lo stesso per la frequenza di base
 * @param vantaggio di quanto il modello abbassa l'errore della base (positivo = meglio)
 * @param margine margine al 95% sul vantaggio; null sotto i due giorni
 * @param precisioneModello quota di direzioni giuste, prendendo come rialzo una stima dal 50% in su
 * @param precisioneBase la stessa per la base
 * @param frequenzaRialzo quante volte e' salito davvero nel periodo verificato
 */
public record Verifica(
        Instant dal,
        int casi,
        int giorni,
        double brierModello,
        double brierBase,
        double vantaggio,
        Double margine,
        double precisioneModello,
        double precisioneBase,
        double frequenzaRialzo,
        Verdetto verdetto) {
}
