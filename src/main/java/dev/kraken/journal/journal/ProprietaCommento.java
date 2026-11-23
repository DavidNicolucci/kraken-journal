package dev.kraken.journal.journal;

import java.time.LocalDate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Le soglie del commento del journal. Come per la scansione: cambiarle e'
 * cambiare il modo di leggere i dati, quindi stanno in configurazione e non
 * dentro un if.
 *
 * @param metodoDal da questa data gli acquisti e le posizioni si confrontano
 *        con il metodo (watchlist, registro, dimensione, time stop, posizioni
 *        massime, ordini al giorno). Prima il metodo non c'era, e giudicarci
 *        sopra direbbe solo che non c'era.
 * @param quotaTakerMassima percentuale del volume di Kraken Pro eseguito a
 *        tariffa taker oltre cui il commento lo fa notare.
 * @param concentrazioneMassima percentuale del volume di una valuta su una
 *        sola coppia oltre cui il commento lo fa notare.
 * @param ordiniAlGiornoMassimi sopra questo numero di ordini un giorno conta
 *        come "troppi ordini": una revisione al giorno da' al massimo
 *        un'uscita e un'entrata.
 * @param giorniRegistrazione quanti giorni prima di un acquisto puo' stare la
 *        sua registrazione nel registro.
 */
@ConfigurationProperties(prefix = "commento")
public record ProprietaCommento(
        LocalDate metodoDal,
        double quotaTakerMassima,
        double concentrazioneMassima,
        int ordiniAlGiornoMassimi,
        int giorniRegistrazione) {

    public ProprietaCommento {
        if (metodoDal == null) {
            throw new IllegalArgumentException("commento.metodo-dal va indicato: da quando giudicare gli acquisti");
        }
    }
}
