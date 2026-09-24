package dev.kraken.journal.scansione;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.kraken.KrakenApiException;
import dev.kraken.journal.mercato.Intervallo;
import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.mercato.dto.Quotazione;
import dev.kraken.journal.scansione.dto.Candidato;

class ServizioScansioneTest {

    private static final List<String> WATCHLIST = List.of("XBTUSD", "ETHUSD", "TOLTAUSD");

    @Test
    void seIlTickerInBloccoVieneRifiutatoChiedeLeQuotazioniUnaPerUna() {
        MercatoClient mercato = mock(MercatoClient.class);
        when(mercato.quotazioni(WATCHLIST)).thenThrow(
                new KrakenApiException("/0/public/Ticker", List.of("EQuery:Unknown asset pair")));
        when(mercato.quotazioni(List.of("XBTUSD"))).thenReturn(Map.of("XXBTZUSD", quotazione("XXBTZUSD")));
        when(mercato.quotazioni(List.of("ETHUSD"))).thenReturn(Map.of("XETHZUSD", quotazione("XETHZUSD")));
        when(mercato.quotazioni(List.of("TOLTAUSD"))).thenThrow(
                new KrakenApiException("/0/public/Ticker", List.of("EQuery:Unknown asset pair")));
        when(mercato.candeleChiuse(any(), any(Intervallo.class))).thenReturn(candeleInSalita());

        var proprieta = proprieta();
        var risposta = new ServizioScansione(mercato, new ValutatoreSetup(proprieta), proprieta).scansiona();

        assertEquals(3, risposta.inWatchlist());
        assertEquals(2, risposta.esaminate(), "solo la coppia tolta dal listino resta fuori");
        assertEquals(List.of("ETHUSD", "XBTUSD"),
                risposta.candidati().stream().map(Candidato::coppia).sorted().toList());
    }

    private static Quotazione quotazione(String nome) {
        return new Quotazione(nome, 180, 100_000, 180, 170, 185, 5_000);
    }

    private static List<Candela> candeleInSalita() {
        List<Candela> candele = new ArrayList<>();
        Instant inizio = Instant.parse("2026-06-01T00:00:00Z");
        for (int giorno = 0; giorno < 80; giorno++) {
            double chiusura = 100 + giorno;
            candele.add(new Candela(inizio.plus(giorno, ChronoUnit.DAYS),
                    chiusura - 1, chiusura + 2, chiusura - 3, chiusura, chiusura, 10_000));
        }
        return candele;
    }

    private static ProprietaScansione proprieta() {
        return new ProprietaScansione(WATCHLIST, 1_000_000, 50, 70, 12, 5, 10, 2.0,
                203, 1, 5, 0.5, 15, 15, 2, TipoOrdine.LIMITE);
    }
}
