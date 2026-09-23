package dev.kraken.journal.scansione;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MatematicaOperazioneTest {

    @Test
    void unaErreEquivaleAllaPerditaAlloStop() {
        assertEquals(-1.0, MatematicaOperazione.risultatoInR(100, 95, 95, TipoOrdine.LIMITE), 0.000001);
        assertEquals(-1.0, MatematicaOperazione.risultatoInR(100, 80, 80, TipoOrdine.LIMITE), 0.000001,
                "vale -1 qualunque sia l'ampiezza dello stop: e' quello che rende confrontabili coppie diverse");
    }

    @Test
    void ilRischioIncludeLeCommissioniSuiDueLati() {
        // 100 -> 95: cinque punti di distanza piu' lo 0,40% su 100 e su 95.
        assertEquals(5 + 0.40 + 0.38, MatematicaOperazione.rischioPerUnita(100, 95, TipoOrdine.LIMITE), 0.000001);
    }

    @Test
    void lOrdineAMercatoCostaIlDoppio() {
        double limite = MatematicaOperazione.rischioPerUnita(100, 95, TipoOrdine.LIMITE) - 5;
        double mercato = MatematicaOperazione.rischioPerUnita(100, 95, TipoOrdine.MERCATO) - 5;
        assertEquals(2.0, mercato / limite, 0.000001);
    }

    @Test
    void uscireAlPrezzoDiPareggioNonLasciaNeUtileNePerdita() {
        double pareggio = MatematicaOperazione.pareggio(100, TipoOrdine.LIMITE);
        assertTrue(pareggio > 100);
        assertEquals(0.0, MatematicaOperazione.risultatoPerUnita(100, pareggio, TipoOrdine.LIMITE), 0.000001);
    }
}
