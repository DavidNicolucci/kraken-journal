package dev.kraken.journal.scansione;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

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

    /**
     * Il caso che mancava: finche' nessun test fa passare tutti e sei i
     * controlli, una scansione rotta che boccia sempre e una scansione corretta
     * in un mercato in discesa producono lo stesso identico risultato.
     */
    @Test
    void unaCoppiaInTendenzaRialzistaSuperaTuttiESeiIControlli() {
        var proprieta = proprieta(List.of("XBTUSD"));
        MercatoClient mercato = mock(MercatoClient.class);
        when(mercato.quotazioni(List.of("XBTUSD")))
                .thenReturn(Map.of("XXBTZUSD", quotazione("XXBTZUSD", 166)));
        when(mercato.candeleChiuse(any(), any(Intervallo.class))).thenReturn(candeleConRitracciamenti());

        Candidato candidato = new ServizioScansione(mercato, new ValutatoreSetup(proprieta), proprieta)
                .scansiona().candidati().get(0);

        assertTrue(candidato.tuttiSuperati(), bocciati(candidato));
        assertEquals(6, candidato.superati());
    }

    /**
     * Regressione: distanzaDallaMedia() ha il segno, quindi senza valore
     * assoluto un prezzo molto SOTTO la media superava il controllo che
     * dovrebbe misurare quanto il prezzo e' scappato dalla media.
     */
    @Test
    void unPrezzoMoltoSottoLaMediaNonSuperaIlControlloSullaDistanza() {
        var proprieta = proprieta(List.of("XBTUSD"));
        MercatoClient mercato = mock(MercatoClient.class);
        when(mercato.quotazioni(List.of("XBTUSD")))
                .thenReturn(Map.of("XXBTZUSD", quotazione("XXBTZUSD", 89)));
        when(mercato.candeleChiuse(any(), any(Intervallo.class)))
                .thenReturn(candeleCrollateDopoLaSalita());

        Candidato candidato = new ServizioScansione(mercato, new ValutatoreSetup(proprieta), proprieta)
                .scansiona().candidati().get(0);

        assertEquals(-38.5, candidato.indicatori().distanzaDallaMedia(), 0.1);
        assertEquals(Esito.BOCCIATO, esitoDi(candidato, Controllo.DISTANZA_DALLA_MEDIA),
                "col segno questo controllo passava: -38,5 e' pur sempre <= 12");
    }

    private static Quotazione quotazione(String nome) {
        return quotazione(nome, 180);
    }

    private static Quotazione quotazione(String nome, double ultimo) {
        return new Quotazione(nome, ultimo, 100_000, ultimo, ultimo * 0.95, ultimo * 1.05, 5_000);
    }

    private static List<Candela> candeleInSalita() {
        return serie(giorno -> 100 + giorno);
    }

    /**
     * Salita con ritracciamenti settimanali. Serve perche' una serie monotona
     * manda l'RSI di Wilder a 100 e non superera' mai il controllo sul momento:
     * questa si ferma a 60, dentro la fascia utile.
     */
    private static List<Candela> candeleConRitracciamenti() {
        return serie(giorno -> 100 + giorno * 0.75 + 4 * Math.sin((giorno + 3) * 2 * Math.PI / 7));
    }

    /** Settanta giorni di salita e dieci di crollo: media ancora alta, prezzo molto sotto. */
    private static List<Candela> candeleCrollateDopoLaSalita() {
        return serie(giorno -> giorno < 70 ? 100 + giorno : 169 - 8.0 * (giorno - 69));
    }

    private static List<Candela> serie(IntToDoubleFunction chiusuraDi) {
        List<Candela> candele = new ArrayList<>();
        Instant inizio = Instant.parse("2026-06-01T00:00:00Z");
        for (int giorno = 0; giorno < 80; giorno++) {
            double chiusura = chiusuraDi.applyAsDouble(giorno);
            candele.add(new Candela(inizio.plus(giorno, ChronoUnit.DAYS),
                    chiusura - 1, chiusura + 2, chiusura - 3, chiusura, chiusura, 10_000));
        }
        return candele;
    }

    private static ProprietaScansione proprieta() {
        return proprieta(WATCHLIST);
    }

    private static ProprietaScansione proprieta(List<String> watchlist) {
        return new ProprietaScansione(watchlist, 1_000_000, 50, 70, 12, 5, 10, 2.0,
                203, 1, 5, 0.5, 15, 15, 2, TipoOrdine.LIMITE);
    }

    private static Esito esitoDi(Candidato candidato, Controllo controllo) {
        return candidato.controlli().stream()
                .filter(c -> c.controllo() == controllo)
                .findFirst().orElseThrow().esito();
    }

    /** Messaggio di errore utile: senza, un 5/6 non dice quale controllo e' caduto. */
    private static String bocciati(Candidato candidato) {
        return candidato.controlli().stream()
                .filter(c -> c.esito() == Esito.BOCCIATO)
                .map(c -> c.controllo() + " " + c.valore() + " (serve " + c.soglia() + ")")
                .toList().toString();
    }
}
