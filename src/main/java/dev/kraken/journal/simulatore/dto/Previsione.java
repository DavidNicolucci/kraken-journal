package dev.kraken.journal.simulatore.dto;

import java.time.Instant;

import dev.kraken.journal.simulatore.Direzione;
import dev.kraken.journal.simulatore.Regime;

/**
 * La stima per una coppia, all'ultima candela giornaliera chiusa.
 *
 * @param probabilitaRialzo quante volte, nel passato di tutta la watchlist, da
 *        uno stato come questo il prezzo era piu' alto dopo l'orizzonte. Fra 0 e 1.
 * @param minimo estremo basso dell'intervallo di Wilson al 95%
 * @param massimo estremo alto
 * @param casi su quanti giorni passati e' contata la frequenza
 * @param indipendenti quanti di quei giorni non si sovrappongono: i casi a un
 *        giorno di distanza condividono sei giorni su sette dello stesso
 *        movimento, e contarli tutti farebbe sembrare l'intervallo stretto
 * @param dallaBase true se lo stato di oggi e' troppo raro: allora la stima e'
 *        la frequenza di rialzo di tutti i giorni, non quella dello stato
 * @param momento60 rendimento degli ultimi 60 giorni, in percentuale
 * @param prezzoBasso il 5° percentile dei movimenti a orizzonte di questa coppia
 *        nell'ultimo anno, applicato alla chiusura
 * @param prezzoMediano il 50°
 * @param prezzoAlto il 95°
 */
public record Previsione(
        String coppia,
        Instant istanteCandela,
        double chiusura,
        Regime regime,
        double rsi,
        double momento60,
        double probabilitaRialzo,
        double minimo,
        double massimo,
        int casi,
        int indipendenti,
        boolean dallaBase,
        Direzione direzione,
        double prezzoBasso,
        double prezzoMediano,
        double prezzoAlto) {
}
