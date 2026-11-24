package dev.kraken.journal.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;

import dev.kraken.journal.kraken.KrakenApiException;
import dev.kraken.journal.kraken.KrakenClient;
import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.kraken.Storico;
import dev.kraken.journal.kraken.dto.KrakenTrade;
import dev.kraken.journal.mercato.Intervallo;
import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.registro.OperazioneRegistrata;
import dev.kraken.journal.registro.RisolutoreOperazione;
import dev.kraken.journal.registro.ServizioRegistro;
import dev.kraken.journal.registro.StatoOperazione;
import dev.kraken.journal.registro.Trigger;
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
        KrakenClient kraken = kraken();
        
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
        KrakenClient kraken = kraken();
        when(kraken.fetchTrades(any(), any())).thenReturn(storico(unAcquisto()));

        servizio(kraken).buildJournal(30);

        verify(kraken).fetchTrades(eq(Instant.EPOCH), any());
        verify(kraken).fetchLedger(eq(Instant.EPOCH), any());
    }

    /** Una chiave senza il permesso sul ledger non deve far sparire le metriche. */
    @Test
    void senzaIlPermessoSulLedgerLeMetricheArrivanoLoStesso() {
        KrakenClient kraken = kraken();
        when(kraken.fetchTrades(any(), any())).thenReturn(storico(unAcquisto()));
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
        KrakenClient kraken = kraken();
        Instant ieri = Instant.now().minus(1, ChronoUnit.DAYS);
        when(kraken.fetchTrades(any(), any())).thenReturn(storico(acquisto(ieri.minus(2, ChronoUnit.DAYS)),
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
        KrakenClient kraken = kraken();
        when(kraken.fetchTrades(any(), any())).thenReturn(storico(trade));
        return servizio(kraken);
    }

    private static TradingJournalService servizio(KrakenClient kraken) {
        return servizio(kraken, mock(MercatoClient.class));
    }

    private static TradingJournalService servizio(KrakenClient kraken, MercatoClient mercato) {
        return servizio(kraken, mercato, mock(ServizioRegistro.class));
    }

    private static TradingJournalService servizio(KrakenClient kraken, MercatoClient mercato, ServizioRegistro registro) {
        var scansione = new ProprietaScansione(List.of("XBTEUR"), 1_000_000, 12, 5, 10, 2.0,
                203, 1, 5, 0.5, 15, 15, 2, TipoOrdine.LIMITE, 20, 1.5);
        var commento = new ProprietaCommento(LocalDate.of(2026, 11, 19), 50, 50, 2, 3);
        return new TradingJournalService(kraken, new StoriaOperazioni(new PairResolver()),
                new TradeStatisticsCalculator(new ProprietaJournal(BigDecimal.ONE), ROMA),
                new GeneratoreCommento(commento, scansione, new PairResolver(), ROMA),
                registro, mercato, commento, new PairResolver());
    }

    /** Un Kraken senza operazioni: trade e ledger vuoti e completi. */
    private static KrakenClient kraken() {
        KrakenClient kraken = mock(KrakenClient.class);
        when(kraken.fetchTrades(any(), any())).thenReturn(storico());
        when(kraken.fetchLedger(any(), any())).thenReturn(storico());
        return kraken;
    }

    @SafeVarargs
    private static <T> Storico<T> storico(T... voci) {
        return new Storico<>(List.of(voci), true);
    }

    /** Kraken si e' fermato al tetto di pagine: il commento lo dice. */
    @Test
    void unoStoricoTroncatoVieneDichiarato() {
        KrakenClient kraken = kraken();
        when(kraken.fetchLedger(any(), any())).thenReturn(new Storico<>(List.of(), false));

        var journal = servizio(kraken).buildJournal(30);

        assertFalse(journal.stats().storicoCompleto());
    }

    private static final Instant REGISTRATA = Instant.now().minus(5, ChronoUnit.DAYS);

    /** Entrata 72, stop 68, obiettivo 79,98, quantita' 0,42: il rischio pianificato e' 0,42 x 4,832. */
    private static OperazioneRegistrata reale() {
        return new OperazioneRegistrata("HYPEEUR", Trigger.MANUALE, 72, 68, 79.98, 0.42, 1.53, "prova", true,
                REGISTRATA, TipoOrdine.LIMITE);
    }

    private static ServizioRegistro registroCon(OperazioneRegistrata operazione) {
        ServizioRegistro registro = mock(ServizioRegistro.class);
        when(registro.realiAperte()).thenReturn(List.of(operazione));
        when(registro.tipoOrdine(operazione)).thenReturn(TipoOrdine.LIMITE);
        when(registro.chiudi(any(), any())).thenReturn(true);
        return registro;
    }

    private static KrakenTrade hype(String id, String verso, String prezzo, String costo, String commissione, Instant quando) {
        return new KrakenTrade(id, "HYPEEUR", BigDecimal.valueOf(quando.getEpochSecond()), verso, "limit",
                new BigDecimal(prezzo), new BigDecimal(costo), new BigDecimal(commissione), new BigDecimal("0.42"),
                BigDecimal.ZERO, true);
    }

    /**
     * Comprata un'ora dopo la registrazione e venduta a 80: l'operazione si
     * chiude dagli ordini, all'obiettivo, con l'R del risultato vero
     * (3,1046 EUR) sul rischio pianificato (2,02944 EUR).
     */
    @Test
    void lOperazioneRealeSiChiudeDagliOrdiniVeri() {
        KrakenClient kraken = kraken();
        when(kraken.fetchTrades(any(), any())).thenReturn(storico(
                hype("C1", "buy", "72", "30.24", "0.121", REGISTRATA.plus(1, ChronoUnit.HOURS)),
                hype("V1", "sell", "80", "33.60", "0.1344", REGISTRATA.plus(2, ChronoUnit.DAYS))));
        var operazione = reale();
        ServizioRegistro registro = registroCon(operazione);

        assertEquals(1, servizio(kraken, mock(MercatoClient.class), registro).chiudiOperazioniReali());

        var chiusura = ArgumentCaptor.forClass(RisolutoreOperazione.Chiusura.class);
        verify(registro).chiudi(any(), chiusura.capture());
        assertEquals(StatoOperazione.CHIUSA_ALL_OBIETTIVO, chiusura.getValue().stato());
        assertEquals(80, chiusura.getValue().prezzoUscita(), 0.000001);
        assertEquals(3.1046 / (0.42 * 4.832), chiusura.getValue().risultatoInR(), 0.000001);
        assertEquals(REGISTRATA.plus(2, ChronoUnit.DAYS).getEpochSecond(), chiusura.getValue().quando().getEpochSecond());
    }

    /** Nessun acquisto nei tre giorni dopo la registrazione: l'operazione non e' mai stata aperta e libera il posto. */
    @Test
    void senzaAcquistoLOperazioneNonEStataEseguita() {
        KrakenClient kraken = kraken();
        var operazione = reale();
        ServizioRegistro registro = registroCon(operazione);

        assertEquals(0, servizio(kraken, mock(MercatoClient.class), registro).chiudiOperazioniReali());

        verify(registro).nonEseguita(any(), any());
        verify(registro, never()).chiudi(any(), any());
    }

    /** Ancora in carico: resta aperta. */
    @Test
    void conLaPosizioneAncoraApertaNonSiChiudeNiente() {
        KrakenClient kraken = kraken();
        when(kraken.fetchTrades(any(), any())).thenReturn(storico(
                hype("C1", "buy", "72", "30.24", "0.121", REGISTRATA.plus(1, ChronoUnit.HOURS))));
        ServizioRegistro registro = registroCon(reale());

        assertEquals(0, servizio(kraken, mock(MercatoClient.class), registro).chiudiOperazioniReali());

        verify(registro, never()).chiudi(any(), any());
        verify(registro, never()).nonEseguita(any(), any());
    }

    /** Senza ledger un acquisto dall'app non si vede: meglio non decidere che chiamarla mai eseguita. */
    @Test
    void senzaLedgerNonSiDecideNiente() {
        KrakenClient kraken = kraken();
        when(kraken.fetchLedger(any(), any())).thenThrow(new KrakenApiException("/0/private/Ledgers", List.of("EGeneral:Permission denied")));
        ServizioRegistro registro = registroCon(reale());

        assertEquals(0, servizio(kraken, mock(MercatoClient.class), registro).chiudiOperazioniReali());

        verify(registro, never()).nonEseguita(any(), any());
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
