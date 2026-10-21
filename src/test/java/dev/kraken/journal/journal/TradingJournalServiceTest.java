package dev.kraken.journal.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpServerErrorException;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.kraken.journal.ai.AnthropicClient;
import dev.kraken.journal.kraken.KrakenClient;
import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.kraken.dto.KrakenTrade;

/**
 * Il modello e' sempre finto. Non perche' sia comodo: una chiamata vera
 * renderebbe questi test lenti, dipendenti dalla rete e non deterministici, e
 * soprattutto verdi sia quando il modello risponde sia quando fallisce. Qui
 * ogni ramo e' scelto esplicitamente dal test.
 */
class TradingJournalServiceTest {

    /** Quello che il modello restituisce quando rispetta le istruzioni. */
    private static final String RISPOSTA_MODELLO = """
            {"summary":"Una sola operazione nel periodo.",
             "observedPatterns":["un solo trade, 0,4 di commissione"],
             "questionsToAskYourself":["perche' ordine a limite?"],
             "dataCaveat":"campione troppo piccolo"}
            """;

    @Test
    void seIlModelloNonRispondeLeMetricheArrivanoLoStesso() {
        AnthropicClient anthropic = mock(AnthropicClient.class);
        when(anthropic.complete(anyString(), anyString()))
                .thenThrow(HttpServerErrorException.create(HttpStatusCode.valueOf(529), "Overloaded",
                        null, null, null));

        var risposta = servizioCon(anthropic).buildJournal(30);

        assertEquals(1, risposta.stats().tradingTrades(), "le metriche non dipendono dal modello");
        assertTrue(risposta.report().summary().startsWith("Analisi non disponibile"));
    }

    @Test
    void seIlModelloRispondeIlReportArrivaCompleto() {
        AnthropicClient anthropic = mock(AnthropicClient.class);
        when(anthropic.complete(anyString(), anyString())).thenReturn(RISPOSTA_MODELLO);

        var report = servizioCon(anthropic).buildJournal(30).report();

        assertEquals("Una sola operazione nel periodo.", report.summary());
        assertEquals(1, report.observedPatterns().size());
        assertEquals(1, report.questionsToAskYourself().size());
        assertEquals("campione troppo piccolo", report.dataCaveat());
    }

    /** Il modello incornicia il JSON in un blocco markdown nonostante le istruzioni. */
    @Test
    void ilJsonDentroUnBloccoMarkdownVieneLettoLoStesso() {
        AnthropicClient anthropic = mock(AnthropicClient.class);
        when(anthropic.complete(anyString(), anyString()))
                .thenReturn("```json\n" + RISPOSTA_MODELLO + "\n```");

        var report = servizioCon(anthropic).buildJournal(30).report();

        assertEquals("Una sola operazione nel periodo.", report.summary(),
                "i backtick vanno tolti prima del parsing, non fatti esplodere");
    }

    @Test
    void seIlModelloNonRestituisceJsonIlReportSiDegrada() {
        AnthropicClient anthropic = mock(AnthropicClient.class);
        when(anthropic.complete(anyString(), anyString())).thenReturn("Certo! Ecco la tua analisi.");

        var risposta = servizioCon(anthropic).buildJournal(30);

        assertEquals(1, risposta.stats().tradingTrades(), "le metriche restano valide");
        assertTrue(risposta.report().summary().startsWith("Analisi non disponibile"));
    }

    /** Senza operazioni non c'e' niente da interpretare: i token non vanno spesi. */
    @Test
    void senzaOperazioniNelPeriodoIlModelloNonVieneChiamato() {
        KrakenClient kraken = mock(KrakenClient.class);
        when(kraken.fetchTrades(any(), any())).thenReturn(List.of());
        AnthropicClient anthropic = mock(AnthropicClient.class);

        var risposta = servizio(kraken, anthropic).buildJournal(30);

        assertTrue(risposta.report().summary().startsWith("Nessuna operazione"));
        verifyNoInteractions(anthropic);
    }

    private static TradingJournalService servizioCon(AnthropicClient anthropic) {
        KrakenClient kraken = mock(KrakenClient.class);
        when(kraken.fetchTrades(any(), any())).thenReturn(List.of(unAcquisto()));
        return servizio(kraken, anthropic);
    }

    private static TradingJournalService servizio(KrakenClient kraken, AnthropicClient anthropic) {
        return new TradingJournalService(kraken,
                new TradeStatisticsCalculator(new PairResolver()), anthropic, new ObjectMapper());
    }

    private static KrakenTrade unAcquisto() {
        return new KrakenTrade("O1", "XXBTZUSD", new BigDecimal("1758153600"), "buy", "limit",
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("0.4"),
                BigDecimal.ONE, BigDecimal.ZERO);
    }
}
