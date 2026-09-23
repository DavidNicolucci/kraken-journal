package dev.kraken.journal.scansione;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.scansione.dto.Indicatori;
import dev.kraken.journal.scansione.dto.Piano;

/**
 * Il riferimento della quantita' e del pareggio resta calcolatore.html: finche'
 * la pagina non chiamera' questo endpoint le due implementazioni devono dare
 * gli stessi numeri.
 *
 * Caso: entrata 72,00, stop 68,00, capitale 203 EUR, rischio 1%, ordini limite.
 * La pagina rispondeva quantita' 0,445175 e pareggio 72,58.
 *
 * L'obiettivo invece NON e' piu' un livello del grafico: dalla regola di uscita
 * 2.2 si costruisce a 2 R netti commissioni.
 */
class ValutatoreSetupTest {

    private static final double ENTRATA = 72.00;
    private static final double STOP_ATTESO = 68.00;

    private final ValutatoreSetup valutatore = new ValutatoreSetup(proprieta());

    @Test
    void riproduceQuantitaEPareggioDelCalcolatore() {
        Piano piano = valutatore.valuta(ENTRATA, indicatori(100));

        assertEquals(STOP_ATTESO, piano.stop(), 0.0001);
        assertEquals(0.445175, piano.quantita(), 0.000001);
        assertEquals(72.58, piano.pareggio(), 0.005);
    }

    @Test
    void lObiettivoValeEsattamenteDueErreNetteCommissioni() {
        Piano piano = valutatore.valuta(ENTRATA, indicatori(100));

        assertEquals(2.0, piano.rapporto(), 0.000001,
                "l'obiettivo e' costruito a partire dal risultato voluto, commissioni comprese");
        assertEquals(2 * piano.perditaAlloStop(), piano.guadagnoAllObiettivo(), 0.000001);
    }

    @Test
    void lObiettivoStaSopraIlPareggioQuindiSopraLEntrata() {
        Piano piano = valutatore.valuta(ENTRATA, indicatori(100));
        assertTrue(piano.obiettivo() > piano.pareggio());
    }

    @Test
    void laPerditaAlloStopEsattamenteIlRischioAmmesso() {
        assertEquals(2.03, valutatore.valuta(ENTRATA, indicatori(100)).perditaAlloStop(), 0.001,
                "la quantita' esiste proprio per far combaciare questo numero");
    }

    @Test
    void ilMassimoATrentaGiorniNonInfluenzaPiuIlPiano() {
        // Era l'obiettivo, ed era l'errore: dopo un breakout sta sotto l'entrata.
        Piano conMassimoBasso = valutatore.valuta(ENTRATA, indicatori(70));
        Piano conMassimoAlto = valutatore.valuta(ENTRATA, indicatori(200));

        assertEquals(conMassimoBasso.obiettivo(), conMassimoAlto.obiettivo(), 0.000001);
    }

    /** Il minimo e' scelto perche', tolto lo 0,5% di margine, lo stop cada a 68,00. */
    private static Indicatori indicatori(double massimo30) {
        return new Indicatori(Instant.EPOCH, ENTRATA, 68.5, true, 60, 3.8, 5_000_000,
                massimo30, 66.0, STOP_ATTESO / 0.995);
    }

    private static ProprietaScansione proprieta() {
        return new ProprietaScansione(List.of("HYPEEUR"), 1_000_000, 50, 70, 12, 5, 10, 2.0,
                203, 1, 5, 0.5, 15, 15, 2, TipoOrdine.LIMITE);
    }
}
