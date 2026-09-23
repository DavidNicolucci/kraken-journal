package dev.kraken.journal.scansione;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.mercato.dto.Candela;

/**
 * I valori attesi non sono inventati: sono le chiusure giornaliere reali di
 * HYPE/EUR fino al 17/09/2026, gia' verificate due volte con implementazioni
 * indipendenti prima di finire qui. Se un giorno questi test si rompono, o e'
 * cambiata la formula o e' entrato un bug: non e' il mercato che si e' mosso.
 */
class CalcolatoreIndicatoriTest {

    private static final double[] CHIUSURE_HYPE = {
            47.71, 49.28, 48.75, 46.85, 47.69, 46.55, 47.73, 47.20, 48.56, 49.73,
            48.78, 49.19, 49.38, 51.32, 50.64, 59.61, 63.07, 64.60, 67.80, 70.35,
            67.57, 68.26, 70.74, 72.68, 69.77, 72.00, 69.18, 72.48, 71.39, 70.73,
            75.25, 72.73, 73.60, 75.69, 73.26, 73.25, 71.86, 67.66, 68.54, 68.82,
            66.67, 69.49, 66.68, 68.22, 74.18};

    @Test
    void laMediaMobileUsaLeUltimeVentiChiusure() {
        assertEquals(71.0840, CalcolatoreIndicatori.mediaMobile(candele(CHIUSURE_HYPE), 20), 0.0005);
    }

    @Test
    void lRsiDiWilderCoincideConQuelloDelGrafico() {
        assertEquals(60.7, CalcolatoreIndicatori.rsi(candele(CHIUSURE_HYPE), 14), 0.2);
    }

    @Test
    void lRsiVaACentoQuandoNonCiSonoDiscese() {
        assertEquals(100.0, CalcolatoreIndicatori.rsi(candele(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15), 14), 0.0001);
    }

    @Test
    void lAtrContaIlSaltoDallaChiusuraPrecedente() {
        List<Candela> piatte = new ArrayList<>(candeleConEscursione(16, 100, 2));
        // Una candela che apre dieci punti sopra: massimo-minimo resta 2, ma il
        // vero movimento rispetto a ieri e' molto piu' grande.
        piatte.set(15, new Candela(Instant.EPOCH, 110, 111, 109, 110, 110, 1));

        double atr = CalcolatoreIndicatori.atr(piatte, 14);
        assertTrue(atr > 2, "l'ATR deve superare la semplice escursione, invece vale " + atr);
    }

    @Test
    void laMediaInSalitaSiLeggeDalConfrontoConLaCandelaPrecedente() {
        var indicatori = CalcolatoreIndicatori.calcola(candeleCrescenti(80), 5);
        assertTrue(indicatori.mediaMobileInSalita());
        assertTrue(indicatori.distanzaDallaMedia() > 0);
    }

    @Test
    void rifiutaUnaSerieTroppoCorta() {
        var errore = assertThrows(IllegalArgumentException.class,
                () -> CalcolatoreIndicatori.calcola(candele(1, 2, 3), 5));
        assertTrue(errore.getMessage().contains("candele chiuse"));
    }

    private static List<Candela> candele(double... chiusure) {
        List<Candela> lista = new ArrayList<>();
        for (int i = 0; i < chiusure.length; i++) {
            double c = chiusure[i];
            lista.add(new Candela(Instant.EPOCH.plusSeconds(i * 86400L), c, c, c, c, c, 1));
        }
        return lista;
    }

    private static List<Candela> candeleConEscursione(int quante, double prezzo, double escursione) {
        List<Candela> lista = new ArrayList<>();
        for (int i = 0; i < quante; i++) {
            lista.add(new Candela(Instant.EPOCH.plusSeconds(i * 86400L), prezzo,
                    prezzo + escursione / 2, prezzo - escursione / 2, prezzo, prezzo, 1));
        }
        return lista;
    }

    private static List<Candela> candeleCrescenti(int quante) {
        double[] chiusure = new double[quante];
        for (int i = 0; i < quante; i++) {
            chiusure[i] = 100 + i;
        }
        return candele(chiusure);
    }
}
