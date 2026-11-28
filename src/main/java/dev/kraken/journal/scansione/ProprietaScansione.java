package dev.kraken.journal.scansione;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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
 * @param giorniBreakout il trigger provvisorio: la chiusura deve superare il
 *        massimo di questi giorni, ultima candela esclusa.
 * @param multiploVolumeBreakout e il controvalore della rottura deve essere
 *        almeno questo multiplo della media degli stessi giorni.
 * @param controlliAttivi i controlli della checklist che decidono se una
 *        coppia e' idonea. Gli altri non vengono nemmeno valutati, ne' dalla
 *        scansione ne' dalla verifica storica. Se manca, valgono tutti: e'
 *        il metodo com'era prima che esistesse questa voce.
 */
@ConfigurationProperties(prefix = "scansione")
public record ProprietaScansione(
        List<String> watchlist,
        double volumeMinimo,
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
        TipoOrdine tipoOrdine,
        int giorniBreakout,
        double multiploVolumeBreakout,
        Set<Controllo> controlliAttivi) {

    public ProprietaScansione {
        if (watchlist == null || watchlist.isEmpty()) {
            throw new IllegalArgumentException("La watchlist non puo' essere vuota");
        }
        // Controllati all'avvio: un valore fuori misura farebbe saltare ogni coppia
        // a ogni scansione, con un avviso nel log invece di un errore chiaro.
        int massimo = CalcolatoreIndicatori.CANDELE_MINIME;
        if (giorniMinimoStop <= 0 || giorniMinimoStop > massimo || giorniBreakout <= 0 || giorniBreakout >= massimo) {
            throw new IllegalArgumentException("scansione.giorni-minimo-stop va da 1 a " + massimo
                    + " e scansione.giorni-breakout da 1 a " + (massimo - 1) + ": la scansione legge " + massimo + " candele");
        }
        if (multiploVolumeBreakout <= 0) {
            throw new IllegalArgumentException("scansione.multiplo-volume-breakout va indicato");
        }
        if (tipoOrdine == null) {
            tipoOrdine = TipoOrdine.LIMITE;
        }
        controlliAttivi = controlliAttivi == null || controlliAttivi.isEmpty()
                ? EnumSet.allOf(Controllo.class) : EnumSet.copyOf(controlliAttivi);
    }

    /** Quanto si e' disposti a perdere su una singola operazione. */
    public double rischioAmmesso() {
        return capitale * rischioPerOperazione / 100;
    }
}
