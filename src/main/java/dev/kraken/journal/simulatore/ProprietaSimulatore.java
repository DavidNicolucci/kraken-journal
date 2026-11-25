package dev.kraken.journal.simulatore;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Le scelte del simulatore. Le coppie no: sono la watchlist della scansione,
 * perche' una seconda lista divergerebbe dalla prima al primo cambio.
 *
 * @param orizzonte fra quante candele giornaliere si guarda il prezzo
 * @param giorniVerifica su quanti giorni recenti si verifica il modello; le
 *        candele piu' vecchie servono solo a dargli una storia da cui partire
 * @param casiMinimi sotto quanti casi uno stato e' troppo raro per avere una
 *        frequenza sua, e si usa quella di tutti i giorni
 */
@ConfigurationProperties(prefix = "simulatore")
public record ProprietaSimulatore(int orizzonte, int giorniVerifica, int casiMinimi) {

    public ProprietaSimulatore {
        if (orizzonte < 1 || orizzonte > CalcolatoreSimulazione.RISCALDAMENTO) {
            throw new IllegalArgumentException("simulatore.orizzonte va da 1 a " + CalcolatoreSimulazione.RISCALDAMENTO);
        }
        if (giorniVerifica < 1 || casiMinimi < 1) {
            throw new IllegalArgumentException("simulatore.giorni-verifica e simulatore.casi-minimi vanno indicati");
        }
    }
}
