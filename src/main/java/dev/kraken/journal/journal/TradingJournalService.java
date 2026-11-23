package dev.kraken.journal.journal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.kraken.journal.journal.dto.JournalResponse;
import dev.kraken.journal.journal.dto.JournalStats;
import dev.kraken.journal.kraken.KrakenApiException;
import dev.kraken.journal.kraken.KrakenClient;
import dev.kraken.journal.kraken.dto.KrakenLedgerEntry;
import dev.kraken.journal.mercato.Intervallo;
import dev.kraken.journal.mercato.MercatoClient;
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

    public TradingJournalService(KrakenClient krakenClient,
                                 StoriaOperazioni storiaOperazioni,
                                 TradeStatisticsCalculator calculator,
                                 GeneratoreCommento generatore,
                                 ServizioRegistro registro,
                                 MercatoClient mercato) {
        this.krakenClient = krakenClient;
        this.storiaOperazioni = storiaOperazioni;
        this.calculator = calculator;
        this.generatore = generatore;
        this.registro = registro;
        this.mercato = mercato;
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
        Instant from = to.minus(lookbackDays, ChronoUnit.DAYS);

        Storia storia = storiaOperazioni.componi(krakenClient.fetchTrades(Instant.EPOCH, to), ledger(to));
        JournalStats stats = calculator.compute(storia, cambi(storia), from, to);
        return new JournalResponse(stats, generatore.genera(stats, lookbackDays, registro.reali()));
    }

    /**
     * Il ledger serve a leggere le operazioni fatte dall'app, ma richiede alla
     * chiave un permesso in piu'. Senza quel permesso il journal funziona lo
     * stesso con i soli trade, e lo dichiara in {@code ledgerLetto}: un errore
     * qui non deve far sparire le metriche che si possono calcolare.
     */
    private List<KrakenLedgerEntry> ledger(Instant to) {
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
