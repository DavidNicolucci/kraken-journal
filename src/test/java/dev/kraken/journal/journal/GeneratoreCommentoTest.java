package dev.kraken.journal.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.journal.dto.JournalReport;
import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.kraken.dto.KrakenLedgerEntry;
import dev.kraken.journal.kraken.dto.KrakenTrade;
import dev.kraken.journal.registro.OperazioneRegistrata;
import dev.kraken.journal.registro.Trigger;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.scansione.TipoOrdine;

/**
 * Un test per ogni regola di docs/regole-commento-journal.md, con lo stesso
 * codice nel nome. I dati passano dal calcolo vero: e' cosi' che arrivano al
 * commento anche nell'applicazione.
 */
class GeneratoreCommentoTest {

    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    private static final Instant DA = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant A = Instant.parse("2026-12-01T00:00:00Z");

    private final AtomicInteger progressivo = new AtomicInteger();
    private final List<KrakenTrade> trade = new ArrayList<>();
    private final List<KrakenLedgerEntry> ledger = new ArrayList<>();
    private final List<OperazioneRegistrata> registro = new ArrayList<>();
    private boolean ledgerLetto = true;
    private int registrazioniPerGiudizio = 15;
    private LocalDate metodoDal = LocalDate.of(2026, 10, 1);
    private Cambi cambi = Cambi.NESSUNO;

    // ------------------------------------------------------------ supporto

    private GeneratoreCommentoTest ordine(String pair, String tipo, String costo, String commissione, String quantita,
                                          String quando, Boolean maker) {
        trade.add(new KrakenTrade("O" + progressivo.incrementAndGet(), pair,
                BigDecimal.valueOf(Instant.parse(quando).getEpochSecond()), tipo, "limit",
                new BigDecimal(costo).divide(new BigDecimal(quantita), java.math.MathContext.DECIMAL64),
                new BigDecimal(costo), new BigDecimal(commissione), new BigDecimal(quantita), BigDecimal.ZERO, maker));
        return this;
    }

    private GeneratoreCommentoTest compra(String pair, String costo, String quantita, String quando) {
        return ordine(pair, "buy", costo, percento(costo, "0.004"), quantita, quando, true);
    }

    private GeneratoreCommentoTest vendi(String pair, String costo, String quantita, String quando) {
        return ordine(pair, "sell", costo, percento(costo, "0.004"), quantita, quando, true);
    }

    private GeneratoreCommentoTest dallApp(String valuta, String spesa, String moneta, String quantita, String quando) {
        BigDecimal tempo = BigDecimal.valueOf(Instant.parse(quando).getEpochSecond());
        String ref = "A" + progressivo.incrementAndGet();
        ledger.add(new KrakenLedgerEntry(ref, tempo, "spend", "", valuta, new BigDecimal(spesa).negate(),
                new BigDecimal(spesa).multiply(new BigDecimal("0.015"))));
        ledger.add(new KrakenLedgerEntry(ref, tempo, "receive", "", moneta, new BigDecimal(quantita), BigDecimal.ZERO));
        return this;
    }

    private static String percento(String costo, String aliquota) {
        return new BigDecimal(costo).multiply(new BigDecimal(aliquota)).toPlainString();
    }

    private JournalReport commento() {
        var proprietaScansione = new ProprietaScansione(List.of("XBTEUR", "ETHEUR", "SOLEUR"), 1_000_000, 12,
                5, 10, 2.0, 203, 1, 5, 0.5, 15, registrazioniPerGiudizio, 2, TipoOrdine.LIMITE, 20, 1.5);
        var generatore = new GeneratoreCommento(new ProprietaCommento(metodoDal, 50, 50, 2, 3),
                proprietaScansione, new PairResolver(), ROMA);
        var storia = new StoriaOperazioni(new PairResolver()).componi(trade, ledgerLetto ? ledger : null);
        var stats = new TradeStatisticsCalculator(new ProprietaJournal(BigDecimal.ONE), ROMA).compute(storia, cambi, DA, A);
        return generatore.genera(stats, 30, registro);
    }

    private static String voce(JournalReport r, String inizio) {
        return r.observedPatterns().stream().filter(v -> v.startsWith(inizio)).findFirst()
                .orElseThrow(() -> new AssertionError("nessuna voce che inizi con \"" + inizio + "\" in " + r.observedPatterns()));
    }

    private static boolean nessunaVoce(JournalReport r, String contiene) {
        return r.observedPatterns().stream().noneMatch(v -> v.contains(contiene));
    }

    // ------------------------------------------------------------ A

    @Test
    void a1_nessunaOperazione() {
        var r = commento();
        assertEquals("Nessuna operazione negli ultimi 30 giorni.", r.summary());
        assertEquals("Nessun dato da analizzare nel periodo.", r.dataCaveat());
    }

    @Test
    void a2_soloConversioni() {
        ordine("ZEURZUSD", "sell", "117.00", "0.23", "100", "2026-11-05T10:00:00Z", null);
        var r = commento();
        assertEquals("Negli ultimi 30 giorni c'è solo una conversione valutaria e nessuna operazione di trading.",
                r.summary());
        assertTrue(voce(r, "Hai fatto 1 conversione valutaria, costata 0,23 USD").contains("(lo 0,20% del convertito)"));
    }

    // ------------------------------------------------------------ C

    @Test
    void c1_c2_attivitaECostoInUnaValuta() {
        compra("SOLEUR", "100.00", "1", "2026-11-05T10:00:00Z");
        compra("XBTEUR", "50.00", "0.001", "2026-11-06T10:00:00Z");
        var r = commento();
        assertTrue(r.summary().startsWith("Dal 1 novembre 2026 al 1 dicembre 2026 hai fatto 2 ordini di trading "
                + "su 2 coppie, in 2 giorni diversi. Le commissioni di trading sono state 0,60 EUR, pari allo 0,40% "
                + "del volume."), r.summary());
    }

    @Test
    void c1_ordiniDallAppEConversioni() {
        dallApp("ZEUR", "20.00", "SOL", "0.2", "2026-11-05T10:00:00Z");
        compra("SOLEUR", "100.00", "1", "2026-11-06T10:00:00Z");
        ordine("ZEURZUSD", "sell", "117.00", "0.23", "100", "2026-11-07T10:00:00Z", null);
        var r = commento();
        assertTrue(r.summary().contains("hai fatto 2 ordini di trading (1 dall'app) su 1 coppia, in 2 giorni diversi. "
                + "A parte, 1 conversione valutaria."),
                r.summary());
    }

    @Test
    void c2_dueValuteDueImporti() {
        compra("SOLEUR", "100.00", "1", "2026-11-05T10:00:00Z");
        ordine("SOLUSD", "buy", "50.00", "0.40", "0.5", "2026-11-06T10:00:00Z", false);
        var r = commento();
        assertTrue(r.summary().contains("Le commissioni di trading sono state 0,40 EUR (lo 0,40% del volume) "
                + "e 0,40 USD (lo 0,80%)."), r.summary());
    }

    @Test
    void c3_soloAcquisti() {
        compra("SOLEUR", "100.00", "1", "2026-11-05T10:00:00Z");
        assertTrue(commento().summary().endsWith("Nel periodo hai solo comprato: non c'è ancora un risultato realizzato."));
    }

    @Test
    void c3_b1_pocheOperazioniChiuse() {
        compra("SOLEUR", "100.00", "1", "2026-11-05T10:00:00Z");
        vendi("SOLEUR", "120.00", "1", "2026-11-08T10:00:00Z");
        var r = commento();
        assertTrue(r.summary().endsWith("Il risultato realizzato è +19,12 EUR, su 1 operazione chiusa: "
                + "non basta per dire come è andato il periodo."), r.summary());
        assertTrue(r.dataCaveat().contains("C'è una sola operazione chiusa con un risultato calcolabile: "
                + "sotto 15 il risultato dice poco sul metodo."));
    }

    @Test
    void c3_campioneSufficiente() {
        registrazioniPerGiudizio = 2;
        compra("SOLEUR", "100.00", "1", "2026-11-05T10:00:00Z");
        vendi("SOLEUR", "120.00", "1", "2026-11-08T10:00:00Z");
        compra("XBTEUR", "100.00", "0.001", "2026-11-10T10:00:00Z");
        vendi("XBTEUR", "90.00", "0.001", "2026-11-12T10:00:00Z");
        var r = commento();
        assertTrue(r.summary().endsWith(": 1 operazione chiusa su 2 in guadagno."), r.summary());
        assertFalse(r.dataCaveat().contains("dice poco sul metodo"));
    }

    @Test
    void c3_risultatoNonCalcolabile() {
        vendi("LIGHTERUSD", "20.00", "5", "2026-11-05T10:00:00Z");
        assertTrue(commento().summary().endsWith("Il risultato non è calcolabile: nessuna vendita del periodo "
                + "ha un acquisto corrispondente."));
    }

    // ------------------------------------------------------------ B

    @Test
    void b0_ledgerNonLetto() {
        ledgerLetto = false;
        compra("SOLEUR", "100.00", "1", "2026-11-05T10:00:00Z");
        assertTrue(commento().dataCaveat().startsWith("La chiave API non può leggere il ledger"));
    }

    @Test
    void b2_venditaSenzaAcquistoConProvenienza() {
        ledger.add(new KrakenLedgerEntry("D1", BigDecimal.valueOf(Instant.parse("2026-10-01T10:00:00Z").getEpochSecond()),
                "deposit", "", "LIGHTER", new BigDecimal("5"), BigDecimal.ZERO));
        vendi("LIGHTERUSD", "20.00", "5", "2026-11-05T10:00:00Z");
        assertTrue(commento().dataCaveat().contains("Hai venduto monete che non risultano comprate su "
                + "LIGHTER/USD (deposito da un altro wallet): per quella parte il risultato non è calcolabile"));
    }

    @Test
    void b2_provenienzaScrittaUnaVoltaPerGruppo() {
        Instant arrivo = Instant.parse("2026-10-01T10:00:00Z");
        ledger.add(new KrakenLedgerEntry("R1", BigDecimal.valueOf(arrivo.getEpochSecond()), "reward", "", "PEPE",
                new BigDecimal("1000000"), BigDecimal.ZERO));
        ledger.add(new KrakenLedgerEntry("R2", BigDecimal.valueOf(arrivo.getEpochSecond()), "reward", "", "TRX",
                new BigDecimal("10"), BigDecimal.ZERO));
        vendi("PEPEEUR", "4.00", "1000000", "2026-11-05T10:00:00Z");
        vendi("TRXEUR", "2.00", "10", "2026-11-05T11:00:00Z");
        vendi("NEAREUR", "3.00", "1", "2026-11-05T12:00:00Z");
        assertTrue(commento().dataCaveat().contains("Hai venduto monete che non risultano comprate su PEPE/EUR e "
                + "TRX/EUR (ricompense, staking o accrediti) e su NEAR/EUR (provenienza non registrata): per quella parte"));
    }

    @Test
    void b3_venditaInUnAltraValuta() {
        compra("SOLUSD", "100.00", "1", "2026-11-05T10:00:00Z");
        vendi("SOLEUR", "90.00", "1", "2026-11-06T10:00:00Z");
        assertTrue(commento().dataCaveat().contains("Su SOL/EUR hai venduto monete comprate in un'altra valuta, "
                + "in giorni senza un cambio: escono dalle posizioni, ma quella parte non ha un risultato."));
    }

    @Test
    void b3_venditaConvertitaAlCambioDelGiorno() {
        cambi = new Cambi(Map.of("EURUSD", new TreeMap<>(Map.of(LocalDate.of(2026, 11, 1), new BigDecimal("1.10")))));
        dallApp("ZEUR", "100.00", "LIGHTER", "10", "2026-11-05T10:00:00Z");
        vendi("LIGHTERUSD", "120.00", "10", "2026-11-08T10:00:00Z");
        var r = commento();
        assertTrue(r.dataCaveat().contains("Su LIGHTER/USD hai venduto monete comprate in un'altra valuta: il costo è "
                + "convertito al cambio medio del giorno della vendita, preso da Kraken."), r.dataCaveat());
        assertFalse(r.dataCaveat().contains("senza un cambio"), r.dataCaveat());
    }

    @Test
    void b4_b5_b6_valuteDiverseAppEMetodo() {
        dallApp("ZEUR", "20.00", "SOL", "0.2", "2026-11-05T10:00:00Z");
        compra("XBTUSD", "50.00", "0.001", "2026-11-06T10:00:00Z");
        String limiti = commento().dataCaveat();
        assertTrue(limiti.contains("Le operazioni sono in EUR e USD: ogni importo è dato nella sua valuta"));
        assertTrue(limiti.contains("oltre alla commissione c'è lo spread"));
        assertTrue(limiti.endsWith("non vale come calcolo fiscale."));
    }

    // ------------------------------------------------------------ D

    @Test
    void d1_ordiniATariffaTaker() {
        ordine("SOLEUR", "buy", "100.00", "0.80", "1", "2026-11-05T10:00:00Z", false);
        ordine("SOLEUR", "sell", "110.00", "0.44", "1", "2026-11-08T10:00:00Z", true);
        assertTrue(nessunaVoce(commento(), "tariffa taker"), "con il 48% a tariffa taker la regola non scatta");

        ordine("XBTEUR", "buy", "100.00", "0.80", "0.001", "2026-11-10T10:00:00Z", false);
        var r2 = commento();
        assertEquals("Su Kraken Pro il 65% del volume in EUR è stato eseguito subito, alla tariffa taker: 2 acquisti. "
                + "Rispetto alla tariffa maker (0,40%) hai pagato 0,80 EUR in più, il 39% delle commissioni.",
                voce(r2, "Su Kraken Pro"));
        assertTrue(r2.questionsToAskYourself().contains("Nei 2 acquisti eseguiti subito, serviva entrare a quel prezzo "
                + "o un ordine limite sarebbe bastato?"));
    }

    /** Con due valute la seconda non ripete la spiegazione. */
    @Test
    void d1_dueValute() {
        ordine("SOLEUR", "buy", "100.00", "0.80", "1", "2026-11-05T10:00:00Z", false);
        ordine("SOLUSD", "sell", "50.00", "0.40", "0.5", "2026-11-06T10:00:00Z", false);
        String voce = voce(commento(), "Su Kraken Pro");
        assertTrue(voce.endsWith(" In USD il 100%, con 1 vendita: 0,20 USD in più, il 50% delle commissioni."), voce);
    }

    @Test
    void d2_operazioniDallApp() {
        dallApp("ZEUR", "20.00", "SOL", "0.2", "2026-11-05T10:00:00Z");
        assertEquals("1 ordine è passato dall'app: 20,00 EUR con una commissione media dell'1,50%, più lo spread che "
                + "sta nel prezzo, contro lo 0,40% di un ordine limite su Kraken Pro.", voce(commento(), "1 ordine"));
    }

    /** Ha la priorita' piu' bassa: qui scatta solo E4, cosi' resta nelle cinque voci. */
    @Test
    void d3_pareggio() {
        registro.add(new OperazioneRegistrata("SOLEUR", Trigger.BREAKOUT_20_GIORNI, 100, 94, 112, 0.2, 2,
                "rottura", true, Instant.parse("2026-11-28T07:00:00Z"), TipoOrdine.LIMITE));
        ordine("SOLEUR", "buy", "20.00", "0.16", "0.2", "2026-11-28T10:00:00Z", false);
        assertEquals("Con commissioni medie dello 0,80% per lato, il prezzo deve salire almeno dell'1,61% "
                + "solo per chiudere in pari.", voce(commento(), "Con commissioni medie"));
    }

    @Test
    void d4_conversioniConDomandaSullaValutaDellaWatchlist() {
        compra("SOLEUR", "100.00", "1", "2026-11-05T10:00:00Z");
        ordine("ZEURZUSD", "sell", "117.00", "0.23", "100", "2026-11-05T10:00:00Z", null);
        ordine("ZEURZUSD", "sell", "117.00", "0.23", "100", "2026-11-06T10:00:00Z", null);
        var r = commento();
        assertTrue(voce(r, "Hai fatto 2 conversioni valutarie, costate 0,46 USD").endsWith("non dal mercato."));
    }

    // ------------------------------------------------------------ E

    @Test
    void e8_acquistoSenzaRegistrazione() {
        compra("SOLEUR", "30.00", "0.3", "2026-11-20T10:00:00Z");
        var r = commento();
        assertEquals("L'acquisto di SOL/EUR del 20 novembre 2026 non ha un'operazione reale nel registro.",
                r.observedPatterns().get(0), "e' la prima per priorita'");
        assertEquals("Dove erano scritti stop, obiettivo e motivo di quell'acquisto?", r.questionsToAskYourself().get(0));
    }

    @Test
    void e8_acquistoRegistratoPrima() {
        registro.add(new OperazioneRegistrata("SOLEUR", Trigger.BREAKOUT_20_GIORNI, 100, 94, 112, 0.3, 2,
                "rottura", true, Instant.parse("2026-11-20T07:00:00Z"), TipoOrdine.LIMITE));
        compra("SOLEUR", "30.00", "0.3", "2026-11-20T10:00:00Z");
        assertTrue(nessunaVoce(commento(), "nel registro"));
    }

    private void registraReale(String coppia, double quantita, String quando) {
        registro.add(new OperazioneRegistrata(coppia, Trigger.BREAKOUT_20_GIORNI, 100, 94, 112, quantita, 2,
                "rottura", true, Instant.parse(quando), TipoOrdine.LIMITE));
    }

    /** Un'operazione eseguita in due ordini e' coperta per intero. */
    @Test
    void e8_unaRegistrazioneCopreLaSuaQuantitaAnchePiuOrdini() {
        registraReale("SOL/EUR", 0.3, "2026-11-20T07:00:00Z");
        compra("SOLEUR", "20.00", "0.2", "2026-11-20T10:00:00Z");
        compra("SOLEUR", "10.00", "0.1", "2026-11-20T11:00:00Z");
        assertTrue(nessunaVoce(commento(), "nel registro"));
    }

    /** Riempita la quantita' registrata, un altro acquisto nei giorni dopo non e' coperto. */
    @Test
    void e8_dopoAverRiempitoLaRegistrazioneUnAltroAcquistoNo() {
        registraReale("SOLEUR", 0.3, "2026-11-20T07:00:00Z");
        compra("SOLEUR", "30.00", "0.3", "2026-11-20T10:00:00Z");
        compra("SOLEUR", "30.00", "0.3", "2026-11-21T10:00:00Z");
        assertEquals("1 acquisto su 2 non ha un'operazione reale nel registro: SOL/EUR del 21 novembre 2026.",
                voce(commento(), "1 acquisto su 2"));
    }

    /** BTC/EUR nel registro e' la XBT/EUR degli ordini; la stessa moneta in un'altra valuta no. */
    @Test
    void e8_laCoppiaRegistrataVieneRiconosciutaAncheConIlNomeComune() {
        registraReale("BTC/EUR", 0.001, "2026-11-20T07:00:00Z");
        compra("XXBTZEUR", "90.00", "0.001", "2026-11-20T10:00:00Z");
        compra("XBTUSD", "100.00", "0.001", "2026-11-20T11:00:00Z");
        assertEquals("1 acquisto su 2 non ha un'operazione reale nel registro: XBT/USD del 20 novembre 2026.",
                voce(commento(), "1 acquisto su 2"));
    }

    @Test
    void e9_dimensioneOltreIlMetodo() {
        compra("SOLEUR", "60.00", "0.6", "2026-11-20T10:00:00Z");
        String voce = voce(commento(), "1 posizione supera");
        assertEquals("1 posizione supera i 33 EUR, la dimensione più alta che il metodo consente con il rischio "
                + "dell'1% e lo stop più stretto ammesso (5%): SOL/EUR per 60,24 EUR.", voce);
    }

    /** Due acquisti da 25 EUR passavano uno per uno: insieme sono una posizione da 50. */
    @Test
    void e9_contaLaPosizioneNonIlSingoloOrdine() {
        compra("SOLEUR", "25.00", "0.25", "2026-11-20T10:00:00Z");
        compra("SOLEUR", "25.00", "0.25", "2026-11-21T10:00:00Z");
        vendi("SOLEUR", "52.00", "0.5", "2026-11-23T10:00:00Z");
        assertTrue(voce(commento(), "1 posizione supera").endsWith("SOL/EUR per 50,20 EUR."));
    }

    /** Una posizione aperta prima del metodo e ancora aperta dopo occupa un posto lo stesso. */
    @Test
    void e10_contaAncheLePosizioniApertePrimaDelMetodo() {
        metodoDal = LocalDate.of(2026, 11, 10);
        compra("SOLEUR", "30.00", "0.3", "2026-11-01T10:00:00Z");
        compra("XBTEUR", "30.00", "0.0003", "2026-11-12T10:00:00Z");
        compra("ETHEUR", "30.00", "0.01", "2026-11-13T10:00:00Z");
        assertEquals("Nel periodo hai tenuto fino a 3 posizioni aperte insieme: la guida ne prevede al massimo 2.",
                voce(commento(), "Nel periodo hai tenuto"));
    }

    /** Quindici giorni e un'ora sono il sedicesimo giorno: oltre il time stop di 15. */
    @Test
    void e7_unaFrazioneDiGiornoOltreIlLimiteConta() {
        compra("SOLEUR", "30.00", "0.3", "2026-11-02T10:00:00Z");
        vendi("SOLEUR", "33.00", "0.3", "2026-11-17T11:00:00Z");
        assertEquals("1 operazione è durata più dei 15 giorni del time stop: SOL/EUR (16 giorni).",
                voce(commento(), "1 operazione è durata"));
    }

    @Test
    void e10_piuPosizioniDelPrevisto() {
        compra("SOLEUR", "30.00", "0.3", "2026-11-05T10:00:00Z");
        compra("XBTEUR", "30.00", "0.0003", "2026-11-06T10:00:00Z");
        compra("ETHEUR", "30.00", "0.01", "2026-11-07T10:00:00Z");
        assertEquals("Nel periodo hai tenuto fino a 3 posizioni aperte insieme: la guida ne prevede al massimo 2.",
                voce(commento(), "Nel periodo hai tenuto"));
    }

    @Test
    void e7_oltreIlTimeStop() {
        compra("SOLEUR", "30.00", "0.3", "2026-11-02T10:00:00Z");
        vendi("SOLEUR", "33.00", "0.3", "2026-11-22T10:00:00Z");
        var r = commento();
        assertEquals("1 operazione è durata più dei 15 giorni del time stop: SOL/EUR (20 giorni).",
                voce(r, "1 operazione è durata"));
        assertTrue(r.questionsToAskYourself().contains("Cosa ti ha tenuto dentro oltre il time stop?"));
    }

    @Test
    void e1_fuoriWatchlistSoloAcquistiESoloDalMetodo() {
        metodoDal = LocalDate.of(2026, 11, 10);
        compra("PEPEEUR", "20.00", "1000000", "2026-11-05T10:00:00Z");
        vendi("PEPEEUR", "21.00", "1000000", "2026-11-12T10:00:00Z");
        compra("UNIEUR", "20.00", "3", "2026-11-15T10:00:00Z");
        compra("SOLEUR", "20.00", "0.2", "2026-11-16T10:00:00Z");
        assertEquals("1 acquisto su 2 è su una moneta fuori dalla watchlist: UNI/EUR.",
                voce(commento(), "1 acquisto su 2 è su"));
    }

    @Test
    void e1_unicoAcquistoFuoriWatchlist() {
        metodoDal = LocalDate.of(2026, 11, 10);
        compra("NEXOEUR", "14.00", "23", "2026-11-15T10:00:00Z");
        assertEquals("L'unico acquisto è su una moneta fuori dalla watchlist: NEXO/EUR.",
                voce(commento(), "L'unico acquisto"));
    }

    @Test
    void e3_troppiOrdiniInUnGiorno() {
        compra("SOLEUR", "20.00", "0.2", "2026-11-05T08:00:00Z");
        vendi("SOLEUR", "21.00", "0.2", "2026-11-05T09:00:00Z");
        compra("XBTEUR", "20.00", "0.0002", "2026-11-05T10:00:00Z");
        compra("ETHEUR", "20.00", "0.007", "2026-11-06T10:00:00Z");
        assertEquals("In 1 giorno su 2 hai fatto più di 2 ordini: la guida prevede una revisione al giorno, a orario fisso.",
                voce(commento(), "In 1 giorno"));
    }

    @Test
    void e2_concentrazione() {
        compra("SOLEUR", "90.00", "0.9", "2026-11-05T10:00:00Z");
        compra("XBTEUR", "10.00", "0.0001", "2026-11-06T10:00:00Z");
        var r = commento();
        assertEquals("SOL/EUR da sola fa il 90% del volume in EUR.", voce(r, "SOL/EUR da sola"));
    }

    @Test
    void e4_posizioneAperta() {
        compra("SOLUSD", "28.46", "0.296", "2026-11-03T10:00:00Z");
        assertEquals("Resta aperta una posizione su SOL/USD, dal 3 novembre 2026, con un costo di carico di 28,57 USD: "
                + "il suo risultato non è ancora realizzato.", voce(commento(), "Resta aperta"));
    }

    @Test
    void e6_risultatoPerOperazione() {
        compra("SOLEUR", "20.00", "0.2", "2026-11-05T10:00:00Z");
        vendi("SOLEUR", "22.00", "0.2", "2026-11-05T18:00:00Z");
        compra("XBTEUR", "20.00", "0.0002", "2026-11-07T10:00:00Z");
        vendi("XBTEUR", "19.00", "0.0002", "2026-11-09T10:00:00Z");
        assertEquals("Operazioni chiuse nel periodo: XBT/EUR −1,16 EUR in 2 giorni e SOL/EUR +1,83 EUR in giornata.",
                voce(commento(), "Operazioni chiuse"));
    }

    @Test
    void alMassimoCinqueVociETreDomande() {
        compra("PEPEEUR", "60.00", "1000000", "2026-11-20T10:00:00Z");
        ordine("UNIEUR", "buy", "60.00", "0.48", "10", "2026-11-20T11:00:00Z", false);
        compra("XBTEUR", "60.00", "0.0006", "2026-11-20T12:00:00Z");
        dallApp("ZEUR", "20.00", "ETH", "0.007", "2026-11-21T10:00:00Z");
        var r = commento();
        assertEquals(5, r.observedPatterns().size(), r.observedPatterns().toString());
        assertEquals(3, r.questionsToAskYourself().size());
    }
}
