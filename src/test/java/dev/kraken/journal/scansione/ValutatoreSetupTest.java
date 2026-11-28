package dev.kraken.journal.scansione;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.scansione.dto.Indicatori;
import dev.kraken.journal.scansione.dto.Piano;

/**
 * Caso: entrata 72,00, stop 68,00, capitale 203 EUR, rischio 1%, entrata con
 * ordine limite. Lo stop esce a mercato: e' il calcolatore con 0,40% in
 * entrata e 0,80% in uscita, che risponde quantita' 0,420116. Il pareggio
 * resta 72,58, perche' si esce all'obiettivo con un ordine limite.
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
        assertEquals(0.420116, piano.quantita(), 0.000001);
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

    /** Prezzo sceso sotto lo stop: il piano e' vuoto, non un'operazione da 2 R. */
    @Test
    void conIlPrezzoSottoLoStopIlPianoEVuoto() {
        Piano piano = valutatore.valuta(67.00, indicatori(100));

        assertEquals(0, piano.quantita());
        assertEquals(0, piano.perditaAlloStop());
        assertEquals(0, piano.guadagnoAllObiettivo());
        assertEquals(0, piano.rapporto());
        assertTrue(piano.ampiezzaStop() < 0);
    }

    @Test
    void unPeriodoFuoriMisuraFermaLAvvio() {
        var errore = assertThrows(IllegalArgumentException.class, () -> new ProprietaScansione(List.of("HYPEEUR"),
                1_000_000, 12, 5, 10, 2.0, 203, 1, 0, 0.5, 15, 15, 2, TipoOrdine.LIMITE, 20, 1.5, null));
        assertTrue(errore.getMessage().contains("giorni-minimo-stop va da 1 a 60"), errore.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new ProprietaScansione(List.of("HYPEEUR"),
                1_000_000, 12, 5, 10, 2.0, 203, 1, 5, 0.5, 15, 15, 2, TipoOrdine.LIMITE, 60, 1.5, null));
    }

    /** Il minimo e' scelto perche', tolto lo 0,5% di margine, lo stop cada a 68,00. */
    private static Indicatori indicatori(double massimo30) {
        return new Indicatori(Instant.EPOCH, ENTRATA, 68.5, true, 60, 3.8, 5_000_000,
                massimo30, 66.0, STOP_ATTESO / 0.995, 71.0, 8_000_000, 5_000_000);
    }

    private static ProprietaScansione proprieta() {
        return new ProprietaScansione(List.of("HYPEEUR"), 1_000_000, 12, 5, 10, 2.0,
                203, 1, 5, 0.5, 15, 15, 2, TipoOrdine.LIMITE, 20, 1.5, null);
    }
}
