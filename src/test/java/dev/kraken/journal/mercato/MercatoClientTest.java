package dev.kraken.journal.mercato;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import dev.kraken.journal.mercato.dto.Candela;

/**
 * Il test che conta di questo modulo: la candela in formazione non deve mai
 * uscire dal client. E' l'errore che in un backtest produce risultati
 * bellissimi e in reale perdite, e non si vede leggendo il codice.
 */
class MercatoClientTest {

    private static final String TRE_CANDELE = """
            {"error":[],"result":{"XXBTZUSD":[
              [1758153600,"100","110","90","105","102","10",5],
              [1758240000,"105","115","95","110","107","12",6],
              [1758326400,"110","112","108","111","111","3",2]
            ],"last":1758326400}}
            """;

    @Test
    void scartaLaCandelaInFormazione() {
        RestClient.Builder costruttore = RestClient.builder().baseUrl("https://api.kraken.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(costruttore).build();
        server.expect(requestTo(Matchers.containsString("/0/public/OHLC")))
                .andRespond(withSuccess(TRE_CANDELE, MediaType.APPLICATION_JSON));

        List<Candela> candele = new MercatoClient(costruttore.build())
                .candeleChiuse("XBTUSD", Intervallo.GIORNALIERO);

        assertEquals(2, candele.size(), "la terza candela e' quella di oggi, non e' chiusa");
        assertEquals(110, candele.get(1).chiusura(), 0.0001);
        server.verify();
    }

    @Test
    void ritentaQuandoKrakenRifiutaPerTroppeRichieste() {
        RestClient.Builder costruttore = RestClient.builder().baseUrl("https://api.kraken.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(costruttore).build();
        server.expect(requestTo(Matchers.containsString("/0/public/OHLC")))
                .andRespond(withSuccess("{\"error\":[\"EGeneral:Too many requests\"]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.containsString("/0/public/OHLC")))
                .andRespond(withSuccess(TRE_CANDELE, MediaType.APPLICATION_JSON));

        List<Candela> candele = new MercatoClient(costruttore.build(), Duration.ZERO)
                .candeleChiuse("XBTUSD", Intervallo.GIORNALIERO);

        assertEquals(2, candele.size(), "la coppia non deve sparire dalla scansione del giorno");
        server.verify();
    }

    @Test
    void ilControvaloreUsaIlPrezzoMedioNonLaChiusura() {
        Candela candela = new Candela(java.time.Instant.EPOCH, 100, 110, 90, 105, 102, 10);
        assertEquals(1020, candela.controvalore(), 0.0001);
    }
}
