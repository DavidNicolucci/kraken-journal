package dev.kraken.journal.journal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.kraken.journal.ai.AnthropicClient;
import dev.kraken.journal.journal.dto.AiView;
import dev.kraken.journal.journal.dto.JournalReport;
import dev.kraken.journal.journal.dto.JournalResponse;
import dev.kraken.journal.journal.dto.JournalStats;
import dev.kraken.journal.journal.dto.PairStats;
import dev.kraken.journal.kraken.KrakenClient;
import dev.kraken.journal.kraken.dto.KrakenTrade;

@Service
public class TradingJournalService {

    private static final Logger log = LoggerFactory.getLogger(TradingJournalService.class);

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN).withZone(ZoneOffset.UTC);

    private static final String SYSTEM_PROMPT = """
            Aiuti un trader retail a rileggere il proprio storico operazioni.

            Ricevi metriche gia' calcolate da codice deterministico. Le interpreti,
            non le ricalcoli.

            COME SCRIVERE
            - Scrivi in italiano corrente, per una persona, non per uno sviluppatore.
            - Non citare MAI nomi di campi tecnici (niente pnlComplete, unmatchedSellVolume,
              realizedPnl e simili). Se un concetto ti serve, dillo a parole: "costo di
              acquisto sconosciuto", "posizione ancora aperta", "risultato non calcolabile".
            - Cita i numeri, non i nomi dei campi. Arrotonda a cifre leggibili.
            - Frasi brevi. Niente parentesi che spiegano il tuo stesso ragionamento.

            COSA SIGNIFICANO I DATI
            - statoDelRisultato "non calcolabile": quell'asset e' stato venduto senza che
              nei dati ci sia l'acquisto corrispondente, quindi non si sa a che prezzo era
              stato comprato. Non e' una perdita ne' un pareggio: e' un'informazione assente.
            - statoDelRisultato "posizione aperta": comprato e non ancora venduto. Un
              risultato di zero qui significa solo che non e' stato ancora realizzato.
            - Le conversioni valutarie sono un blocco a parte: non sono trade.
            - commissioniInPercentuale e' il costo effettivo su ogni coppia. Su Kraken
              l'aliquota dipende dal tipo di ordine: piu' bassa con ordine limite, circa il
              doppio con ordine a mercato. Se una coppia costa il doppio di un'altra, e'
              un'informazione che vale la pena far notare.

            REGOLE VINCOLANTI
            - Usa solo i numeri forniti. Non stimarne altri.
            - Nessuna previsione di prezzo. Nessun consiglio di comprare, vendere o tenere.
            - Non presentare come comportamento di mercato cio' che dipende da dati mancanti.
            - Se il campione e' troppo piccolo per concludere qualcosa, dillo e basta.
            - Le osservazioni piu' utili riguardano cio' su cui la persona ha controllo:
              i costi, la frequenza, la dimensione delle operazioni.

            Rispondi ESCLUSIVAMENTE con un oggetto JSON valido, senza testo prima o dopo,
            senza backtick:
            {
              "summary": "2-4 frasi sul periodo",
              "observedPatterns": ["osservazione con il numero che la sostiene"],
              "questionsToAskYourself": ["domanda che il trader dovrebbe porsi"],
              "dataCaveat": "limiti dei dati di questo periodo"
            }
            """;

    private final KrakenClient krakenClient;
    private final TradeStatisticsCalculator calculator;
    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    public TradingJournalService(KrakenClient krakenClient,
                                 TradeStatisticsCalculator calculator,
                                 AnthropicClient anthropicClient,
                                 ObjectMapper objectMapper) {
        this.krakenClient = krakenClient;
        this.calculator = calculator;
        this.anthropicClient = anthropicClient;
        this.objectMapper = objectMapper;
    }

    public JournalResponse buildJournal(int lookbackDays) {
        Instant to = Instant.now();
        Instant from = to.minus(lookbackDays, ChronoUnit.DAYS);

        List<KrakenTrade> trades = krakenClient.fetchTrades(from, to);
        JournalStats stats = calculator.compute(trades, from, to);

        if (trades.isEmpty()) {
            return new JournalResponse(stats, new JournalReport(
                    "Nessuna operazione negli ultimi %d giorni.".formatted(lookbackDays),
                    List.of(), List.of(),
                    "Nessun dato da analizzare nel periodo."));
        }
        return new JournalResponse(stats, analyse(stats));
    }

    /** Traduce le metriche in una vista comprensibile prima di darle al modello. */
    private AiView toAiView(JournalStats s) {
        List<AiView.Coppia> coppie = s.byPair().stream()
                .filter(p -> !p.fxConversion())
                .map(TradingJournalService::toCoppia)
                .toList();

        String notaRisultato = s.pairsWithIncompletePnl() > 0
                ? "Il risultato complessivo copre solo le coppie con costo di acquisto noto: %d su %d."
                    .formatted(coppie.size() - s.pairsWithIncompletePnl(), coppie.size())
                : "Tutte le coppie hanno un costo di acquisto ricostruibile.";

        String notaValute = s.mixedQuoteCurrencies()
                ? "I totali sommano importi in valute diverse (%s): vanno letti come ordine di grandezza."
                    .formatted(String.join(" e ", s.quoteCurrencies()))
                : "Tutti gli importi sono nella stessa valuta.";

        java.math.BigDecimal fxPercent = s.byPair().stream()
                .filter(PairStats::fxConversion)
                .map(PairStats::feesToVolumePercent)
                .filter(v -> v != null)
                .findFirst().orElse(null);

        return new AiView(
                DAY.format(s.periodStart()) + " - " + DAY.format(s.periodEnd()),
                s.tradingDays(),
                s.tradingTrades(),
                s.tradesPerActiveDay(),
                s.tradingVolumeQuote(),
                s.tradingFees(),
                s.feesToVolumePercent(),
                s.realizedPnlComplete(),
                notaRisultato,
                new AiView.Cambi(s.fxConversions(), s.fxFees(), fxPercent),
                notaValute,
                coppie);
    }

    private static AiView.Coppia toCoppia(PairStats p) {
        String stato;
        if (!p.pnlComplete()) {
            stato = "non calcolabile: venduto senza acquisto corrispondente nei dati";
        } else if (p.sellCount() == 0) {
            stato = "posizione aperta: comprato e non ancora venduto";
        } else {
            stato = "calcolato";
        }
        return new AiView.Coppia(
                p.displayName(), p.tradeCount(), p.buyCount(), p.sellCount(),
                p.volumeQuote(), p.quoteCurrency(),
                p.feesPaid(), p.feesToVolumePercent(),
                p.pnlComplete() && p.sellCount() > 0 ? p.realizedPnl() : null,
                stato,
                p.openPosition());
    }

    private JournalReport analyse(JournalStats stats) {
        try {
            String payload = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(toAiView(stats));

            String userPrompt = """
                    Dati del periodo:

                    %s

                    Analizza il comportamento operativo del periodo.
                    """.formatted(payload);

            return parse(anthropicClient.complete(SYSTEM_PROMPT, userPrompt));

        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossibile serializzare le metriche", e);
        }
    }

    /**
     * Il modello puo' incorniciare il JSON in un blocco markdown nonostante le
     * istruzioni. Se il parsing fallisce restituisco un report degradato invece
     * di far saltare la richiesta: le metriche restano valide.
     */
    private JournalReport parse(String raw) {
        String cleaned = raw == null ? "" : raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        try {
            return objectMapper.readValue(cleaned, JournalReport.class);
        } catch (JsonProcessingException e) {
            log.warn("Il modello non ha restituito JSON valido, report degradato", e);
            return new JournalReport(
                    "Analisi non disponibile: risposta del modello non interpretabile.",
                    List.of(), List.of(),
                    "Le metriche restano valide e verificabili.");
        }
    }
}
