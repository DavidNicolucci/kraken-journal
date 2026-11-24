package dev.kraken.journal.kraken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PairResolverTest {

    private final PairResolver resolver = new PairResolver();

    @Test
    void riconosceLeCoppieConPrefissoStorico() {
        var p = resolver.resolve("XETHZEUR");
        assertEquals("ETH", p.base());
        assertEquals("EUR", p.quote());
        assertFalse(p.fxConversion());
    }

    @Test
    void riconosceLeCoppieModerne() {
        var p = resolver.resolve("SOLUSD");
        assertEquals("SOL", p.base());
        assertEquals("USD", p.quote());

        var l = resolver.resolve("LIGHTERUSD");
        assertEquals("LIGHTER", l.base());
        assertEquals("USD", l.quote());
    }

    @Test
    void riconosceIlCambioValuta() {
        var p = resolver.resolve("ZEURZUSD");
        assertEquals("EUR", p.base());
        assertEquals("USD", p.quote());
        assertTrue(p.fxConversion(), "EUR/USD e' una conversione, non un trade");
    }

    @Test
    void bitcoinNonEUnaConversioneValutaria() {
        assertFalse(resolver.resolve("XXBTZUSD").fxConversion());
    }

    /** Prima ogni codice di quattro lettere che iniziava per X o Z perdeva la prima lettera. */
    @Test
    void nonTagliaLeMoneteCheInizianoPerXOZ() {
        assertEquals("ZETA", resolver.resolve("ZETAEUR").base());
        assertEquals("XTZ", resolver.resolve("XTZEUR").base());
        assertEquals("EUR", resolver.resolve("XTZEUR").quote());
    }

    /** Il ledger riporta i due codici separati, a volte con un suffisso di staking. */
    @Test
    void componeLaCoppiaDaiCodiciDelLedger() {
        var p = resolver.coppia("XXBT", "ZEUR");
        assertEquals("XBT/EUR", p.display());
        assertFalse(p.fxConversion());
        assertTrue(resolver.coppia("ZEUR", "ZUSD").fxConversion());
        assertEquals("SOL", resolver.asset("SOL.F"));
    }

    @Test
    void ilNomePerIlRegistroEQuelloDellaWatchlist() {
        assertEquals("XBTEUR", resolver.altname("XBT/EUR"));
        assertEquals("XBTEUR", resolver.altname("btc/eur"));
        assertEquals("XBTEUR", resolver.altname("XXBTZEUR"));
        assertEquals("XDGEUR", resolver.altname("DOGE/EUR"));
        assertEquals("ZECEUR", resolver.altname("XZECZEUR"));
        assertEquals("XTZEUR", resolver.altname(" XTZEUR "));
        assertNull(resolver.altname("XBTEUR&interval=60"));
        assertNull(resolver.altname("PIPPO"));
        assertNull(resolver.altname(""));
    }
}
