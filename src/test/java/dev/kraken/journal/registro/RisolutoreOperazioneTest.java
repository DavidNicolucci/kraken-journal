package dev.kraken.journal.registro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.scansione.TipoOrdine;

class RisolutoreOperazioneTest {

    private static final Instant REGISTRAZIONE = Instant.parse("2026-09-23T00:00:00Z");
    private static final int TIME_STOP = 15;

    @Test
    void chiudeAlloStopQuandoIlMinimoLoTocca() {
        var chiusura = RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(1, 102, 98), candela(2, 101, 89)), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_ALLO_STOP, chiusura.stato());
        assertEquals(90, chiusura.prezzoUscita(), 0.0001);
    }

    @Test
    void alloStopIlRisultatoEsattamenteMenoUnaR() {
        var chiusura = RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(1, 101, 89)), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(-1.0, chiusura.risultatoInR(), 0.000001,
                "una R e' la perdita allo stop, commissioni comprese: per definizione vale -1");
    }

    @Test
    void chiudeAllObiettivoQuandoIlMassimoLoRaggiunge() {
        var chiusura = RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(1, 105, 99), candela(2, 121, 104)), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_ALL_OBIETTIVO, chiusura.stato());
        assertEquals(120, chiusura.prezzoUscita(), 0.0001);
    }

    @Test
    void seNellaStessaCandelaCiSonoEntrambiVinceLoStop() {
        var chiusura = RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(1, 125, 85)), TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_ALLO_STOP, chiusura.stato(),
                "la candela giornaliera non dice quale sia arrivato prima: si assume il peggio");
    }

    @Test
    void chiudePerTempoDopoIGiorniPrevisti() {
        List<Candela> quindiciGiorniPiatti = new ArrayList<>();
        for (int giorno = 1; giorno <= 20; giorno++) {
            quindiciGiorniPiatti.add(candela(giorno, 105, 99));
        }
        var chiusura = RisolutoreOperazione.risolvi(operazione(), quindiciGiorniPiatti, TIME_STOP, TipoOrdine.LIMITE);

        assertEquals(StatoOperazione.CHIUSA_PER_TEMPO, chiusura.stato());
        assertEquals(REGISTRAZIONE.plus(15, ChronoUnit.DAYS), chiusura.quando());
    }

    @Test
    void restaApertaFinchePriceNonFaNiente() {
        assertNull(RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(1, 105, 99), candela(2, 106, 98)), TIME_STOP, TipoOrdine.LIMITE));
    }

    @Test
    void ignoraLeCandelePrecedentiAllaRegistrazione() {
        // Un crollo del giorno prima non puo' chiudere un'operazione aperta dopo.
        assertNull(RisolutoreOperazione.risolvi(operazione(),
                List.of(candela(-3, 101, 50), candela(1, 105, 99)), TIME_STOP, TipoOrdine.LIMITE));
    }

    /** Entrata 100, stop 90, obiettivo 120. */
    private static OperazioneRegistrata operazione() {
        return new OperazioneRegistrata("XBTUSD", Trigger.BREAKOUT_20_GIORNI, 100, 90, 120,
                1, 2.0, "prova", false, REGISTRAZIONE);
    }

    private static Candela candela(int giorno, double massimo, double minimo) {
        Instant istante = REGISTRAZIONE.plus(giorno, ChronoUnit.DAYS);
        double chiusura = (massimo + minimo) / 2;
        return new Candela(istante, chiusura, massimo, minimo, chiusura, chiusura, 1);
    }
}
