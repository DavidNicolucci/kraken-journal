package dev.kraken.journal.giornaliero;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.kraken.journal.registro.ServizioRegistro;
import dev.kraken.journal.registro.Trigger;
import dev.kraken.journal.registro.dto.NuovaOperazione;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.scansione.ServizioScansione;
import dev.kraken.journal.scansione.dto.Candidato;

/**
 * Il lavoro delle sette del mattino: prima chiude quello che e' successo
 * stanotte, poi guarda cosa c'e' oggi e lo registra su carta.
 *
 * L'ordine non e' casuale. Risolvere prima significa che la scansione trova un
 * registro gia' aggiornato e che il conteggio delle posizioni aperte e' vero.
 *
 * Perche' le sette e perche' con la zona oraria dichiarata: le candele
 * giornaliere di Kraken chiudono a mezzanotte UTC, cioe' all'una o alle due qui
 * a seconda della stagione. Alle sette la candela di ieri e' chiusa da un
 * pezzo, sempre. La zona oraria va indicata esplicitamente nel cron: senza,
 * Spring userebbe il fuso di sistema e con l'ora legale il lavoro si
 * sposterebbe di un'ora due volte l'anno.
 *
 * REGISTRA TUTTE LE IDONEE SU CARTA, non solo quelle che si potrebbero
 * davvero aprire. E' la regola 2.3: il rischio si limita a due posizioni, la
 * misura no. La carta non costa commissioni, quindi non c'e' ragione di
 * buttare via un'osservazione solo perche' il capitale e' gia' impegnato.
 *
 * Se il computer e' spento alle sette il lavoro salta. Il risolutore recupera
 * da solo, perche' riparte sempre dalla data di registrazione; le idonee di
 * quel giorno invece si perdono, ed e' il motivo per cui esiste l'avvio a mano.
 */
@Component
public class LavoroGiornaliero {

    private static final Logger log = LoggerFactory.getLogger(LavoroGiornaliero.class);

    private final ServizioScansione servizioScansione;
    private final ServizioRegistro servizioRegistro;
    private final ProprietaScansione proprieta;

    public LavoroGiornaliero(ServizioScansione servizioScansione, ServizioRegistro servizioRegistro,
                             ProprietaScansione proprieta) {
        this.servizioScansione = servizioScansione;
        this.servizioRegistro = servizioRegistro;
        this.proprieta = proprieta;
    }

    /**
     * Il metodo pianificato non restituisce niente ed e' separato da quello che
     * fa il lavoro: Spring tratta in modo speciale i valori di ritorno dei
     * metodi annotati, e un metodo void toglie ogni ambiguita'.
     */
    @Scheduled(cron = "${giornaliero.cron}", zone = "${giornaliero.fuso}")
    void eseguiPianificato() {
        esegui();
    }

    public EsitoLavoro esegui() {
        int chiuse = servizioRegistro.risolviAperte();

        // Ordinate per rotazione: prima le coppie su cui si e' operato meno.
        // Chi esce per primo e' la candidata da prendere per davvero, se c'e'
        // spazio nelle due posizioni. Non e' una previsione, e' un criterio che
        // allarga il campione invece di concentrarlo (regola 2.3).
        List<Candidato> idonee = servizioScansione.scansiona().candidati().stream()
                .filter(Candidato::tuttiSuperati)
                .sorted(Comparator.comparingLong(c -> servizioRegistro.quanteVolteRegistrata(c.coppia())))
                .toList();

        long registrate = idonee.stream().filter(c -> !servizioRegistro.giaApertaSu(c.coppia()))
                .filter(c -> servizioRegistro.registraSuCarta(daCandidato(c)) != null)
                .count();

        long aperteReali = servizioRegistro.posizioniRealiAperte();
        if (aperteReali >= proprieta.posizioniMassime() && !idonee.isEmpty()) {
            log.info("Posizioni reali gia' al massimo ({}): le idonee di oggi restano solo su carta",
                    proprieta.posizioniMassime());
        }
        log.info("Lavoro giornaliero: {} operazioni chiuse, {} coppie idonee, {} registrate su carta",
                chiuse, idonee.size(), registrate);

        return new EsitoLavoro(chiuse, idonee.size(), registrate, aperteReali,
                idonee.isEmpty() ? null : idonee.get(0).coppia());
    }

    private static NuovaOperazione daCandidato(Candidato c) {
        return new NuovaOperazione(c.coppia(), Trigger.BREAKOUT_20_GIORNI,
                c.piano().entrata(), c.piano().stop(), c.piano().obiettivo(), c.piano().quantita(),
                c.piano().rapporto(),
                "Scansione automatica: tutti i controlli superati. RSI "
                        + String.format(java.util.Locale.ITALY, "%.1f", c.indicatori().rsi14())
                        + ", distanza dalla media "
                        + String.format(java.util.Locale.ITALY, "%.1f", c.indicatori().distanzaDallaMedia())
                        + "%, controvalore 24h "
                        + String.format(java.util.Locale.ITALY, "%.0f", c.quotazione().controvalore24h()),
                false);
    }

    /**
     * @param sceltaPerRotazione la coppia che la rotazione indica per prima, se
     *        c'e' spazio fra le posizioni reali. Null se oggi non c'e' niente.
     */
    public record EsitoLavoro(int operazioniChiuse, int coppieIdonee, long registrateSuCarta,
                              long posizioniRealiAperte, String sceltaPerRotazione) {
    }
}
