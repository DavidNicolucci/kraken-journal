package dev.kraken.journal.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class TestoItalianoTest {

    @Test
    void lArticoloSegueLaPronunciaDelNumero() {
        assertEquals("lo 0,72%", TestoItaliano.articolo("0,72%"));
        assertEquals("l'1,44%", TestoItaliano.articolo("1,44%"));
        assertEquals("l'8%", TestoItaliano.articolo("8%"));
        assertEquals("l'11%", TestoItaliano.articolo("11%"));
        assertEquals("l'80%", TestoItaliano.articolo("80%"));
        assertEquals("il 63%", TestoItaliano.articolo("63%"));
        assertEquals("il 18%", TestoItaliano.articolo("18%"));
    }

    @Test
    void lePreposizioniArticolateSiFormanoSullArticolo() {
        assertEquals("allo 0,72%", TestoItaliano.a("0,72%"));
        assertEquals("all'1,44%", TestoItaliano.a("1,44%"));
        assertEquals("al 63%", TestoItaliano.a("63%"));
        assertEquals("dello 0,72%", TestoItaliano.di("0,72%"));
        assertEquals("dell'1,61%", TestoItaliano.di("1,61%"));
        assertEquals("del 63%", TestoItaliano.di("63%"));
    }

    @Test
    void numeriEImportiAllItaliana() {
        assertEquals("1.234,50", TestoItaliano.numero(new BigDecimal("1234.5"), 2));
        assertEquals("+1,95 EUR", TestoItaliano.conSegno(new BigDecimal("1.949"), "EUR"));
        assertEquals("−0,80 USD", TestoItaliano.conSegno(new BigDecimal("-0.8"), "USD"));
        assertEquals("0,40%", TestoItaliano.percentuale(new BigDecimal("0.4")));
    }

    @Test
    void elenchiEPlurali() {
        assertEquals("a, b e c", TestoItaliano.unisci(List.of("a", "b", "c")));
        assertEquals("a, b e altre 2", TestoItaliano.elenco(List.of("a", "b", "c", "d"), 2, "altre"));
        assertEquals("1 ordine", TestoItaliano.plurale(1, "ordine", "ordini"));
        assertEquals("3 ordini", TestoItaliano.plurale(3, "ordine", "ordini"));
        assertEquals("in giornata", TestoItaliano.durata(0));
    }
}
