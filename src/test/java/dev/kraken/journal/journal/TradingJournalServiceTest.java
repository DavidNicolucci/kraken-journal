package dev.kraken.journal.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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

class TradingJournalServiceTest {

    @Test
    void seIlModelloNonRispondeLeMetricheArrivanoLoStesso() {
        KrakenClient kraken = mock(KrakenClient.class);
        when(kraken.fetchTrades(any(), any())).thenReturn(List.of(new KrakenTrade("O1", "XXBTZUSD",
                new BigDecimal("1758153600"), "buy", "limit", new BigDecimal("100"),
                new BigDecimal("100"), new BigDecimal("0.4"), BigDecimal.ONE, BigDecimal.ZERO)));
        AnthropicClient anthropic = mock(AnthropicClient.class);
        when(anthropic.complete(anyString(), anyString()))
                .thenThrow(HttpServerErrorException.create(HttpStatusCode.valueOf(529), "Overloaded",
                        null, null, null));

        var servizio = new TradingJournalService(kraken,
                new TradeStatisticsCalculator(new PairResolver()), anthropic, new ObjectMapper());

        var risposta = servizio.buildJournal(30);

        assertEquals(1, risposta.stats().tradingTrades(), "le metriche non dipendono dal modello");
        assertTrue(risposta.report().summary().startsWith("Analisi non disponibile"));
    }
}
