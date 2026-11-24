package dev.kraken.journal.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.journal.dto.JournalStats;
import dev.kraken.journal.journal.dto.OperazioneChiusa;
import dev.kraken.journal.journal.dto.PairStats;
import dev.kraken.journal.journal.dto.TotaliValuta;
import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.kraken.dto.KrakenLedgerEntry;
import dev.kraken.journal.kraken.dto.KrakenTrade;

class TradeStatisticsCalculatorTest {

    private final StoriaOperazioni storia = new StoriaOperazioni(new PairResolver());
    private final TradeStatisticsCalculator calculator =
            new TradeStatisticsCalculator(new ProprietaJournal(BigDecimal.ONE), ZoneId.of("Europe/Rome"));
    private final AtomicInteger progressivo = new AtomicInteger();
    private Cambi cambi = Cambi.NESSUNO;

    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-04-01T00:00:00Z");

    private KrakenTrade trade(String pair, String type, String price,
                              String cost, String fee, String vol, long dayOffset) {
        return trade("O" + progressivo.incrementAndGet(), pair, type, cost, fee, vol,
                FROM.plus(dayOffset, ChronoUnit.DAYS), null, price);
    }

    private static KrakenTrade trade(String ordine, String pair, String type, String cost, String fee, String vol,
                                     Instant quando, Boolean maker, String price) {
        return new KrakenTrade(ordine, pair, BigDecimal.valueOf(quando.getEpochSecond()), type, "limit",
                new BigDecimal(price), new BigDecimal(cost), new BigDecimal(fee),
                new BigDecimal(vol), BigDecimal.ZERO, maker);
    }

    private static KrakenLedgerEntry riga(String refid, String type, String subtype, String asset,
                                          String amount, String fee, long dayOffset) {
        return new KrakenLedgerEntry(refid,
                BigDecimal.valueOf(FROM.plus(dayOffset, ChronoUnit.DAYS).getEpochSecond()),
                type, subtype, asset, new BigDecimal(amount), new BigDecimal(fee));
    }

    private JournalStats calcola(KrakenTrade... trades) {
        return calcola(List.of(trades), List.of());
    }

    private JournalStats calcola(List<KrakenTrade> trades, List<KrakenLedgerEntry> ledger) {
        return calculator.compute(storia.componi(trades, ledger), cambi, FROM, TO);
    }

    private static PairStats find(JournalStats stats, String pair) {
        return stats.byPair().stream().filter(p -> p.displayName().equals(pair) || p.pair().equals(pair))
                .findFirst().orElseThrow();
    }

    private static TotaliValuta totali(JournalStats stats, String valuta) {
        return stats.totaliPerValuta().stream().filter(t -> t.valuta().equals(valuta)).findFirst().orElseThrow();
    }

    private static void assertNumero(String atteso, BigDecimal effettivo) {
        assertNotNull(effettivo, "atteso " + atteso + ", ricevuto null");
        assertEquals(0, new BigDecimal(atteso).compareTo(effettivo), "atteso " + atteso + ", ricevuto " + effettivo);
    }

    @Test
    void venditaSenzaAcquistoNonProduceUnPnlInventato() {
        var stats = calcola(trade("LIGHTERUSD", "sell", "3.53", "14.122", "0.05649", "4.0", 1));

        PairStats p = find(stats, "LIGHTERUSD");
        assertNull(p.realizedPnl(), "senza costo di carico il P&L deve essere non calcolabile");
        assertFalse(p.pnlComplete());
        assertNumero("4.0", p.unmatchedSellVolume());
        assertEquals(1, stats.pairsWithIncompletePnl());
    }

    @Test
    void acquistoEVenditaCompletiDannoUnPnlReale() {
        var stats = calcola(
                trade("SOLUSD", "buy", "100", "100.00", "0.00", "1.0", 1),
                trade("SOLUSD", "sell", "120", "120.00", "0.00", "1.0", 2));

        PairStats p = find(stats, "SOLUSD");
        assertTrue(p.pnlComplete());
        assertNumero("20", p.realizedPnl());
        assertNumero("0", p.openPosition());
    }

    @Test
    void leConversioniValutarieNonContanoComeTrade() {
        var stats = calcola(
                trade("ZEURZUSD", "sell", "1.17", "117.00", "0.23", "100.0", 1),
                trade("SOLUSD", "buy", "100", "100.00", "0.20", "1.0", 1));

        assertEquals(2, stats.totalOperations());
        assertEquals(1, stats.tradingTrades(), "solo SOLUSD e' un trade");
        assertEquals(1, stats.fxConversions());
        assertTrue(find(stats, "ZEURZUSD").fxConversion());
        assertFalse(find(stats, "SOLUSD").fxConversion());
    }

    @Test
    void iPrezziSottoUnCentesimoNonVengonoArrotondatiAZero() {
        var stats = calcola(trade("AMIUSD", "sell", "0.00039", "4.20", "0.0168", "10590.2", 1));

        BigDecimal avg = find(stats, "AMIUSD").averageSellPrice();
        assertNotNull(avg);
        assertTrue(avg.compareTo(BigDecimal.ZERO) > 0, "prezzo arrotondato a zero: " + avg);
    }

    @Test
    void leCommissioniSulVolumeSonoSempreCalcolabili() {
        var stats = calcola(trade("SOLUSD", "buy", "100", "100.00", "1.00", "1.0", 1));

        assertNumero("1.00", totali(stats, "USD").commissioniPercentuale());
    }

    /**
     * Il rapporto commissioni/volume deve confrontare grandezze omogenee.
     * Includere le fee di cambio al numeratore mentre il denominatore contiene
     * solo il volume di trading gonfia il risultato con costi estranei.
     */
    @Test
    void leFeeDiCambioNonEntranoNelRapportoSulVolumeDiTrading() {
        var stats = calcola(
                trade("ZEURZUSD", "sell", "1.17", "117.00", "0.30", "100.0", 1),
                trade("SOLUSD", "buy", "100", "100.00", "1.00", "1.0", 1));

        assertNumero("0.30", find(stats, "ZEURZUSD").feesPaid());
        assertNumero("1.00", totali(stats, "USD").commissioni());
        assertNumero("1.00", totali(stats, "USD").commissioniPercentuale());
    }

    /** stripTrailingZeros trasforma 3000 in 3E+3, illeggibile una volta serializzato. */
    @Test
    void iNumeriInteriGrandiRestanoInFormaPiana() {
        var stats = calcola(trade("AMIUSD", "sell", "0.0014", "4.20", "0.0168", "3000", 1));

        BigDecimal unmatched = find(stats, "AMIUSD").unmatchedSellVolume();
        assertTrue(unmatched.scale() >= 0, "notazione esponenziale: " + unmatched);
        assertEquals("3000", unmatched.toString());
    }

    /**
     * Il caso che rendeva inutile la vista a trenta giorni: comprato prima del
     * periodo, venduto dentro. Il costo di carico esiste, sta solo fuori dalla
     * finestra.
     */
    @Test
    void unAcquistoPrimaDelPeriodoDaIlCostoDiCarico() {
        var stats = calcola(
                trade("SOLUSD", "buy", "100", "100.00", "0.00", "1.0", -10),
                trade("SOLUSD", "sell", "120", "120.00", "0.00", "1.0", 5));

        PairStats p = find(stats, "SOLUSD");
        assertTrue(p.pnlComplete());
        assertNumero("20", p.realizedPnl());
        assertEquals(1, stats.tradingTrades(), "l'acquisto non e' un'operazione del periodo");
        assertEquals(0, p.buyCount());
    }

    @Test
    void unaPosizioneComprataPrimaDelPeriodoEAncoraAperta() {
        var stats = calcola(trade("SOLUSD", "buy", "100", "100.00", "0.40", "1.0", -30));

        assertTrue(stats.byPair().isEmpty(), "nel periodo non c'e' nessuna operazione");
        assertEquals(1, stats.posizioniAperte().size());
        var aperta = stats.posizioniAperte().get(0);
        assertEquals("SOL/USD", aperta.coppia());
        assertNumero("100.40", aperta.costo());
    }

    /**
     * Una vendita scoperta non deve cancellare il risultato del giro completo
     * fatto dopo sulla stessa coppia: prima la coppia intera diventava "non
     * calcolabile".
     */
    @Test
    void laParteAbbinataNonVieneButtata() {
        var stats = calcola(
                trade("PEPEEUR", "sell", "1", "100.00", "0.00", "100", 1),
                trade("PEPEEUR", "buy", "1", "50.00", "0.00", "50", 2),
                trade("PEPEEUR", "sell", "1.2", "60.00", "0.00", "50", 3));

        PairStats p = find(stats, "PEPE/EUR");
        assertFalse(p.pnlComplete(), "una parte venduta resta senza costo di carico");
        assertNumero("10", p.realizedPnl());
        assertNumero("100", p.unmatchedSellVolume());
        assertNumero("10", totali(stats, "EUR").risultato());
    }

    /** L'app non passa da TradesHistory: senza il ledger questa vendita sarebbe "senza acquisto". */
    @Test
    void unAcquistoDallAppSiAbbinaAUnaVenditaSuKrakenPro() {
        var stats = calcola(
                List.of(trade("UNIEUR", "sell", "12", "120.00", "0.96", "10", 20)),
                List.of(riga("TAPP1", "spend", "", "ZEUR", "-100.00", "1.50", 10),
                        riga("TAPP1", "receive", "", "UNI", "10", "0", 10)));

        PairStats p = find(stats, "UNI/EUR");
        assertTrue(p.pnlComplete());
        assertNumero("17.54", p.realizedPnl());
        assertEquals(1, p.operazioniDaApp());
        assertNumero("100.00", totali(stats, "EUR").volumeApp());
    }

    /** SOL comprato in dollari e venduto in euro in un giorno senza cambio: esce dal carico, ma non ha un risultato. */
    @Test
    void unaVenditaInUnAltraValutaChiudeLaPosizioneSenzaInventareUnRisultato() {
        var stats = calcola(
                trade("SOLUSD", "buy", "100", "100.00", "0.00", "1.0", 1),
                trade("SOLEUR", "sell", "90", "90.00", "0.00", "1.0", 2));

        PairStats euro = find(stats, "SOL/EUR");
        assertFalse(euro.pnlComplete());
        assertNull(euro.realizedPnl());
        assertNumero("1.0", euro.quantitaInAltraValuta());
        assertNumero("0", euro.unmatchedSellVolume());
        assertTrue(stats.posizioniAperte().isEmpty(), "altrimenti SOL/USD resterebbe aperta per sempre");
        assertNull(stats.operazioniChiuse().get(0).risultato());
    }

    /**
     * LIGHTER comprato in EUR dall'app e venduto in USD su Kraken Pro, con
     * EURUSD a 1,10 il giorno della vendita. Il costo, 101,50 EUR commissione
     * compresa, vale 111,65 USD: il ricavo netto di 119,04 USD da' +7,39 USD
     * sulla coppia, cioe' +6,7182 EUR sull'operazione aperta in euro.
     */
    @Test
    void unaVenditaInUnAltraValutaSiConverteAlCambioDelGiorno() {
        cambi = new Cambi(Map.of("EURUSD", new TreeMap<>(Map.of(
                LocalDate.of(2026, 1, 1), new BigDecimal("1.05"),
                LocalDate.of(2026, 1, 3), new BigDecimal("1.10")))));
        var stats = calcola(
                List.of(trade("LIGHTERUSD", "sell", "12", "120.00", "0.96", "10", 3)),
                List.of(riga("TAPP1", "spend", "", "ZEUR", "-100.00", "1.50", 1),
                        riga("TAPP1", "receive", "", "LIGHTER", "10", "0", 1)));

        PairStats dollari = find(stats, "LIGHTER/USD");
        assertTrue(dollari.pnlComplete());
        assertNumero("7.39", dollari.realizedPnl());
        assertNumero("10", dollari.quantitaConvertita());
        assertNumero("0", dollari.quantitaInAltraValuta());
        assertTrue(stats.posizioniAperte().isEmpty());
        OperazioneChiusa chiusa = stats.operazioniChiuse().get(0);
        assertEquals("EUR", chiusa.valuta());
        assertNumero("101.5", chiusa.investito());
        assertNumero("6.71818182", chiusa.risultato());
    }

    /** Un giorno senza candela prende l'ultimo cambio prima di lui; prima della prima candela non c'e' cambio. */
    @Test
    void ilCambioDiUnGiornoSenzaCandelaEQuelloPrecedente() {
        var tassi = new Cambi(Map.of("EURUSD", new TreeMap<>(Map.of(LocalDate.of(2026, 1, 10), new BigDecimal("1.25")))));
        assertNumero("1.25", tassi.tasso("EUR", "USD", Instant.parse("2026-01-12T22:00:00Z")).orElseThrow());
        assertNumero("0.8", tassi.tasso("USD", "EUR", Instant.parse("2026-01-12T22:00:00Z")).orElseThrow());
        assertTrue(tassi.tasso("EUR", "USD", Instant.parse("2026-01-09T22:00:00Z")).isEmpty());
        assertNumero("1", tassi.tasso("EUR", "EUR", Instant.parse("2020-01-01T00:00:00Z")).orElseThrow());
    }

    @Test
    void leEsecuzioniDelloStessoOrdineSonoUnOrdineSolo() {
        Instant quando = FROM.plus(1, ChronoUnit.DAYS);
        var stats = calcola(
                trade("OX", "XETHZEUR", "buy", "50.00", "0.20", "0.025", quando, true, "2000"),
                trade("OX", "XETHZEUR", "buy", "30.00", "0.12", "0.015", quando.plusSeconds(5), true, "2000"),
                trade("OX", "XETHZEUR", "buy", "20.00", "0.08", "0.010", quando.plusSeconds(9), true, "2000"));

        PairStats p = find(stats, "ETH/EUR");
        assertEquals(3, p.tradeCount());
        assertEquals(1, p.ordini());
        assertEquals(1, stats.ordini().size());
        assertEquals(3, stats.ordini().get(0).esecuzioni());
        assertNumero("1", stats.tradesPerActiveDay());
    }

    @Test
    void laPolvereNonEUnaPosizioneAperta() {
        var stats = calcola(
                trade("HYPEEUR", "buy", "70", "70.00", "0.00", "1.0", 1),
                trade("HYPEEUR", "sell", "78", "77.61", "0.00", "0.995", 2));

        assertNumero("0", find(stats, "HYPE/EUR").openPosition());
        assertTrue(stats.posizioniAperte().isEmpty());
        assertEquals(1, stats.operazioniChiuse().size(), "lo 0,5% rimasto e' polvere: il giro e' chiuso");
    }

    /**
     * Il primo giro lascia 0,005 HYPE: il loro costo, 0,35 EUR, esce dal suo
     * investito e passa al giro dopo, che le vende insieme alle nuove.
     * Investito e risultato di ogni giro parlano della stessa quantita'.
     */
    @Test
    void ilCostoDellaPolverePassaAlGiroSuccessivo() {
        var stats = calcola(
                trade("HYPEEUR", "buy", "70", "70.00", "0.00", "1.0", 1),
                trade("HYPEEUR", "sell", "78", "77.61", "0.00", "0.995", 2),
                trade("HYPEEUR", "buy", "80", "80.00", "0.00", "1.0", 5),
                trade("HYPEEUR", "sell", "90", "90.45", "0.00", "1.005", 6));

        assertEquals(2, stats.operazioniChiuse().size());
        var primo = stats.operazioniChiuse().get(0);
        assertNumero("69.65", primo.investito());
        assertNumero("7.96", primo.risultato());
        assertNumero("70", primo.costoMassimo());
        assertNumero("78", primo.prezzoMedioUscita());
        var secondo = stats.operazioniChiuse().get(1);
        assertNumero("80.35", secondo.investito());
        assertNumero("10.10", secondo.risultato());
        assertNumero("80.35", secondo.costoMassimo());
        assertNumero("90", secondo.prezzoMedioUscita());
    }

    /** Due acquisti da 25 EUR sono una posizione da 50: e' la dimensione che conta per il metodo. */
    @Test
    void laDimensioneDellaPosizioneSommaGliAcquisti() {
        var stats = calcola(
                trade("SOLEUR", "buy", "100", "25.00", "0.00", "0.25", 1),
                trade("SOLEUR", "buy", "100", "25.00", "0.00", "0.25", 2),
                trade("SOLEUR", "sell", "110", "27.50", "0.00", "0.25", 3),
                trade("SOLEUR", "sell", "90", "22.50", "0.00", "0.25", 4));

        var giro = stats.operazioniChiuse().get(0);
        assertNumero("50", giro.costoMassimo());
        assertNumero("100", giro.prezzoMedioUscita());
    }

    /** Un acquisto in un'altra valuta senza cambio: non c'e' ne' il risultato ne' l'investito, che sarebbe parziale. */
    @Test
    void senzaCambioAncheLInvestitoNonECalcolabile() {
        var stats = calcola(
                trade("SOLEUR", "buy", "100", "100.00", "0.00", "1.0", 1),
                trade("SOLUSD", "buy", "110", "110.00", "0.00", "1.0", 2),
                trade("SOLEUR", "sell", "105", "210.00", "0.00", "2.0", 3));

        var giro = stats.operazioniChiuse().get(0);
        assertNull(giro.investito());
        assertNull(giro.risultato());
    }

    /** Due acquisti e due vendite sono un'operazione, la stessa unita' del registro. */
    @Test
    void unGiroCompletoEUnaOperazioneSola() {
        var stats = calcola(
                trade("SOLEUR", "buy", "100", "100.00", "0.00", "1.0", 1),
                trade("SOLEUR", "buy", "110", "110.00", "0.00", "1.0", 2),
                trade("SOLEUR", "sell", "120", "120.00", "0.00", "1.0", 5),
                trade("SOLEUR", "sell", "130", "130.00", "0.00", "1.0", 7));

        assertEquals(1, stats.operazioniChiuse().size());
        var giro = stats.operazioniChiuse().get(0);
        assertEquals(6, giro.giorni());
        assertNumero("210", giro.investito());
        assertNumero("40", giro.risultato());
    }

    @Test
    void iTotaliSonoPerValutaEdEsatti() {
        var stats = calcola(
                trade("SOLEUR", "buy", "100", "100.00", "0.40", "1.0", 1),
                trade("SOLUSD", "buy", "100", "50.00", "0.40", "0.5", 1));

        assertTrue(stats.mixedQuoteCurrencies());
        assertEquals(2, stats.totaliPerValuta().size());
        assertNumero("100.00", totali(stats, "EUR").volume());
        assertNumero("0.40", totali(stats, "EUR").commissioniPercentuale());
        assertNumero("0.80", totali(stats, "USD").commissioniPercentuale());
    }

    /** Una conversione EUR->USD non rende "miste" le valute delle operazioni in EUR. */
    @Test
    void unaConversioneNonRendeMisteLeValuteDelTrading() {
        var stats = calcola(
                trade("ZEURZUSD", "sell", "1.17", "117.00", "0.23", "100.0", 1),
                trade("SOLEUR", "buy", "100", "100.00", "0.40", "1.0", 1));

        assertFalse(stats.mixedQuoteCurrencies());
        assertEquals(List.of("EUR"), stats.quoteCurrencies());
    }

    /** Alle 23:30 UTC a Roma e' gia' il giorno dopo. */
    @Test
    void iGiorniSiContanoNelFusoDiRoma() {
        var stats = calcola(
                trade("O1", "SOLEUR", "buy", "50.00", "0.20", "0.5", Instant.parse("2026-01-10T22:30:00Z"), true, "100"),
                trade("O2", "SOLEUR", "buy", "50.00", "0.20", "0.5", Instant.parse("2026-01-10T23:30:00Z"), true, "100"));

        assertEquals(2, stats.tradingDays(), "in UTC sarebbe un giorno solo");
    }

    @Test
    void ilCampoMakerDiceQuantoVolumeEAndatoATariffaTaker() {
        Instant quando = FROM.plus(1, ChronoUnit.DAYS);
        var stats = calcola(
                trade("O1", "SOLEUR", "buy", "100.00", "0.80", "1.0", quando, false, "100"),
                trade("O2", "SOLEUR", "sell", "100.00", "0.40", "1.0", quando.plusSeconds(60), true, "100"));

        TotaliValuta euro = totali(stats, "EUR");
        assertNumero("100", euro.volumeTaker());
        assertNumero("100", euro.volumeMaker());
        assertNumero("0.80", euro.commissioniTaker());
        assertEquals(Boolean.TRUE, stats.ordini().get(0).taker());
        assertEquals(Boolean.FALSE, stats.ordini().get(1).taker());
    }

    @Test
    void unaVenditaScopertaDiceComeEraArrivataLaMoneta() {
        var stats = calcola(
                List.of(trade("LIGHTERUSD", "sell", "4", "20.00", "0.16", "5", 3)),
                List.of(riga("D1", "deposit", "", "LIGHTER", "5", "0", 1)));

        assertTrue(find(stats, "LIGHTER/USD").provenienze().contains(Provenienza.DEPOSITO));
    }
}
