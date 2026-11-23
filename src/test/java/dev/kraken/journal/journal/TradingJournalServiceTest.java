package dev.kraken.journal.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.kraken.KrakenApiException;
import dev.kraken.journal.kraken.KrakenClient;
import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.kraken.dto.KrakenTrade;
import dev.kraken.journal.mercato.Intervallo;
import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.registro.ServizioRegistro;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.scansione.TipoOrdine;

/**
 * Il servizio mette insieme Kraken, calcolo e commento. Kraken e registro sono
 * finti; calcolo e commento sono quelli veri, perche' sono funzioni pure.
 */
class TradingJournalServiceTest {

    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");

    @Test
    void senzaOperazioniNelPeriodoIlCommentoLoDice() {
        KrakenClient kraken = mock(KrakenClient.class);
        when(kraken.fetchTrades(any(), any())).thenReturn(List.of());

        var risposta = servizio(kraken).buildJournal(30);

        assertEquals("Nessuna operazione negli ultimi 30 giorni.", risposta.report().summary());
    }

    @Test
    void conUnOperazioneIlCommentoNasceDalleRegole() {
        var report = servizioCon(unAcquisto()).buildJournal(30).report();

        assertTrue(report.summary().startsWith("Dal "), report.summary());
        assertTrue(report.summary().contains("1 ordine di trading"), report.summary());
        assertTrue(report.dataCaveat().endsWith("non vale come calcolo fiscale."), report.dataCaveat());
    }

    /** Il punto di togliere il modello: stessi dati, stesso testo. */
    @Test
    void stessiDatiStessoCommento() {
        var servizio = servizioCon(unAcquisto());
        assertEquals(servizio.buildJournal(30).report().observedPatterns(),
                servizio.buildJournal(30).report().observedPatterns());
    }

    /**
     * Il costo di carico dipende da acquisti che possono essere molto prima
     * del periodo: lo storico va chiesto dall'inizio, non dall'inizio della
     * finestra.
     */
    @Test
    void loStoricoSiChiedeDallInizioDelConto() {
        KrakenClient kraken = mock(KrakenClient.class);
        when(kraken.fetchTrades(any(), any())).thenReturn(List.of(unAcquisto()));

        servizio(kraken).buildJournal(30);

        verify(kraken).fetchTrades(eq(Instant.EPOCH), any());
        verify(kraken).fetchLedger(eq(Instant.EPOCH), any());
    }

    /** Una chiave senza il permesso sul ledger non deve far sparire le metriche. */
    @Test
    void senzaIlPermessoSulLedgerLeMetricheArrivanoLoStesso() {
        KrakenClient kraken = mock(KrakenClient.class);
        when(kraken.fetchTrades(any(), any())).thenReturn(List.of(unAcquisto()));
        when(kraken.fetchLedger(any(), any()))
                .thenThrow(new KrakenApiException("/0/private/Ledgers", List.of("EGeneral:Permission denied")));

        var risposta = servizio(kraken).buildJournal(30);

        assertEquals(1, risposta.stats().tradingTrades());
        assertFalse(risposta.stats().ledgerLetto(), "la pagina deve poter dire che mancano le operazioni dell'app");
        assertTrue(risposta.report().dataCaveat().startsWith("La chiave API non può leggere il ledger"));
    }

    /** Un'operazione di un anno fa non e' un'operazione degli ultimi trenta giorni. */
    @Test
    void unOperazionePrimaDelPeriodoNonContaComeAttivita() {
        var risposta = servizioCon(acquisto(Instant.now().minus(400, ChronoUnit.DAYS))).buildJournal(30);

        assertTrue(risposta.report().summary().startsWith("Nessuna operazione"));
        assertEquals(1, risposta.stats().posizioniAperte().size(), "la posizione resta aperta anche fuori periodo");
        assertTrue(risposta.report().observedPatterns().get(0).startsWith("Resta aperta una posizione su XBT/USD"));
    }

    /**
     * Comprato in USD e venduto in EUR: il servizio chiede a Kraken il cambio
     * fra le due valute (prima EURUSD, il verso in cui e' quotato) e la
     * vendita ha un risultato: 100,40 USD di costo a 1,10 sono 91,27 EUR,
     * contro un ricavo netto di 99,60 EUR.
     */
    @Test
    void conDueValuteIlServizioChiedeIlCambioEDaUnRisultato() {
        KrakenClient kraken = mock(KrakenClient.class);
        Instant ieri = Instant.now().minus(1, ChronoUnit.DAYS);
        when(kraken.fetchTrades(any(), any())).thenReturn(List.of(acquisto(ieri.minus(2, ChronoUnit.DAYS)),
                new KrakenTrade("O2", "XXBTZEUR", BigDecimal.valueOf(ieri.getEpochSecond()), "sell", "limit",
                        new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("0.4"),
                        BigDecimal.ONE, BigDecimal.ZERO, true)));
        MercatoClient mercato = mock(MercatoClient.class);
        when(mercato.candeleChiuse("EURUSD", Intervallo.GIORNALIERO)).thenReturn(List.of(
                new Candela(ieri.minus(10, ChronoUnit.DAYS), 1.1, 1.1, 1.1, 1.1, 1.1, 1000)));

        var coppia = servizio(kraken, mercato).buildJournal(30).stats().byPair().stream()
                .filter(p -> p.displayName().equals("XBT/EUR")).findFirst().orElseThrow();

        assertTrue(coppia.pnlComplete());
        assertEquals(0, new BigDecimal("8.33").compareTo(coppia.realizedPnl().setScale(2, RoundingMode.HALF_UP)),
                coppia.realizedPnl().toPlainString());
    }

    private static TradingJournalService servizioCon(KrakenTrade trade) {
        KrakenClient kraken = mock(KrakenClient.class);
        when(kraken.fetchTrades(any(), any())).thenReturn(List.of(trade));
        return servizio(kraken);
    }

    private static TradingJournalService servizio(KrakenClient kraken) {
        return servizio(kraken, mock(MercatoClient.class));
    }

    private static TradingJournalService servizio(KrakenClient kraken, MercatoClient mercato) {
        var scansione = new ProprietaScansione(List.of("XBTEUR"), 1_000_000, 50, 70, 12, 5, 10, 2.0,
                203, 1, 5, 0.5, 15, 15, 2, TipoOrdine.LIMITE, 20, 1.5);
        var commento = new ProprietaCommento(LocalDate.of(2026, 11, 19), 50, 50, 2, 3);
        return new TradingJournalService(kraken, new StoriaOperazioni(new PairResolver()),
                new TradeStatisticsCalculator(new ProprietaJournal(BigDecimal.ONE), ROMA),
                new GeneratoreCommento(commento, scansione, new PairResolver(), ROMA),
                mock(ServizioRegistro.class), mercato);
    }

    /** Ieri: il periodo parte da adesso meno N giorni, quindi la data deve seguire l'orologio. */
    private static KrakenTrade unAcquisto() {
        return acquisto(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private static KrakenTrade acquisto(Instant quando) {
        return new KrakenTrade("O1", "XXBTZUSD", BigDecimal.valueOf(quando.getEpochSecond()), "buy", "limit",
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("0.4"),
                BigDecimal.ONE, BigDecimal.ZERO, true);
    }
}
