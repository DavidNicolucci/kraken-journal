package dev.kraken.journal.scansione;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.scansione.dto.EsitoControllo;
import dev.kraken.journal.scansione.dto.Indicatori;
import dev.kraken.journal.scansione.dto.Piano;

class ChecklistTest {

    /** Rottura con volume, ma stop vicinissimo e prezzo lontano dalla media: passa solo con le regole spente. */
    private static final Indicatori ROTTURA = new Indicatori(Instant.parse("2026-11-27T00:00:00Z"),
            115, 100, true, 70, 2, 1_000_000, 120, 113, 114.5, 110, 3_000_000, 1_000_000);

    @Test
    void senzaConfigurazioneValgonoTuttiIControlli() {
        assertEquals(EnumSet.allOf(Controllo.class), proprieta(null).controlliAttivi());
        assertEquals(EnumSet.allOf(Controllo.class), proprieta(Set.of()).controlliAttivi());
    }

    @Test
    void unControlloSpentoNonBocciaENonCompare() {
        ProprietaScansione tutte = proprieta(null);
        ProprietaScansione tre = proprieta(EnumSet.of(Controllo.LIQUIDITA, Controllo.TREND, Controllo.BREAKOUT));

        List<EsitoControllo> conTutte = controlla(tutte);
        List<EsitoControllo> conTre = controlla(tre);

        assertEquals(6, conTutte.size());
        assertTrue(conTutte.stream().anyMatch(e -> e.esito() == Esito.BOCCIATO), "distanza e ampiezza stop bocciano");
        assertEquals(List.of(Controllo.LIQUIDITA, Controllo.TREND, Controllo.BREAKOUT),
                conTre.stream().map(EsitoControllo::controllo).toList());
        assertTrue(conTre.stream().allMatch(e -> e.esito() == Esito.SUPERATO));
    }

    /**
     * Senza il limite sull'ampiezza dello stop, il rischio non deve chiedere piu' del capitale.
     * Con l'1% le commissioni bastano a impedirlo; con il 3% e uno stop sotto l'1% no.
     */
    @Test
    void conUnoStopVicinissimoLaQuantitaNonSuperaIlCapitale() {
        ProprietaScansione rischioAlto = new ProprietaScansione(List.of("XBTEUR"), 1_000_000, 12, 5, 10, 2.0, 203, 3, 5,
                0.5, 15, 15, 2, TipoOrdine.LIMITE, 20, 1.5, null);
        Piano piano = new ValutatoreSetup(rischioAlto).valuta(115, ROTTURA);

        assertTrue(piano.ampiezzaStop() < 1, "stop sotto l'1%: " + piano.ampiezzaStop());
        assertEquals(203, piano.controvalore(), 1e-9);
        assertTrue(piano.perditaAlloStop() < rischioAlto.rischioAmmesso(), "si rischia meno di una R piena");
    }

    private static List<EsitoControllo> controlla(ProprietaScansione proprieta) {
        Piano piano = new ValutatoreSetup(proprieta).valuta(115.5, ROTTURA);
        return Checklist.controlla(2_000_000, 115.5, ROTTURA, piano, proprieta);
    }

    private static ProprietaScansione proprieta(Set<Controllo> attivi) {
        return new ProprietaScansione(List.of("XBTEUR"), 1_000_000, 12, 5, 10, 2.0, 203, 1, 5, 0.5, 15, 15, 2,
                TipoOrdine.LIMITE, 20, 1.5, attivi);
    }
}
