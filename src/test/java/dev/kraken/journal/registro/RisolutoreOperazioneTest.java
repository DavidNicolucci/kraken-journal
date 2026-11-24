package dev.kraken.journal.registro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.scansione.TipoOrdine;

class RisolutoreOperazioneTest {

    private static final Instant REGISTRAZIONE = Instant.parse("2026-09-23T00:00:00Z");
    /** Il lavoro delle sette, in UTC: la candela del giorno e' iniziata cinque ore prima. */
    private static final Instant ALLE_SETTE = REGISTRAZIONE.plus(5, ChronoUnit.HOURS);
    private static final int TIME_STOP = 15;

    @Test
    void chiudeAlloStopQuandoIlMinimoLoTocca() {
        var chiusura = RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(1, 102, 98), candela(2, 101, 89)), List.of(), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_ALLO_STOP, chiusura.stato());
        assertEquals(90, chiusura.prezzoUscita(), 0.0001);
    }

    @Test
    void alloStopIlRisultatoEsattamenteMenoUnaR() {
        var chiusura = RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(1, 101, 89)), List.of(), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(-1.0, chiusura.risultatoInR(), 0.000001,
                "una R e' la perdita allo stop, commissioni comprese: per definizione vale -1");
    }

    @Test
    void chiudeAllObiettivoQuandoIlMassimoLoRaggiunge() {
        var chiusura = RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(1, 105, 99), candela(2, 121, 104)), List.of(), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_ALL_OBIETTIVO, chiusura.stato());
        assertEquals(120, chiusura.prezzoUscita(), 0.0001);
    }

    @Test
    void seNellaStessaCandelaCiSonoEntrambiVinceLoStop() {
        var chiusura = RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(1, 125, 85)), List.of(), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_ALLO_STOP, chiusura.stato(),
                "la candela giornaliera non dice quale sia arrivato prima: si assume il peggio");
    }

    @Test
    void chiudePerTempoDopoIGiorniPrevisti() {
        List<Candela> quindiciGiorniPiatti = new ArrayList<>();
        for (int giorno = 1; giorno <= 20; giorno++) {
            quindiciGiorniPiatti.add(candela(giorno, 105, 99));
        }
        var chiusura = RisolutoreOperazione.risolvi(operazione(), quindiciGiorniPiatti, List.of(), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_PER_TEMPO, chiusura.stato());
        assertEquals(REGISTRAZIONE.plus(16, ChronoUnit.DAYS), chiusura.quando(),
                "si esce alla chiusura del quindicesimo giorno pieno, cioe' alla fine della sua candela");
    }

    /** Il time stop chiude alla chiusura della candela, con un ordine a mercato. */
    @Test
    void ilTimeStopEsceAMercato() {
        List<Candela> giorniPiatti = new ArrayList<>();
        for (int giorno = 1; giorno <= 20; giorno++) {
            giorniPiatti.add(candela(giorno, 105, 99));
        }
        var chiusura = RisolutoreOperazione.risolvi(operazione(), giorniPiatti, List.of(), TIME_STOP, TipoOrdine.LIMITE);

        // Entrata 100 a limite, uscita 102 a mercato; una R e' 10 + 0,40 + 0,72.
        double atteso = (102 - 100 - 0.40 - 102 * 0.008) / (10 + 0.40 + 0.72);
        assertEquals(atteso, chiusura.risultatoInR(), 0.000001);
    }

    @Test
    void restaApertaFinchePriceNonFaNiente() {
        assertNull(RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(1, 105, 99), candela(2, 106, 98)), List.of(), TIME_STOP, TipoOrdine.LIMITE));
    }

    @Test
    void ignoraLeCandelePrecedentiAllaRegistrazione() {
        // Un crollo del giorno prima non puo' chiudere un'operazione aperta dopo.
        assertNull(RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(-3, 101, 50), candela(1, 105, 99)), List.of(), TIME_STOP, TipoOrdine.LIMITE));
    }

    @Test
    void loStopToccatoIlGiornoDellaRegistrazioneChiudeLOperazione() {
        // Registrata alle sette, crollo nel pomeriggio: la candela di quel giorno
        // inizia prima della registrazione ma non puo' essere scartata.
        var chiusura = RisolutoreOperazione.risolvi(operazione(ALLE_SETTE),
                List.of(candela(0, 101, 88), candela(1, 121, 99)), List.of(), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_ALLO_STOP, chiusura.stato());
        assertEquals(90, chiusura.prezzoUscita(), 0.0001);
        assertEquals(ALLE_SETTE, chiusura.quando(), "non si chiude prima di essere stata aperta");
    }

    @Test
    void lObiettivoToccatoIlGiornoDellaRegistrazioneNonConta() {
        // Il massimo della candela potrebbe essere delle ore prima dell'entrata.
        assertNull(RisolutoreOperazione.risolvi(operazione(ALLE_SETTE),
                List.of(candela(0, 125, 99), candela(1, 105, 99)), List.of(), TIME_STOP, TipoOrdine.LIMITE));
    }

    @Test
    void ilGiornoDellaRegistrazioneNonContaPerIlTimeStop() {
        List<Candela> giorniPiatti = new ArrayList<>();
        for (int giorno = 0; giorno <= 20; giorno++) {
            giorniPiatti.add(candela(giorno, 105, 99));
        }
        var chiusura = RisolutoreOperazione.risolvi(operazione(ALLE_SETTE), giorniPiatti, List.of(), TIME_STOP,
                TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_PER_TEMPO, chiusura.stato());
        assertEquals(REGISTRAZIONE.plus(16, ChronoUnit.DAYS), chiusura.quando());
    }

    @Test
    void seLaCandelaApreSottoLoStopSiEsceAllApertura() {
        var chiusura = RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(1, 105, 99), candela(2, 86, 80, 85)), List.of(), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_ALLO_STOP, chiusura.stato());
        assertEquals(85, chiusura.prezzoUscita(), 0.0001, "nel salto lo stop non trova prezzo");
        assertTrue(chiusura.risultatoInR() < -1, "un salto oltre lo stop costa piu' di una R");
    }

    @Test
    void nellaCandelaDiRegistrazioneLAperturaNonEUnSalto() {
        // L'apertura precede l'entrata: anche se e' sotto lo stop non dice nulla.
        var chiusura = RisolutoreOperazione.risolvi(operazione(ALLE_SETTE),
                List.of(candela(0, 101, 84, 85)), List.of(), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(90, chiusura.prezzoUscita(), 0.0001);
    }

    /** Le ventiquattro ore del giorno di registrazione: minimo a 85 alle cinque, poi fra 95 e 105. */
    private static List<Candela> oreConMinimoAlleCinque(double massimoNelPomeriggio) {
        List<Candela> ore = new ArrayList<>();
        for (int ora = 0; ora < 24; ora++) {
            double massimo = ora == 15 ? massimoNelPomeriggio : 105;
            ore.add(ora(ora, massimo, ora == 4 ? 85 : 95));
        }
        return ore;
    }

    /**
     * Registrata alle 7 (le 5 UTC): il minimo a 85 delle quattro e' prima
     * dell'entrata. Sulla candela del giorno sarebbe uno stop; sulle orarie no,
     * e l'operazione arriva all'obiettivo il giorno dopo.
     */
    @Test
    void conLeOrarieUnMinimoPrimaDellEntrataNonEUnoStop() {
        var giornaliere = List.of(candela(0, 105, 85), candela(1, 121, 99));

        var conOrarie = RisolutoreOperazione.risolvi(operazione(ALLE_SETTE), giornaliere,
                oreConMinimoAlleCinque(105), TIME_STOP, TipoOrdine.LIMITE);
        var senzaOrarie = RisolutoreOperazione.risolvi(operazione(ALLE_SETTE), giornaliere,
                List.of(), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_ALL_OBIETTIVO, conOrarie.stato());
        assertEquals(StatoOperazione.CHIUSA_ALLO_STOP, senzaOrarie.stato(), "senza orarie si assume il peggio");
    }

    /** Un obiettivo toccato alle 15 del giorno di registrazione conta: e' dopo l'entrata. */
    @Test
    void conLeOrarieLObiettivoDelGiornoDiRegistrazioneConta() {
        var chiusura = RisolutoreOperazione.risolvi(operazione(ALLE_SETTE), List.of(candela(0, 121, 85)),
                oreConMinimoAlleCinque(121), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_ALL_OBIETTIVO, chiusura.stato());
        assertEquals(REGISTRAZIONE.plus(15, ChronoUnit.HOURS), chiusura.quando());
    }

    /** Se le orarie non arrivano a fine giornata, il giorno si legge sulla candela giornaliera. */
    @Test
    void conOrarieIncompleteSiTornaAllaCandelaDelGiorno() {
        var chiusura = RisolutoreOperazione.risolvi(operazione(ALLE_SETTE), List.of(candela(0, 105, 85)),
                oreConMinimoAlleCinque(105).subList(0, 20), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_ALLO_STOP, chiusura.stato());
    }

    private static Candela ora(int ora, double massimo, double minimo) {
        double chiusura = (massimo + minimo) / 2;
        return new Candela(REGISTRAZIONE.plus(ora, ChronoUnit.HOURS), chiusura, massimo, minimo, chiusura, chiusura, 1);
    }

    /** Entrata 100, stop 90, obiettivo 120. */
    private static OperazioneRegistrata operazione() {
        return operazione(REGISTRAZIONE);
    }

    private static OperazioneRegistrata operazione(Instant registrazione) {
        return new OperazioneRegistrata("XBTUSD", Trigger.BREAKOUT_20_GIORNI, 100, 90, 120,
                1, 2.0, "prova", false, registrazione, TipoOrdine.LIMITE);
    }

    private static Candela candela(int giorno, double massimo, double minimo) {
        return candela(giorno, massimo, minimo, (massimo + minimo) / 2);
    }

    private static Candela candela(int giorno, double massimo, double minimo, double apertura) {
        Instant istante = REGISTRAZIONE.plus(giorno, ChronoUnit.DAYS);
        double chiusura = (massimo + minimo) / 2;
        return new Candela(istante, apertura, massimo, minimo, chiusura, chiusura, 1);
    }
}
