package dev.kraken.journal.kraken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
