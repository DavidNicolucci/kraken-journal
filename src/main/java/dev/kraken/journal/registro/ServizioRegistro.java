package dev.kraken.journal.registro;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.kraken.journal.mercato.Intervallo;
import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.registro.dto.NuovaOperazione;
import dev.kraken.journal.registro.dto.StatisticheRegistro;
import dev.kraken.journal.scansione.ProprietaScansione;

/**
 * Il registro delle operazioni: le scrive prima, le chiude dopo, e dice come
 * stanno andando.
 *
 * E' la parte che giustifica tutto il resto del programma. La scansione trova
 * candidati, ma senza un registro che li segua nel tempo non si accumula
 * nessuna misura, e senza misura ogni regola resta un'opinione. Le quindici
 * registrazioni in avanti che servono per giudicare il trigger provvisorio si
 * raccolgono qui, non a memoria.
 */
@Service
public class ServizioRegistro {

    private static final Logger log = LoggerFactory.getLogger(ServizioRegistro.class);

    private final RegistroRepository repository;
    private final MercatoClient mercatoClient;
    private final ProprietaScansione proprieta;

    public ServizioRegistro(RegistroRepository repository, MercatoClient mercatoClient,
                            ProprietaScansione proprieta) {
        this.repository = repository;
        this.mercatoClient = mercatoClient;
        this.proprieta = proprieta;
    }

    /**
     * Le annotazioni sul DTO controllano che i campi ci siano; qui si controlla
     * che abbiano senso insieme. Uno stop sopra l'entrata non e' un campo
     * mancante, e' un'operazione che non esiste.
     */
    @Transactional
    public OperazioneRegistrata registra(NuovaOperazione nuova) {
        if (nuova.stop() >= nuova.entrata()) {
            throw new IllegalArgumentException("Lo stop (" + nuova.stop()
                    + ") deve stare sotto l'entrata (" + nuova.entrata() + ")");
        }
        if (nuova.obiettivo() <= nuova.entrata()) {
            throw new IllegalArgumentException("L'obiettivo (" + nuova.obiettivo()
                    + ") deve stare sopra l'entrata (" + nuova.entrata() + ")");
        }
        log.info("Registrata {} su {} ({})", nuova.reale() ? "operazione reale" : "analisi su carta",
                nuova.coppia(), nuova.trigger());

        return repository.save(new OperazioneRegistrata(nuova.coppia(), nuova.trigger(), nuova.entrata(),
                nuova.stop(), nuova.obiettivo(), nuova.quantita(), nuova.rapporto(), nuova.motivo(),
                nuova.reale(), Instant.now()));
    }

    /**
     * Chiude le operazioni che nel frattempo hanno toccato stop, obiettivo o il
     * limite di tempo. Idempotente: quelle gia' chiuse non vengono riesaminate.
     */
    @Transactional
    public int risolviAperte() {
        int chiuse = 0;
        for (OperazioneRegistrata operazione : repository.findByStato(StatoOperazione.APERTA)) {
            try {
                var chiusura = RisolutoreOperazione.risolvi(operazione,
                        mercatoClient.candeleChiuse(operazione.coppia(), Intervallo.GIORNALIERO),
                        proprieta.giorniTimeStop(), proprieta.tipoOrdine());
                if (chiusura != null) {
                    operazione.chiudi(chiusura.stato(), chiusura.quando(), chiusura.prezzoUscita(),
                            chiusura.risultatoInR());
                    chiuse++;
                    log.info("Operazione {} su {} chiusa come {} a {} ({} R)", operazione.id(),
                            operazione.coppia(), chiusura.stato(), chiusura.prezzoUscita(),
                            chiusura.risultatoInR());
                }
            } catch (RuntimeException e) {
                log.warn("Operazione {} non risolvibile ora: {}", operazione.id(), e.getMessage());
            }
        }
        return chiuse;
    }

    /**
     * Registra un'analisi su carta, senza far fallire il lavoro giornaliero se
     * i numeri non tornano. Restituisce null quando l'operazione e' stata
     * scartata: chiamarla in un ciclo non deve interrompere il ciclo.
     */
    @Transactional
    public OperazioneRegistrata registraSuCarta(NuovaOperazione nuova) {
        try {
            return registra(nuova);
        } catch (IllegalArgumentException e) {
            log.warn("Analisi su {} scartata: {}", nuova.coppia(), e.getMessage());
            return null;
        }
    }

    /** Una coppia con un'operazione gia' aperta non se ne apre una seconda. */
    @Transactional(readOnly = true)
    public boolean giaApertaSu(String coppia) {
        return repository.existsByCoppiaAndStato(coppia, StatoOperazione.APERTA);
    }

    /** Quante volte quella coppia e' gia' passata dal registro: serve alla rotazione. */
    @Transactional(readOnly = true)
    public long quanteVolteRegistrata(String coppia) {
        return repository.countByCoppia(coppia);
    }

    /** Solo le posizioni vere: e' su queste che vale il tetto della regola 2.3. */
    @Transactional(readOnly = true)
    public long posizioniRealiAperte() {
        return repository.countByStatoAndReale(StatoOperazione.APERTA, true);
    }

    @Transactional(readOnly = true)
    public List<OperazioneRegistrata> tutte() {
        return repository.findAllByOrderByDataRegistrazioneDesc();
    }

    @Transactional(readOnly = true)
    public StatisticheRegistro statistiche() {
        List<OperazioneRegistrata> tutte = repository.findAll();
        List<OperazioneRegistrata> chiuse = tutte.stream().filter(o -> o.stato().chiusa()).toList();
        double totale = chiuse.stream().mapToDouble(OperazioneRegistrata::risultatoInR).sum();

        return new StatisticheRegistro(
                tutte.size() - chiuse.size(),
                chiuse.size(),
                conta(chiuse, StatoOperazione.CHIUSA_ALL_OBIETTIVO),
                conta(chiuse, StatoOperazione.CHIUSA_ALLO_STOP),
                conta(chiuse, StatoOperazione.CHIUSA_PER_TEMPO),
                chiuse.isEmpty() ? 0 : conta(chiuse, StatoOperazione.CHIUSA_ALL_OBIETTIVO) * 100.0 / chiuse.size(),
                chiuse.isEmpty() ? 0 : totale / chiuse.size(),
                totale,
                Math.max(0, proprieta.registrazioniPerGiudizio() - chiuse.size()),
                posizioniRealiAperte(),
                proprieta.posizioniMassime());
    }

    private static long conta(List<OperazioneRegistrata> operazioni, StatoOperazione stato) {
        return operazioni.stream().filter(o -> o.stato() == stato).count();
    }
}
