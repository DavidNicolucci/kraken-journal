package dev.kraken.journal.registro;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.mercato.Intervallo;
import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.registro.dto.NuovaOperazione;
import dev.kraken.journal.registro.dto.StatisticheRegistro;
import dev.kraken.journal.scansione.MatematicaOperazione;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.scansione.TipoOrdine;

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
    /** Kraken tiene 720 candele per intervallo: per le orarie sono trenta giorni. */
    private static final Duration ORARIE_DISPONIBILI = Duration.ofDays(29);

    private final RegistroRepository repository;
    private final MercatoClient mercatoClient;
    private final ProprietaScansione proprieta;
    private final PairResolver pairResolver;

    public ServizioRegistro(RegistroRepository repository, MercatoClient mercatoClient,
                            ProprietaScansione proprieta, PairResolver pairResolver) {
        this.repository = repository;
        this.mercatoClient = mercatoClient;
        this.proprieta = proprieta;
        this.pairResolver = pairResolver;
    }

    /**
     * Le annotazioni sul DTO controllano che i campi ci siano; qui si controlla
     * che abbiano senso insieme. Uno stop sopra l'entrata non e' un campo
     * mancante, e' un'operazione che non esiste.
     *
     * Un'operazione reale oltre il tetto di posizioni viene rifiutata: il
     * registro si scrive prima di aprire, e se dice di no l'operazione non si
     * apre. La carta invece non ha tetto (regola 2.3).
     */
    @Transactional
    public OperazioneRegistrata registra(NuovaOperazione nuova) {
        String coppia = pairResolver.altname(nuova.coppia());
        if (coppia == null) {
            throw new IllegalArgumentException("Coppia non riconosciuta: " + nuova.coppia());
        }
        if (nuova.stop() >= nuova.entrata()) {
            throw new IllegalArgumentException("Lo stop (" + nuova.stop()
                    + ") deve stare sotto l'entrata (" + nuova.entrata() + ")");
        }
        if (nuova.obiettivo() <= nuova.entrata()) {
            throw new IllegalArgumentException("L'obiettivo (" + nuova.obiettivo()
                    + ") deve stare sopra l'entrata (" + nuova.entrata() + ")");
        }
        if (nuova.reale() && nuova.quantita() <= 0) {
            throw new IllegalArgumentException("Un'operazione reale ha una quantita': serve a misurarne il rischio");
        }
        if (nuova.reale() && posizioniRealiAperte() >= proprieta.posizioniMassime()) {
            throw new IllegalArgumentException("Hai gia' " + proprieta.posizioniMassime()
                    + " posizioni reali aperte, il massimo della regola 2.3: questa resta su carta");
        }
        TipoOrdine tipoOrdine = proprieta.tipoOrdine();
        double rapporto = MatematicaOperazione.risultatoInR(nuova.entrata(), nuova.stop(), nuova.obiettivo(),
                tipoOrdine, tipoOrdine);
        log.info("Registrata {} su {} ({})", nuova.reale() ? "operazione reale" : "analisi su carta",
                coppia, nuova.trigger());

        return repository.save(new OperazioneRegistrata(coppia, nuova.trigger(), nuova.entrata(),
                nuova.stop(), nuova.obiettivo(), nuova.quantita(), rapporto, nuova.motivo(),
                nuova.reale(), Instant.now(), tipoOrdine));
    }

    /**
     * Chiude le analisi su carta che nel frattempo hanno toccato stop,
     * obiettivo o il limite di tempo. Idempotente: quelle gia' chiuse non
     * vengono riesaminate. Le operazioni reali no: si chiudono dagli ordini
     * veri, non da una simulazione sulle candele.
     */
    @Transactional
    public int risolviAperte() {
        int chiuse = 0;
        for (OperazioneRegistrata operazione : repository.findByStatoAndReale(StatoOperazione.APERTA, false)) {
            try {
                var chiusura = RisolutoreOperazione.risolvi(operazione,
                        mercatoClient.candeleChiuse(operazione.coppia(), Intervallo.GIORNALIERO), orarie(operazione),
                        proprieta.giorniTimeStop(), tipoOrdine(operazione));
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
     * Le candele orarie per il giorno di registrazione. Kraken ne restituisce
     * trenta giorni: oltre non servono a niente, e se la chiamata non riesce
     * il giorno si legge sulla candela giornaliera, con la lettura prudente.
     */
    private List<Candela> orarie(OperazioneRegistrata operazione) {
        if (operazione.dataRegistrazione().isBefore(Instant.now().minus(ORARIE_DISPONIBILI))) {
            return List.of();
        }
        try {
            return mercatoClient.candeleChiuse(operazione.coppia(), Intervallo.ORA);
        } catch (RuntimeException e) {
            log.info("Candele orarie di {} non disponibili, uso la giornaliera: {}", operazione.coppia(), e.getMessage());
            return List.of();
        }
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

    /**
     * Scrive l'esito di un'operazione reale letto dagli ordini su Kraken.
     * Restituisce false se nel frattempo era gia' stata chiusa.
     */
    @Transactional
    public boolean chiudi(Long id, RisolutoreOperazione.Chiusura chiusura) {
        OperazioneRegistrata operazione = repository.findById(id).orElseThrow();
        if (operazione.stato() != StatoOperazione.APERTA) {
            return false;
        }
        operazione.chiudi(chiusura.stato(), chiusura.quando(), chiusura.prezzoUscita(), chiusura.risultatoInR());
        log.info("Operazione reale {} su {} chiusa dagli ordini come {} a {} ({} R)", id, operazione.coppia(),
                chiusura.stato(), chiusura.prezzoUscita(), chiusura.risultatoInR());
        return true;
    }

    /** Un'operazione reale senza nessun acquisto nei giorni dopo la registrazione: non e' mai stata aperta. */
    @Transactional
    public void nonEseguita(Long id, Instant quando) {
        OperazioneRegistrata operazione = repository.findById(id).orElseThrow();
        operazione.nonEseguita(quando);
        log.info("Operazione reale {} su {} mai eseguita: libera il suo posto", id, operazione.coppia());
    }

    /**
     * La riserva della chiusura dagli ordini: quando Kraken non basta (venduta
     * in un'altra valuta, ledger non leggibile) si scrive il prezzo medio di
     * uscita. Lo stato viene dal prezzo; la commissione d'uscita e' quella
     * a mercato, tranne all'obiettivo, che e' un ordine in attesa.
     */
    @Transactional
    public OperazioneRegistrata chiudiAMano(long id, double prezzoUscita) {
        OperazioneRegistrata operazione = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nessuna operazione con id " + id));
        if (!operazione.reale()) {
            throw new IllegalArgumentException("Le analisi su carta si chiudono da sole, sulle candele");
        }
        if (operazione.stato() != StatoOperazione.APERTA) {
            throw new IllegalArgumentException("L'operazione " + id + " e' gia' " + operazione.stato());
        }
        StatoOperazione stato = StatoOperazione.dallUscita(prezzoUscita, operazione.stop(), operazione.obiettivo());
        TipoOrdine tipo = tipoOrdine(operazione);
        operazione.chiudi(stato, Instant.now(), prezzoUscita, MatematicaOperazione.risultatoInR(operazione.entrata(),
                operazione.stop(), prezzoUscita, tipo,
                stato == StatoOperazione.CHIUSA_ALL_OBIETTIVO ? tipo : MatematicaOperazione.USCITA_FORZATA));
        return operazione;
    }

    /** La tariffa con cui l'operazione e' stata registrata; per le righe piu' vecchie, quella di oggi. */
    public TipoOrdine tipoOrdine(OperazioneRegistrata operazione) {
        return operazione.tipoOrdine() != null ? operazione.tipoOrdine() : proprieta.tipoOrdine();
    }

    /** Una coppia con un'operazione gia' aperta non se ne apre una seconda. */
    @Transactional(readOnly = true)
    public boolean giaApertaSu(String coppia) {
        return repository.existsByCoppiaAndStato(nome(coppia), StatoOperazione.APERTA);
    }

    /** Quante volte quella coppia e' gia' passata dal registro: serve alla rotazione. */
    @Transactional(readOnly = true)
    public long quanteVolteRegistrata(String coppia) {
        return repository.countByCoppia(nome(coppia));
    }

    /** Il nome con cui la coppia sta nel registro, anche se la watchlist la scrive in un altro modo. */
    private String nome(String coppia) {
        String nome = pairResolver.altname(coppia);
        return nome != null ? nome : coppia;
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

    /** Le operazioni vere, per confrontarle con gli acquisti letti da Kraken. */
    @Transactional(readOnly = true)
    public List<OperazioneRegistrata> reali() {
        return repository.findByReale(true);
    }

    /** Le operazioni vere ancora aperte: si chiudono dagli ordini su Kraken. */
    @Transactional(readOnly = true)
    public List<OperazioneRegistrata> realiAperte() {
        return repository.findByStatoAndReale(StatoOperazione.APERTA, true);
    }

    @Transactional(readOnly = true)
    public StatisticheRegistro statistiche() {
        List<OperazioneRegistrata> tutte = repository.findAll();
        List<OperazioneRegistrata> chiuse = tutte.stream().filter(o -> o.stato().chiusa()).toList();

        Map<ChiaveGruppo, List<OperazioneRegistrata>> perGruppo = tutte.stream().collect(Collectors.groupingBy(
                o -> new ChiaveGruppo(o.trigger(), o.reale()), TreeMap::new, Collectors.toList()));

        return new StatisticheRegistro(
                conta(tutte, StatoOperazione.APERTA),
                chiuse.size(),
                conta(chiuse, StatoOperazione.CHIUSA_ALL_OBIETTIVO),
                conta(chiuse, StatoOperazione.CHIUSA_ALLO_STOP),
                conta(chiuse, StatoOperazione.CHIUSA_PER_TEMPO),
                posizioniRealiAperte(),
                proprieta.posizioniMassime(),
                perGruppo.entrySet().stream().map(v -> gruppo(v.getKey(), v.getValue())).toList());
    }

    /** Prima le operazioni vere, poi la carta; dentro, nell'ordine dei trigger. */
    private record ChiaveGruppo(Trigger trigger, boolean reale) implements Comparable<ChiaveGruppo> {
        @Override
        public int compareTo(ChiaveGruppo altro) {
            return reale != altro.reale ? (reale ? -1 : 1) : trigger.compareTo(altro.trigger);
        }
    }

    private StatisticheRegistro.Gruppo gruppo(ChiaveGruppo chiave, List<OperazioneRegistrata> operazioni) {
        double[] risultati = operazioni.stream().filter(o -> o.stato().chiusa() && o.risultatoInR() != null)
                .mapToDouble(OperazioneRegistrata::risultatoInR).toArray();
        long chiuse = operazioni.stream().filter(o -> o.stato().chiusa()).count();
        return new StatisticheRegistro.Gruppo(
                chiave.trigger(),
                chiave.reale(),
                conta(operazioni, StatoOperazione.APERTA),
                chiuse,
                chiuse == 0 ? 0 : conta(operazioni, StatoOperazione.CHIUSA_ALL_OBIETTIVO) * 100.0 / chiuse,
                risultati.length == 0 ? null : Statistica.media(risultati),
                Statistica.margine95(risultati),
                Math.max(0, proprieta.registrazioniPerGiudizio() - chiuse));
    }

    private static long conta(List<OperazioneRegistrata> operazioni, StatoOperazione stato) {
        return operazioni.stream().filter(o -> o.stato() == stato).count();
    }
}
