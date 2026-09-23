package dev.kraken.journal.scansione;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Le soglie della guida operativa, in un posto solo.
 *
 * Stanno nella configurazione e non nel codice per un motivo preciso: sono
 * decisioni di metodo, e una decisione di metodo va cambiata consapevolmente,
 * in un file che si legge tutto insieme, non ritoccata dentro un if.
 *
 * @param watchlist coppie da esaminare, congelate a una data. Ricostruirla ogni
 *        volta con le coppie piu' scambiate del giorno introdurrebbe un
 *        survivorship bias: si starebbe scegliendo chi e' sopravvissuto.
 * @param volumeMinimo controvalore minimo scambiato in 24 ore, nella valuta di
 *        quotazione della coppia. Non serve a sapere se "girano capitali
 *        grossi": serve a escludere i book sottili, dove gli indicatori
 *        descrivono il rumore di poche mani.
 * @param giorniMinimoStop su quante candele cercare il minimo che fa da stop.
 * @param margineStop quanto scendere sotto quel minimo, in percentuale.
 * @param giorniTimeStop dopo quanti giorni si chiude comunque un'operazione
 *        che non e' andata ne' allo stop ne' all'obiettivo. Sta qui e non nel
 *        registro perche' e' una regola di metodo, e le regole di metodo
 *        vivono in un posto solo.
 * @param registrazioniPerGiudizio quante operazioni chiuse servono prima di
 *        poter dire qualcosa su una regola. Sotto questo numero il risultato
 *        medio e' rumore, e il registro lo dichiara invece di lasciarlo
 *        interpretare.
 * @param posizioniMassime quante posizioni VERE si tengono aperte insieme.
 *        Non vale per le analisi su carta: quelle non costano commissioni e
 *        non corrono rischio, quindi limitarle rallenterebbe solo la misura.
 */
@ConfigurationProperties(prefix = "scansione")
public record ProprietaScansione(
        List<String> watchlist,
        double volumeMinimo,
        double rsiMinimo,
        double rsiMassimo,
        double distanzaMassimaDallaMedia,
        double ampiezzaStopMinima,
        double ampiezzaStopMassima,
        double multiploObiettivo,
        double capitale,
        double rischioPerOperazione,
        int giorniMinimoStop,
        double margineStop,
        int giorniTimeStop,
        int registrazioniPerGiudizio,
        int posizioniMassime,
        TipoOrdine tipoOrdine) {

    public ProprietaScansione {
        if (watchlist == null || watchlist.isEmpty()) {
            throw new IllegalArgumentException("La watchlist non puo' essere vuota");
        }
        if (tipoOrdine == null) {
            tipoOrdine = TipoOrdine.LIMITE;
        }
    }

    /** Quanto si e' disposti a perdere su una singola operazione. */
    public double rischioAmmesso() {
        return capitale * rischioPerOperazione / 100;
    }
}
