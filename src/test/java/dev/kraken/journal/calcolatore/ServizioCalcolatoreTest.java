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
 * 203 EUR, rischio 1%, ordini limite in entrata e all'obiettivo. La pagina
 * rispondeva quantita' 0,445175 e rapporto 1,62 a 1, pagando lo stop a
 * limite. Lo stop esce a mercato: quantita' 0,420116, la stessa della
 * scansione (ValutatoreSetupTest), e rapporto 1,53. Il pareggio, 72,58, non
 * cambia: e' all'obiettivo.
 */
class ServizioCalcolatoreTest {

    private final ServizioCalcolatore servizio = new ServizioCalcolatore();

    @Test
    void riproduceINumeriDellaPagina() {
        EsitoValutazione e = servizio.valuta(acquisto(72.00, 68.00, 79.98));

        assertEquals(0.420116, e.quantita(), 0.000001);
        assertEquals(72.578313, e.pareggio(), 0.00001);
        assertEquals(7.37208 / 4.832, e.rapporto(), 0.00001);
        assertEquals(2.03, e.rischioAmmesso(), 0.0001);
        assertEquals(4.832, e.perditaPerUnita(), 0.0001, "4 di distanza, 0,40% su 72 e 0,80% su 68");
    }

    @Test
    void senzaObiettivoIlRapportoENulloNonZero() {
        EsitoValutazione e = servizio.valuta(acquisto(72.00, 68.00, 0));

        assertNull(e.rapporto(), "non calcolabile e vale zero sono due cose diverse");
        assertNull(e.guadagnoAllObiettivo());
        assertEquals(0.420116, e.quantita(), 0.000001, "la quantita' si calcola lo stesso");
    }

    @Test
    void loScopertoInverteISegni() {
        EsitoValutazione e = servizio.valuta(new RichiestaValutazione(
                203, 1, Verso.VENDITA_ALLO_SCOPERTO, 100, 105, 90, 0.40, 0.40));

        assertEquals(6.24, e.perditaPerUnita(), 0.000001, "5 di distanza, 0,40% su 100 e lo stop a mercato su 105");
        assertEquals(99.203187, e.pareggio(), 0.00001, "vendendo si pareggia sotto l'entrata");
        assertEquals(9.24 / 6.24, e.rapporto(), 0.000001);
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

    /** La stessa operazione deve dare la stessa quantita' qui e nella scansione, qualunque uscita si scelga. */
    @Test
    void lUscitaSceltaNonCambiaLoStop() {
        EsitoValutazione aLimite = servizio.valuta(acquisto(72.00, 68.00, 79.98));
        EsitoValutazione aMercato = servizio.valuta(new RichiestaValutazione(
                203, 1, Verso.ACQUISTO, 72, 68, 79.98, 0.40, 0.80));

        assertEquals(aMercato.quantita(), aLimite.quantita(), 0.0000001);
        assertEquals(aMercato.commissioneInUscitaAlloStop(), aLimite.commissioneInUscitaAlloStop(), 0.0000001);
        assertTrue(aLimite.rapporto() > aMercato.rapporto(), "all'obiettivo l'ordine limite costa meno");
    }

    @Test
    void loStopDallaParteSbagliataVieneRifiutato() {
        var errore = assertThrows(IllegalArgumentException.class,
                () -> servizio.valuta(acquisto(72.00, 75.00, 79.98)));
        assertTrue(errore.getMessage().contains("sotto"));
    }

    @Test
    void segnalaQuandoLaPosizioneNonStaNelCapitale() {
        // Stop vicinissimo e entrata senza commissione: la quantita' corretta vale 225 EUR contro 203 di capitale.
        EsitoValutazione e = servizio.valuta(new RichiestaValutazione(
                203, 1, Verso.ACQUISTO, 100, 99.9, 110, 0, 0.40));
        assertTrue(e.capitaleSuperato());
    }

    /**
     * Senza leva si paga anche la commissione d'entrata: rischio 2%, perdita
     * per unita' 2,008, posizione da 202,19 EUR piu' 1,62 di commissione non
     * stanno in 203.
     */
    @Test
    void ilCapitaleComprendeLaCommissioneDEntrata() {
        EsitoValutazione e = servizio.valuta(new RichiestaValutazione(
                203, 2, Verso.ACQUISTO, 100, 99.5887, 110, 0.80, 0.40));
        assertTrue(e.controvalore() < 203);
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
