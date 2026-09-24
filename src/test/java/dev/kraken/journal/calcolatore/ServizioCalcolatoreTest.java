package dev.kraken.journal.calcolatore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.calcolatore.dto.EsitoValutazione;
import dev.kraken.journal.calcolatore.dto.RichiestaValutazione;
import dev.kraken.journal.scansione.Verso;

/**
 * Il riferimento e' la pagina PRIMA che smettesse di calcolare: se questi
 * numeri cambiano, lo spostamento della matematica dal browser al server ha
 * modificato qualcosa che doveva restare identico.
 *
 * Caso noto: HYPE/EUR, entrata 72,00, stop 68,00, obiettivo 79,98, capitale
 * 203 EUR, rischio 1%, ordini limite su entrambi i lati. La pagina rispondeva
 * quantita' 0,445175, pareggio 72,58, rapporto 1,62 a 1.
 */
class ServizioCalcolatoreTest {

    private final ServizioCalcolatore servizio = new ServizioCalcolatore();

    @Test
    void riproduceINumeriDellaPagina() {
        EsitoValutazione e = servizio.valuta(acquisto(72.00, 68.00, 79.98));

        assertEquals(0.445175, e.quantita(), 0.000001);
        assertEquals(72.578313, e.pareggio(), 0.00001);
        assertEquals(1.616684, e.rapporto(), 0.00001);
        assertEquals(2.03, e.rischioAmmesso(), 0.0001);
        assertEquals(4.56, e.perditaPerUnita(), 0.0001);
    }

    @Test
    void senzaObiettivoIlRapportoENulloNonZero() {
        EsitoValutazione e = servizio.valuta(acquisto(72.00, 68.00, 0));

        assertNull(e.rapporto(), "non calcolabile e vale zero sono due cose diverse");
        assertNull(e.guadagnoAllObiettivo());
        assertEquals(0.445175, e.quantita(), 0.000001, "la quantita' si calcola lo stesso");
    }

    @Test
    void loScopertoInverteISegni() {
        EsitoValutazione e = servizio.valuta(new RichiestaValutazione(
                203, 1, Verso.VENDITA_ALLO_SCOPERTO, 100, 105, 90, 0.40, 0.40));

        assertEquals(5.82, e.perditaPerUnita(), 0.000001, "5 di distanza piu' le due commissioni");
        assertEquals(99.203187, e.pareggio(), 0.00001, "vendendo si pareggia sotto l'entrata");
        assertEquals(9.24 / 5.82, e.rapporto(), 0.000001);
        assertTrue(e.pareggio() < 100);
    }

    @Test
    void leCommissioniPossonoEssereDiverseSuiDueLati() {
        EsitoValutazione aMercato = servizio.valuta(new RichiestaValutazione(
                203, 1, Verso.ACQUISTO, 72, 68, 79.98, 0.80, 0.40));
        EsitoValutazione aLimite = servizio.valuta(acquisto(72.00, 68.00, 79.98));

        assertTrue(aMercato.perditaPerUnita() > aLimite.perditaPerUnita());
        assertTrue(aMercato.quantita() < aLimite.quantita(),
                "se ogni unita' costa di piu', a parita' di rischio se ne comprano meno");
    }

    @Test
    void loStopDallaParteSbagliataVieneRifiutato() {
        var errore = assertThrows(IllegalArgumentException.class,
                () -> servizio.valuta(acquisto(72.00, 75.00, 79.98)));
        assertTrue(errore.getMessage().contains("sotto"));
    }

    @Test
    void segnalaQuandoLaPosizioneNonStaNelCapitale() {
        // Stop vicinissimo: la quantita' corretta vale 225 EUR contro 203 di capitale.
        EsitoValutazione e = servizio.valuta(new RichiestaValutazione(
                203, 1, Verso.ACQUISTO, 100, 99.9, 110, 0.40, 0.40));
        assertTrue(e.capitaleSuperato());
    }

    @Test
    void unObiettivoSottoIlPareggioNonHaUnaPercentualeDiSuccesso() {
        // Pareggio a 72,58: a 72,30 il prezzo va nella direzione giusta ma si perde.
        EsitoValutazione e = servizio.valuta(acquisto(72.00, 68.00, 72.30));

        assertTrue(e.obiettivoNonCopreICosti());
        assertTrue(e.guadagnoAllObiettivo() < 0, "all'obiettivo si perde, e il numero lo deve dire");
        assertTrue(e.rapporto() < 0);
        assertNull(e.percentualeMinimaDiSuccesso(),
                "nessuna percentuale di successo porta in pari: non va inventata");
    }

    @Test
    void unObiettivoOltreIlPareggioCopreICosti() {
        EsitoValutazione e = servizio.valuta(acquisto(72.00, 68.00, 79.98));

        assertFalse(e.obiettivoNonCopreICosti());
        assertEquals(100 / (1 + e.rapporto()), e.percentualeMinimaDiSuccesso(), 0.000001);
    }

    private static RichiestaValutazione acquisto(double entrata, double stop, double obiettivo) {
        return new RichiestaValutazione(203, 1, Verso.ACQUISTO, entrata, stop, obiettivo, 0.40, 0.40);
    }
}
