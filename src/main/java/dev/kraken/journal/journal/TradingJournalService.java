package dev.kraken.journal.journal;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.kraken.journal.journal.dto.JournalResponse;
import dev.kraken.journal.journal.dto.JournalStats;
import dev.kraken.journal.kraken.KrakenApiException;
import dev.kraken.journal.kraken.KrakenClient;
import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.kraken.Storico;
import dev.kraken.journal.kraken.dto.KrakenTrade;
import dev.kraken.journal.kraken.dto.KrakenLedgerEntry;
import dev.kraken.journal.mercato.Intervallo;
import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.registro.OperazioneRegistrata;
import dev.kraken.journal.registro.ServizioRegistro;

/**
 * Storico da Kraken, metriche, commento.
 *
 * Il commento era scritto da Claude a ogni caricamento della pagina: costava,
 * mandava fuori i dati aggregati, e non era ripetibile ne' testabile. Dal 23/11
 * lo scrive {@link GeneratoreCommento} con regole fisse. La versione con il
 * modello resta nella storia di git.
 */
@Service
public class TradingJournalService {

    private static final Logger log = LoggerFactory.getLogger(TradingJournalService.class);

    private final KrakenClient krakenClient;
    private final StoriaOperazioni storiaOperazioni;
    private final TradeStatisticsCalculator calculator;
    private final GeneratoreCommento generatore;
    private final ServizioRegistro registro;
    private final MercatoClient mercato;
    private final ProprietaCommento commento;
    private final PairResolver pairResolver;

    public TradingJournalService(KrakenClient krakenClient,
                                 StoriaOperazioni storiaOperazioni,
                                 TradeStatisticsCalculator calculator,
                                 GeneratoreCommento generatore,
                                 ServizioRegistro registro,
                                 MercatoClient mercato,
                                 ProprietaCommento commento,
                                 PairResolver pairResolver) {
        this.krakenClient = krakenClient;
        this.storiaOperazioni = storiaOperazioni;
        this.calculator = calculator;
        this.generatore = generatore;
        this.registro = registro;
        this.mercato = mercato;
        this.commento = commento;
        this.pairResolver = pairResolver;
    }

    /**
     * Lo storico si scarica sempre per intero, dall'apertura del conto: il
     * periodo scelto decide cosa riportare, non da dove ricostruire il costo
     * di carico. Ogni pagina sono 50 voci e costa due punti del limite di
     * Kraken: con poche decine di operazioni basta una pagina per fonte, con
     * molte ricompense di staking il ledger ne chiede di piu'.
     */
    public JournalResponse buildJournal(int lookbackDays) {
        Instant to = Instant.now();
        JournalStats stats = statistiche(to.minus(lookbackDays, ChronoUnit.DAYS), to);
        return new JournalResponse(stats, generatore.genera(stats, lookbackDays, registro.reali()));
    }

    /**
     * Chiude le operazioni reali del registro dagli ordini veri: e' la parte
     * del lavoro giornaliero che legge Kraken. Senza ledger non decide niente,
     * perche' un acquisto dall'app non si vedrebbe e l'operazione risulterebbe
     * mai eseguita; se Kraken non risponde, riprova al giro dopo.
     *
     * @return quante operazioni ha chiuso con un esito.
     */
    public int chiudiOperazioniReali() {
        List<OperazioneRegistrata> aperte = registro.realiAperte();
        if (aperte.isEmpty()) {
            return 0;
        }
        Instant adesso = Instant.now();
        JournalStats stats;
        try {
            stats = statistiche(Instant.EPOCH, adesso);
        } catch (RuntimeException e) {
            log.warn("Operazioni reali non risolvibili ora: {}", e.getMessage());
            return 0;
        }
        if (!stats.ledgerLetto()) {
            log.warn("Operazioni reali non risolvibili senza il ledger: si chiudono a mano o col permesso Query Ledger Entries");
            return 0;
        }
        Duration finestra = Duration.ofDays(commento.giorniRegistrazione());
        int chiuse = 0;
        for (OperazioneRegistrata operazione : aperte) {
            Optional<ChiusuraReale.Esito> esito = ChiusuraReale.di(operazione, registro.tipoOrdine(operazione),
                    stats, finestra, adesso, pairResolver);
            if (esito.isPresent() && esito.get().nonEseguita()) {
                registro.nonEseguita(operazione.id(), adesso);
            } else if (esito.isPresent() && registro.chiudi(operazione.id(), esito.get().chiusura())) {
                chiuse++;
            }
        }
        return chiuse;
    }

    /** Lo storico intero, per il costo di carico; il periodo decide solo cosa riportare. */
    private JournalStats statistiche(Instant from, Instant to) {
        Storico<KrakenTrade> trade = krakenClient.fetchTrades(Instant.EPOCH, to);
        Storico<KrakenLedgerEntry> ledger = ledger(to);
        Storia storia = storiaOperazioni.componi(trade.voci(), ledger == null ? null : ledger.voci());
        if (!trade.completo() || ledger != null && !ledger.completo()) {
            storia = storia.troncata();
        }
        return calculator.compute(storia, cambi(storia), from, to);
    }

    /**
     * Il ledger serve a leggere le operazioni fatte dall'app, ma richiede alla
     * chiave un permesso in piu'. Senza quel permesso il journal funziona lo
     * stesso con i soli trade, e lo dichiara in {@code ledgerLetto}: un errore
     * qui non deve far sparire le metriche che si possono calcolare.
     */
    private Storico<KrakenLedgerEntry> ledger(Instant to) {
        try {
            return krakenClient.fetchLedger(Instant.EPOCH, to);
        } catch (KrakenApiException e) {
            log.warn("Ledger non leggibile, il journal usa i soli trade di Kraken Pro: {}", e.getMessage());
            return null;
        }
    }

    /**
     * I cambi fra le valute delle operazioni, solo se ce n'e' piu' d'una.
     * Kraken quota ogni cambio in un verso solo (EURUSD, non USDEUR): si
     * prova un verso e poi l'altro. Le candele giornaliere arrivano al massimo
     * a 720 giorni fa: una vendita piu' vecchia resta senza cambio, come una
     * chiamata che non risponde. Il journal non si ferma per questo.
     */
    private Cambi cambi(Storia storia) {
        List<String> valute = storia.movimenti().stream().filter(m -> !m.coppia().fxConversion())
                .map(m -> m.coppia().quote()).distinct().sorted().toList();
        Map<String, NavigableMap<LocalDate, BigDecimal>> perCoppia = new HashMap<>();
        for (String da : valute) {
            for (String a : valute.subList(valute.indexOf(da) + 1, valute.size())) {
                if (!scaricaCambio(da + a, perCoppia) && !scaricaCambio(a + da, perCoppia)) {
                    log.warn("Nessun cambio {}/{}: le vendite fra queste valute restano senza risultato", da, a);
                }
            }
        }
        return new Cambi(perCoppia);
    }

    /** Il valore di ogni giorno e' il prezzo medio della candela, o la chiusura se quel giorno non ci sono scambi. */
    private boolean scaricaCambio(String coppia, Map<String, NavigableMap<LocalDate, BigDecimal>> perCoppia) {
        try {
            perCoppia.put(coppia, mercato.candeleChiuse(coppia, Intervallo.GIORNALIERO).stream().collect(Collectors.toMap(
                    c -> LocalDate.ofInstant(c.istante(), ZoneOffset.UTC),
                    c -> BigDecimal.valueOf(c.prezzoMedio() > 0 ? c.prezzoMedio() : c.chiusura()),
                    (prima, dopo) -> dopo, TreeMap::new)));
            return true;
        } catch (RuntimeException e) {
            log.debug("Cambio {} non disponibile: {}", coppia, e.getMessage());
            return false;
        }
    }
}
