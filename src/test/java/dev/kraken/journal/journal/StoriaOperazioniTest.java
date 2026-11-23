package dev.kraken.journal.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.kraken.dto.KrakenLedgerEntry;

class StoriaOperazioniTest {

    private final StoriaOperazioni storia = new StoriaOperazioni(new PairResolver());

    private static KrakenLedgerEntry riga(String refid, String type, String subtype, String asset,
                                          String amount, String fee) {
        return new KrakenLedgerEntry(refid, new BigDecimal("1790000000.5"), type, subtype, asset,
                new BigDecimal(amount), new BigDecimal(fee));
    }

    private static void assertNumero(String atteso, BigDecimal effettivo) {
        assertEquals(0, new BigDecimal(atteso).compareTo(effettivo), "atteso " + atteso + ", ricevuto " + effettivo);
    }

    private Movimento unico(List<KrakenLedgerEntry> ledger) {
        List<Movimento> movimenti = storia.componi(List.of(), ledger).movimenti();
        assertEquals(1, movimenti.size());
        return movimenti.get(0);
    }

    @Test
    void unAcquistoDallAppDiventaUnAcquisto() {
        Movimento m = unico(List.of(
                riga("T1", "spend", "", "ZEUR", "-20.00", "0.30"),
                riga("T1", "receive", "", "SOL", "0.2", "0")));

        assertTrue(m.acquisto());
        assertEquals(Origine.APP, m.origine());
        assertEquals("SOL/EUR", m.coppia().display());
        assertNumero("0.2", m.quantita());
        assertNumero("20.00", m.controvalore());
        assertNumero("0.30", m.commissione());
    }

    @Test
    void unaVenditaDallAppDiventaUnaVendita() {
        Movimento m = unico(List.of(
                riga("T2", "spend", "", "XXBT", "-0.001", "0"),
                riga("T2", "receive", "", "ZEUR", "80.00", "1.20")));

        assertFalse(m.acquisto());
        assertEquals("XBT/EUR", m.coppia().display());
        assertNumero("0.001", m.quantita());
        assertNumero("80.00", m.controvalore());
        assertNumero("1.20", m.commissione());
    }

    /**
     * Se la commissione e' trattenuta nella moneta, la quantita' arrivata e'
     * piu' bassa e la commissione va contata lo stesso: il costo totale resta
     * quello uscito dal conto.
     */
    @Test
    void unaCommissioneNellaMonetaSiConverteInValuta() {
        Movimento m = unico(List.of(
                riga("T3", "spend", "", "ZEUR", "-20.00", "0"),
                riga("T3", "receive", "", "SOL", "0.2", "0.002")));

        assertNumero("0.198", m.quantita());
        assertNumero("0.2", m.commissione());
        assertNumero("20.00", m.controvalore().add(m.commissione()));
    }

    @Test
    void uneuroCambiatoInDollariDallAppEUnaConversione() {
        Movimento m = unico(List.of(
                riga("T4", "spend", "", "ZEUR", "-100.00", "0"),
                riga("T4", "receive", "", "ZUSD", "117.00", "0.20")));

        assertTrue(m.coppia().fxConversion());
    }

    @Test
    void depositiERicompenseSonoArriviMaLoStakingNo() {
        var risultato = storia.componi(List.of(), List.of(
                riga("D1", "deposit", "", "LIGHTER", "5", "0"),
                riga("R1", "earn", "reward", "ETH.F", "0.0001", "0"),
                riga("S1", "transfer", "spottostaking", "SOL.S", "1", "0")));

        assertEquals(Set.of(Provenienza.DEPOSITO), risultato.arrivi().get("LIGHTER"));
        assertEquals(Set.of(Provenienza.RICOMPENSA), risultato.arrivi().get("ETH"));
        assertFalse(risultato.arrivi().containsKey("SOL"), "lo spostamento verso lo staking non e' un arrivo");
    }

    @Test
    void senzaLedgerLaStoriaLoDichiara() {
        assertFalse(storia.componi(List.of(), null).ledgerLetto());
        assertTrue(storia.componi(List.of(), List.of()).ledgerLetto());
    }
}
