package dev.kraken.journal.registro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.scansione.TipoOrdine;

class ServizioRegistroTest {

    private static OperazioneRegistrata chiusa(Trigger trigger, boolean reale, double risultatoInR) {
        var operazione = new OperazioneRegistrata("XBTEUR", trigger, 100, 90, 120, 1, 2, "prova", reale, Instant.EPOCH);
        operazione.chiudi(risultatoInR > 0 ? StatoOperazione.CHIUSA_ALL_OBIETTIVO : StatoOperazione.CHIUSA_ALLO_STOP,
                Instant.EPOCH.plusSeconds(86400), 100, risultatoInR);
        return operazione;
    }

    /** Una media unica mescolava carta e soldi veri, un trigger e l'altro. */
    @Test
    void iRisultatiSonoSeparatiPerRegolaEPerTipo() {
        RegistroRepository repository = mock(RegistroRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                chiusa(Trigger.BREAKOUT_20_GIORNI, false, 2),
                chiusa(Trigger.BREAKOUT_20_GIORNI, false, -1),
                chiusa(Trigger.MANUALE, true, -1)));
        var proprieta = new ProprietaScansione(List.of("XBTEUR"), 1_000_000, 50, 70, 12, 5, 10, 2.0,
                203, 1, 5, 0.5, 15, 15, 2, TipoOrdine.LIMITE, 20, 1.5);

        var statistiche = new ServizioRegistro(repository, mock(MercatoClient.class), proprieta).statistiche();

        assertEquals(2, statistiche.gruppi().size());
        var reale = statistiche.gruppi().get(0);
        assertTrue(reale.reale(), "le operazioni vere vengono prima");
        assertEquals(-1.0, reale.risultatoMedioInR(), 0.000001);
        assertNull(reale.margineInR(), "con una sola operazione la dispersione non esiste");
        var carta = statistiche.gruppi().get(1);
        assertEquals(0.5, carta.risultatoMedioInR(), 0.000001);
        assertEquals(13, carta.registrazioniMancanti());
    }

    @Test
    void ilQuantileDiStudentCoincideConLeTavole() {
        assertEquals(12.706, Statistica.quantileT(1), 0.001);
        assertEquals(4.303, Statistica.quantileT(2), 0.001);
        assertEquals(2.145, Statistica.quantileT(14), 0.001);
        assertEquals(2.042, Statistica.quantileT(30), 0.001);
        assertEquals(2.040, Statistica.quantileT(31), 0.001);
    }

    /**
     * Quindici operazioni, un terzo all'obiettivo a +2 R e il resto allo stop:
     * la media e' zero, ma potrebbe essere ovunque fra -0,81 e +0,81 R. E' il
     * motivo per cui quindici operazioni non bastano a dire che una regola funziona.
     */
    @Test
    void ilMargineDiceQuantoPuoSbagliareLaMedia() {
        double[] risultati = {2, 2, 2, 2, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
        assertEquals(0.0, Statistica.media(risultati), 0.000001);
        assertEquals(0.81, Statistica.margine95(risultati), 0.005);
    }
}
