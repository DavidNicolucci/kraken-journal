package dev.kraken.journal.registro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.registro.dto.NuovaOperazione;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.scansione.TipoOrdine;

class ServizioRegistroTest {

    private static OperazioneRegistrata chiusa(Trigger trigger, boolean reale, double risultatoInR) {
        var operazione = new OperazioneRegistrata("XBTEUR", trigger, 100, 90, 120, 1, 2, "prova", reale, Instant.EPOCH, TipoOrdine.LIMITE);
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

        var statistiche = new ServizioRegistro(repository, mock(MercatoClient.class), proprieta, new PairResolver()).statistiche();

        assertEquals(2, statistiche.gruppi().size());
        var reale = statistiche.gruppi().get(0);
        assertTrue(reale.reale(), "le operazioni vere vengono prima");
        assertEquals(-1.0, reale.risultatoMedioInR(), 0.000001);
        assertNull(reale.margineInR(), "con una sola operazione la dispersione non esiste");
        var carta = statistiche.gruppi().get(1);
        assertEquals(0.5, carta.risultatoMedioInR(), 0.000001);
        assertEquals(13, carta.registrazioniMancanti());
    }

    private final RegistroRepository repository = mock(RegistroRepository.class);
    private final ServizioRegistro servizio = new ServizioRegistro(repository, mock(MercatoClient.class),
            new ProprietaScansione(List.of("XBTEUR"), 1_000_000, 50, 70, 12, 5, 10, 2.0,
                    203, 1, 5, 0.5, 15, 15, 2, TipoOrdine.LIMITE, 20, 1.5), new PairResolver());

    private OperazioneRegistrata registra(String coppia, double quantita, boolean reale) {
        when(repository.save(any())).thenAnswer(chiamata -> chiamata.getArgument(0));
        return servizio.registra(new NuovaOperazione(coppia, Trigger.MANUALE, 72, 68, 79.98, quantita, "prova", reale));
    }

    /** Come la scrive l'interfaccia di Kraken o la watchlist, la coppia finisce nel registro col nome della watchlist. */
    @Test
    void laCoppiaSiScriveComeNellaWatchlist() {
        assertEquals("XBTEUR", registra("BTC/EUR", 0.001, false).coppia());
        assertEquals("XBTEUR", registra("XXBTZEUR", 0.001, false).coppia());
        var errore = assertThrows(IllegalArgumentException.class, () -> registra("XBTEUR&interval=60", 0.001, false));
        assertTrue(errore.getMessage().contains("non riconosciuta"));
    }

    /** Il rapporto non arriva dal chiamante: e' l'R all'obiettivo, con la tariffa registrata accanto. */
    @Test
    void rapportoETariffaLiScriveIlRegistro() {
        var operazione = registra("HYPEEUR", 0.42, false);
        assertEquals(7.37208 / 4.832, operazione.rapporto(), 0.00001);
        assertEquals(TipoOrdine.LIMITE, operazione.tipoOrdine());
    }

    @Test
    void unOperazioneRealeHaUnaQuantita() {
        var errore = assertThrows(IllegalArgumentException.class, () -> registra("HYPEEUR", 0, true));
        assertTrue(errore.getMessage().contains("quantita'"));
    }

    /** Con due posizioni reali aperte la terza non si registra: la carta si'. */
    @Test
    void oltreIlTettoLOperazioneRealeVieneRifiutata() {
        when(repository.countByStatoAndReale(StatoOperazione.APERTA, true)).thenReturn(2L);

        var errore = assertThrows(IllegalArgumentException.class, () -> registra("HYPEEUR", 0.42, true));
        assertTrue(errore.getMessage().contains("regola 2.3"));
        assertEquals("HYPEEUR", registra("HYPEEUR", 0.42, false).coppia());
    }

    /** Le operazioni reali non si simulano sulle candele: la risoluzione legge solo la carta. */
    @Test
    void laRisoluzioneSulleCandeleRiguardaSoloLaCarta() {
        servizio.risolviAperte();
        verify(repository).findByStatoAndReale(StatoOperazione.APERTA, false);
        verify(repository, never()).findByStatoAndReale(StatoOperazione.APERTA, true);
    }

    /** Uscita a 70: sotto l'obiettivo e sopra lo stop, quindi a mano; R con la commissione a mercato. */
    @Test
    void laChiusuraAManoPrendeLoStatoDalPrezzo() {
        var operazione = new OperazioneRegistrata("HYPEEUR", Trigger.MANUALE, 72, 68, 79.98, 0.42, 1.5, "prova",
                true, Instant.EPOCH, TipoOrdine.LIMITE);
        when(repository.findById(7L)).thenReturn(Optional.of(operazione));

        servizio.chiudiAMano(7L, 70);

        assertEquals(StatoOperazione.CHIUSA_A_MANO, operazione.stato());
        assertEquals((70 - 72 - 72 * 0.004 - 70 * 0.008) / 4.832, operazione.risultatoInR(), 0.000001);
        assertEquals(StatoOperazione.CHIUSA_ALLO_STOP, StatoOperazione.dallUscita(67.5, 68, 79.98));
        assertEquals(StatoOperazione.CHIUSA_ALL_OBIETTIVO, StatoOperazione.dallUscita(79.98, 68, 79.98));
    }

    /** Un'operazione mai eseguita non e' ne' aperta ne' chiusa: non occupa un posto e non entra nei risultati. */
    @Test
    void laNonEseguitaNonContaDaNessunaParte() {
        var mai = new OperazioneRegistrata("HYPEEUR", Trigger.MANUALE, 72, 68, 79.98, 0.42, 1.5, "prova",
                true, Instant.EPOCH, TipoOrdine.LIMITE);
        mai.nonEseguita(Instant.EPOCH.plusSeconds(4 * 86400));
        when(repository.findAll()).thenReturn(List.of(mai, chiusa(Trigger.MANUALE, true, 2)));

        var statistiche = servizio.statistiche();

        assertEquals(0, statistiche.aperte());
        assertEquals(1, statistiche.chiuse());
        assertEquals(0, statistiche.gruppi().get(0).aperte());
        assertThrows(IllegalStateException.class, () -> mai.chiudi(StatoOperazione.CHIUSA_ALLO_STOP, Instant.EPOCH, 1, -1));
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
