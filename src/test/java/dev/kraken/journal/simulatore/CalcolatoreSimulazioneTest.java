package dev.kraken.journal.simulatore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntToDoubleFunction;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.registro.Statistica;
import dev.kraken.journal.simulatore.CalcolatoreSimulazione.Cella;
import dev.kraken.journal.simulatore.CalcolatoreSimulazione.Osservazione;
import dev.kraken.journal.simulatore.dto.Previsione;
import dev.kraken.journal.simulatore.dto.Verifica;

class CalcolatoreSimulazioneTest {

    private static final Instant INIZIO = Instant.parse("2025-01-01T00:00:00Z");
    private static final int ORIZZONTE = 7;
    private static final Cella SALE = new Cella(Regime.BULL, FasciaRsi.ALTA);
    private static final Cella SCENDE = new Cella(Regime.BEAR, FasciaRsi.BASSA);

    @Test
    void ilRegimeVuoleTutteETreLeCondizioni() {
        assertEquals(Regime.BULL, Regime.di(110, 100, true, 5));
        assertEquals(Regime.BEAR, Regime.di(90, 100, false, -5));
        assertEquals(Regime.LATERALE, Regime.di(110, 100, false, 5), "sopra la media ma con la media che scende");
        assertEquals(Regime.LATERALE, Regime.di(90, 100, false, 2), "sotto la media ma sopra di 60 giorni fa");
    }

    @Test
    void leFasceDiRsiTengonoLeSoglieNelCentro() {
        assertEquals(FasciaRsi.BASSA, FasciaRsi.di(39.9));
        assertEquals(FasciaRsi.CENTRALE, FasciaRsi.di(40));
        assertEquals(FasciaRsi.CENTRALE, FasciaRsi.di(60));
        assertEquals(FasciaRsi.ALTA, FasciaRsi.di(60.1));
    }

    @Test
    void laDirezioneLaDecideLIntervalloNonLaStima() {
        assertEquals(Direzione.INCERTO, Direzione.di(0.41, 0.73), "58% ma con il 50% dentro l'intervallo");
        assertEquals(Direzione.RIALZO, Direzione.di(0.52, 0.70));
        assertEquals(Direzione.RIBASSO, Direzione.di(0.30, 0.48));
    }

    @Test
    void wilsonResta_dentroLaScalaESiAllargaConPochiCasi() {
        double[] meta = Statistica.wilson95(0.5, 10);
        assertEquals(0.2366, meta[0], 0.0005);
        assertEquals(0.7634, meta[1], 0.0005);

        double[] tutti = Statistica.wilson95(1.0, 10);
        assertEquals(0.7225, tutti[0], 0.0005, "dieci su dieci non vuol dire certezza");
        assertEquals(1.0, tutti[1], 0.000001);

        double[] nessuno = Statistica.wilson95(0.6, 0);
        assertEquals(0, nessuno[0]);
        assertEquals(1, nessuno[1]);
    }

    @Test
    void iGiorniDistanziatiNonCondividonoIlMovimento() {
        List<Instant> giorni = new ArrayList<>();
        for (int g = 0; g <= 20; g++) {
            giorni.add(giorno(g));
        }
        assertEquals(List.of(giorno(0), giorno(7), giorno(14)), CalcolatoreSimulazione.distanziati(giorni, ORIZZONTE));
    }

    @Test
    void ilPercentileInterpolaFraIValori() {
        double[] valori = {1, 2, 3, 4, 5};
        assertEquals(3, CalcolatoreSimulazione.percentile(valori, 0.5), 1e-9);
        assertEquals(1.2, CalcolatoreSimulazione.percentile(valori, 0.05), 1e-9);
        assertEquals(5, CalcolatoreSimulazione.percentile(valori, 1), 1e-9);
    }

    @Test
    void unaSerieInSalitaEBullEGliUltimiGiorniNonHannoAncoraEsito() {
        List<Candela> candele = candele(200, g -> 100 * Math.pow(1.01, g));

        List<Osservazione> osservazioni = CalcolatoreSimulazione.osservazioni("XBTEUR", candele, ORIZZONTE);

        assertEquals(200 - CalcolatoreSimulazione.RISCALDAMENTO, osservazioni.size());
        Osservazione ultima = osservazioni.get(osservazioni.size() - 1);
        assertEquals(Regime.BULL, ultima.cella().regime());
        assertFalse(ultima.conEsito());
        assertNull(ultima.nota());
        assertEquals(ORIZZONTE, osservazioni.stream().filter(o -> !o.conEsito()).count());
        Osservazione prima = osservazioni.get(0);
        assertEquals(giorno(CalcolatoreSimulazione.RISCALDAMENTO + ORIZZONTE), prima.nota());
        assertTrue(prima.salita());
    }

    /** Lo stato di un giorno non puo' dipendere da cosa succede dopo: e' la regola che rende onesta la verifica. */
    @Test
    void loStatoDiUnGiornoNonGuardaIlFuturo() {
        List<Candela> salita = candele(150, g -> 100 * Math.pow(1.01, g));
        List<Candela> crollo = new ArrayList<>(salita.subList(0, 101));
        crollo.addAll(candele(150, g -> 10).subList(101, 150));

        Osservazione conSalita = CalcolatoreSimulazione.osservazioni("X", salita, ORIZZONTE).get(100 - CalcolatoreSimulazione.RISCALDAMENTO);
        Osservazione conCrollo = CalcolatoreSimulazione.osservazioni("X", crollo, ORIZZONTE).get(100 - CalcolatoreSimulazione.RISCALDAMENTO);

        assertEquals(conSalita.cella(), conCrollo.cella());
        assertEquals(conSalita.rsi(), conCrollo.rsi(), 1e-12);
        assertNotEquals(conSalita.salita(), conCrollo.salita(), "cambia solo l'esito");
    }

    /**
     * Un giorno entra nei conteggi solo quando il suo esito e' arrivato: con
     * orizzonte 7 il giorno d conosce gli esiti fino a d-7, e con 30 casi
     * minimi la prima stima arriva il giorno 36.
     */
    @Test
    void laVerificaUsaSoloGliEsitiGiaNoti() {
        List<Osservazione> osservazioni = new ArrayList<>();
        for (int g = 0; g < 100; g++) {
            osservazioni.add(osservazione("X", g, SALE, true));
        }

        Verifica verifica = CalcolatoreSimulazione.verifica(osservazioni, giorno(0), ORIZZONTE, 30);

        assertEquals(100 - 36, verifica.casi());
        assertEquals(1.0, verifica.frequenzaRialzo(), 1e-12);
    }

    @Test
    void unoStatoCheDecideLEsitoBatteLaBase() {
        List<Osservazione> osservazioni = new ArrayList<>();
        for (int g = 0; g < 400; g++) {
            boolean sale = (g / 10) % 2 == 0;
            osservazioni.add(osservazione("X", g, sale ? SALE : SCENDE, sale));
        }

        Verifica verifica = CalcolatoreSimulazione.verifica(osservazioni, giorno(100), ORIZZONTE, 30);

        assertEquals(Verdetto.VANTAGGIO, verifica.verdetto());
        assertTrue(verifica.brierModello() < verifica.brierBase());
        assertEquals(1.0, verifica.precisioneModello(), 1e-12);
    }

    /** Il caso normale dei mercati: uno stato che non c'entra con l'esito non deve sembrare un vantaggio. */
    @Test
    void unoStatoCasualeNonMostraVantaggio() {
        Random caso = new Random(42);
        List<Osservazione> osservazioni = new ArrayList<>();
        for (int g = 0; g < 700; g++) {
            for (String coppia : List.of("A", "B", "C")) {
                osservazioni.add(osservazione(coppia, g, caso.nextBoolean() ? SALE : SCENDE, caso.nextDouble() < 0.52));
            }
        }

        Verifica verifica = CalcolatoreSimulazione.verifica(osservazioni, giorno(335), ORIZZONTE, 30);

        assertNotEquals(Verdetto.VANTAGGIO, verifica.verdetto());
        assertEquals(365 / ORIZZONTE + 1, verifica.giorni(), "un giorno ogni orizzonte, non uno per coppia");
    }

    @Test
    void troppoPochiGiorniNonDannoUnVerdetto() {
        List<Osservazione> osservazioni = new ArrayList<>();
        for (int g = 0; g < 40; g++) {
            osservazioni.add(osservazione("X", g, SALE, g % 2 == 0));
        }
        assertEquals(Verdetto.DATI_INSUFFICIENTI,
                CalcolatoreSimulazione.verifica(osservazioni, giorno(0), ORIZZONTE, 30).verdetto());
    }

    @Test
    void laPrevisioneContaGliStatiSimiliEDichiaraICasiIndipendenti() {
        List<Osservazione> osservazioni = new ArrayList<>();
        for (int g = 0; g < 70; g++) {
            osservazioni.add(osservazione("A", g, SALE, g % 10 != 0));     // sale 9 volte su 10
            osservazioni.add(osservazione("B", g, SCENDE, false));
        }
        Osservazione oggi = osservazione("A", 70, SALE, null);

        Previsione previsione = CalcolatoreSimulazione.previsione(oggi, osservazioni, giorno(0), ORIZZONTE, 30);

        assertFalse(previsione.dallaBase());
        assertEquals(70, previsione.casi());
        assertEquals(0.9, previsione.probabilitaRialzo(), 1e-12);
        assertEquals(10, previsione.indipendenti(), "70 giorni consecutivi sono 10 settimane, non 70 prove");
        assertTrue(previsione.minimo() < 0.9 && previsione.massimo() > 0.9);
        assertEquals(Direzione.RIALZO, previsione.direzione());
    }

    @Test
    void unoStatoRaroUsaLaFrequenzaDiTuttiIGiorni() {
        List<Osservazione> osservazioni = new ArrayList<>();
        for (int g = 0; g < 70; g++) {
            osservazioni.add(osservazione("A", g, SCENDE, g % 2 == 0));
        }
        Osservazione oggi = osservazione("A", 70, SALE, null);

        Previsione previsione = CalcolatoreSimulazione.previsione(oggi, osservazioni, giorno(0), ORIZZONTE, 30);

        assertTrue(previsione.dallaBase());
        assertEquals(0.5, previsione.probabilitaRialzo(), 1e-12);
        assertEquals(Direzione.INCERTO, previsione.direzione());
    }

    /** L'intervallo di prezzo usa i movimenti della coppia, non quelli delle altre. */
    @Test
    void lIntervalloDiPrezzoEDellaSolaCoppia() {
        List<Osservazione> osservazioni = new ArrayList<>();
        for (int g = 0; g < 101; g++) {
            double movimento = 0.9 + 0.2 * g / 100;                     // da 0,9 a 1,1
            osservazioni.add(new Osservazione("A", giorno(g), SALE, 50, 0, 100, 100 * movimento, giorno(g + ORIZZONTE)));
            osservazioni.add(new Osservazione("B", giorno(g), SALE, 50, 0, 100, 300.0, giorno(g + ORIZZONTE)));
        }
        Osservazione oggi = new Osservazione("A", giorno(110), SALE, 50, 0, 200, null, null);

        Previsione previsione = CalcolatoreSimulazione.previsione(oggi, osservazioni, giorno(0), ORIZZONTE, 30);

        assertEquals(200 * 0.91, previsione.prezzoBasso(), 1e-9);
        assertEquals(200, previsione.prezzoMediano(), 1e-9);
        assertEquals(200 * 1.09, previsione.prezzoAlto(), 1e-9);
    }

    private static Osservazione osservazione(String coppia, int g, Cella cella, Boolean sale) {
        return new Osservazione(coppia, giorno(g), cella, 50, 0, 100,
                sale == null ? null : sale ? 110.0 : 90.0,
                sale == null ? null : giorno(g + ORIZZONTE));
    }

    private static Instant giorno(int g) {
        return INIZIO.plus(g, ChronoUnit.DAYS);
    }

    private static List<Candela> candele(int quante, IntToDoubleFunction chiusura) {
        List<Candela> candele = new ArrayList<>();
        for (int g = 0; g < quante; g++) {
            double c = chiusura.applyAsDouble(g);
            candele.add(new Candela(giorno(g), c, c * 1.01, c * 0.99, c, c, 1000));
        }
        return candele;
    }
}
