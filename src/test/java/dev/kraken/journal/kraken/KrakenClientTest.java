package dev.kraken.journal.kraken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.kraken.journal.config.KrakenProperties;

class KrakenClientTest {

    private static final String SEGRETO =
            "kQH5HW/8p1uGOVjbgWA7FunAmGO8lsSUXNsu3eow76sz84Q18fWxnyRzBHCd3pd5nE9qa99HAZtuZuj6F1huXg==";

    private static final String LIMITE_SUPERATO = """
            {"error":["EAPI:Rate limit exceeded"]}
            """;

    private static final String UN_TRADE = """
            {"error":[],"result":{"count":1,"trades":{"T1":{
              "ordertxid":"O1","pair":"XXBTZUSD","time":1758153600.5,"type":"buy",
              "ordertype":"limit","price":"100","cost":"100","fee":"0.4","vol":"1","margin":"0"}}}}
            """;

    @Test
    void ritentaQuandoKrakenRifiutaPerFrequenzaEFirmaDiNuovo() {
        RestClient.Builder costruttore = RestClient.builder().baseUrl("https://api.kraken.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(costruttore).build();
        List<String> firme = new ArrayList<>();
        server.expect(requestTo("https://api.kraken.com/0/private/TradesHistory"))
                .andExpect(r -> firme.add(r.getHeaders().getFirst("API-Sign")))
                .andRespond(withSuccess(LIMITE_SUPERATO, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.kraken.com/0/private/TradesHistory"))
                .andExpect(r -> firme.add(r.getHeaders().getFirst("API-Sign")))
                .andRespond(withSuccess(UN_TRADE, MediaType.APPLICATION_JSON));

        var proprieta = new KrakenProperties("https://api.kraken.com", "chiave", SEGRETO);
        var client = new KrakenClient(costruttore.build(), new KrakenSigner(proprieta), proprieta, Duration.ZERO);

        var trade = client.fetchTrades(Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-24T00:00:00Z"));

        assertEquals(1, trade.size());
        assertNotEquals(firme.get(0), firme.get(1), "un nonce riusato verrebbe rifiutato da Kraken");
        server.verify();
    }
}
