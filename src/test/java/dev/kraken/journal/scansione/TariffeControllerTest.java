package dev.kraken.journal.scansione;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class TariffeControllerTest {

    /** Le pagine leggono le tariffe da qui: in percentuale, come le mostra Kraken. */
    @Test
    void leTariffeArrivanoInPercentualeDaTipoOrdine() {
        var tariffe = new TariffeController().tariffe();
        assertEquals(0, new BigDecimal("0.4").compareTo(tariffe.get(TipoOrdine.LIMITE)));
        assertEquals(0, new BigDecimal("0.8").compareTo(tariffe.get(TipoOrdine.MERCATO)));
    }
}
