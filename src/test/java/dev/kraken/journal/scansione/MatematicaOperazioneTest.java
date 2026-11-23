package dev.kraken.journal.scansione;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MatematicaOperazioneTest {

    @Test
    void unaErreEquivaleAllaPerditaAlloStop() {
        assertEquals(-1.0, MatematicaOperazione.risultatoInR(100, 95, 95, TipoOrdine.LIMITE,
                MatematicaOperazione.USCITA_FORZATA), 0.000001);
        assertEquals(-1.0, MatematicaOperazione.risultatoInR(100, 80, 80, TipoOrdine.LIMITE,
                MatematicaOperazione.USCITA_FORZATA), 0.000001,
                "vale -1 qualunque sia l'ampiezza dello stop: e' quello che rende confrontabili coppie diverse");
    }

    /**
     * 100 -> 95: cinque punti di distanza, lo 0,40% sull'entrata a limite e lo
     * 0,80% sull'uscita, perche' uno stop che scatta esce a mercato.
     */
    @Test
    void ilRischioIncludeLeCommissioniSuiDueLatiConLoStopAMercato() {
        assertEquals(5 + 0.40 + 0.76, MatematicaOperazione.rischioPerUnita(100, 95, TipoOrdine.LIMITE), 0.000001);
    }

    @Test
    void lEntrataAMercatoCostaIlDoppioSoloSulPrimoLato() {
        double limite = MatematicaOperazione.rischioPerUnita(100, 95, TipoOrdine.LIMITE) - 5;
        double mercato = MatematicaOperazione.rischioPerUnita(100, 95, TipoOrdine.MERCATO) - 5;
        assertEquals(0.40, mercato - limite, 0.000001);
    }

    /** All'obiettivo si esce con un ordine in attesa: paga la tariffa scelta, non quella dello stop. */
    @Test
    void lUscitaAllObiettivoPagaLaTariffaScelta() {
        double rischio = MatematicaOperazione.rischioPerUnita(100, 95, TipoOrdine.LIMITE);
        double obiettivo = MatematicaOperazione.obiettivoPerMultiplo(100, rischio, 2.0, TipoOrdine.LIMITE);
        assertEquals(2.0, MatematicaOperazione.risultatoInR(100, 95, obiettivo, TipoOrdine.LIMITE, TipoOrdine.LIMITE),
                0.000001);
    }

    @Test
    void uscireAlPrezzoDiPareggioNonLasciaNeUtileNePerdita() {
        double pareggio = MatematicaOperazione.pareggio(100, TipoOrdine.LIMITE);
        assertTrue(pareggio > 100);
        assertEquals(0.0, MatematicaOperazione.risultatoPerUnita(100, pareggio, TipoOrdine.LIMITE), 0.000001);
    }
}
