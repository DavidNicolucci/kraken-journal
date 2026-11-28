package dev.kraken.journal.verifica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.registro.StatoOperazione;
import dev.kraken.journal.scansione.CalcolatoreIndicatori;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.scansione.TipoOrdine;
import dev.kraken.journal.scansione.ValutatoreSetup;
import dev.kraken.journal.verifica.VerificaStorica.EsitoCoppia;
import dev.kraken.journal.verifica.dto.OperazioneStorica;
import dev.kraken.journal.verifica.dto.Riepilogo;

class VerificaStoricaTest {

    private static final Instant INIZIO = Instant.parse("2025-01-01T00:00:00Z");
    private static final int ROTTURA = 80;
    private static final ProprietaScansione PROPRIETA = new ProprietaScansione(List.of("XBTEUR"), 1000, 12, 3, 10,
            2.0, 203, 1, 5, 0.5, 15, 3, 2, TipoOrdine.LIMITE, 20, 1.5, null);
    private static final ValutatoreSetup VALUTATORE = new ValutatoreSetup(PROPRIETA);

    /**
     * Ottanta giorni piatti, poi una chiusura sopra il massimo con il triplo del
     * volume: il segnale e' quella candela, l'entrata l'apertura del giorno dopo.
     */
    @Test
    void unaRotturaDiventaUnOperazioneConLoStessoPianoDellaScansione() {
        List<Candela> candele = rotturaPoi(g -> 103.2 * (1 + 0.015 * (g - ROTTURA)));

        EsitoCoppia esito = VerificaStorica.simula("XBTEUR", candele, PROPRIETA, VALUTATORE);

        OperazioneStorica prima = esito.operazioni().get(0);
        assertEquals(giorno(ROTTURA), prima.segnale());
        assertEquals(103.2, prima.entrata(), 1e-9);
        assertEquals(StatoOperazione.CHIUSA_ALL_OBIETTIVO, prima.stato());
        assertTrue(prima.risultatoInR() > 1.5, "obiettivo a 2 R, commissioni tolte: " + prima.risultatoInR());
        assertEquals(candele.size() - CalcolatoreIndicatori.CANDELE_MINIME, esito.giorni());
    }

    /** Il segnale non puo' dipendere da cosa succede dopo: cambia solo l'esito. */
    @Test
    void ilSegnaleNonGuardaIlFuturo() {
        List<Candela> crollo = rotturaPoi(g -> g == ROTTURA + 1 ? 103.2 : 95);

        OperazioneStorica prima = VerificaStorica.simula("XBTEUR", crollo, PROPRIETA, VALUTATORE).operazioni().get(0);

        assertEquals(giorno(ROTTURA), prima.segnale());
        assertEquals(StatoOperazione.CHIUSA_ALLO_STOP, prima.stato());
        assertTrue(prima.risultatoInR() < -1, "apertura sotto lo stop: si esce peggio dello stop, " + prima.risultatoInR());
    }

    /** Come nel registro: con un'operazione aperta sulla coppia, le rotture successive non contano. */
    @Test
    void unaCoppiaNonApreUnaSecondaOperazioneFincheLaPrimaEAperta() {
        Random caso = new Random(7);
        List<Candela> candele = new ArrayList<>();
        double prezzo = 100;
        for (int g = 0; g < 500; g++) {
            double apertura = prezzo;
            prezzo *= 1 + 0.004 + 0.02 * caso.nextGaussian();
            double volume = g % 7 == 0 ? 3000 : 1000;
            candele.add(new Candela(giorno(g), apertura, Math.max(apertura, prezzo) * 1.01,
                    Math.min(apertura, prezzo) * 0.99, prezzo, prezzo, volume));
        }

        EsitoCoppia esito = VerificaStorica.simula("XBTEUR", candele, PROPRIETA, VALUTATORE);

        assertFalse(esito.operazioni().isEmpty());
        for (int i = 1; i < esito.operazioni().size(); i++) {
            OperazioneStorica prima = esito.operazioni().get(i - 1);
            OperazioneStorica dopo = esito.operazioni().get(i);
            assertTrue(dopo.segnale().plus(1, ChronoUnit.DAYS).isAfter(prima.chiusura()),
                    "entrata il " + dopo.segnale().plus(1, ChronoUnit.DAYS) + " con la precedente chiusa il " + prima.chiusura());
        }
        esito.bocciature().values().forEach(volte -> assertTrue(volte <= esito.giorni()));
    }

    @Test
    void ilGiudizioUsaIlMargineELaSogliaDelRegistro() {
        assertEquals(Giudizio.POCHI_CASI, riepilogo(2.0, 2.0).giudizio(), "sotto i 3 casi");
        assertEquals(Giudizio.POSITIVO, riepilogo(2.0, 1.9, 2.1, 2.0).giudizio());
        assertEquals(Giudizio.NEGATIVO, riepilogo(-1.0, -1.1, -0.9, -1.0).giudizio());
        assertEquals(Giudizio.INCERTO, riepilogo(2.0, -1.0, 2.0, -1.0).giudizio());

        Riepilogo conAperta = VerificaStorica.riepilogo(null, List.of(operazione(2.0), operazione(-1.0), operazione(null)), 3);
        assertEquals(3, conAperta.operazioni());
        assertEquals(2, conAperta.chiuse());
        assertEquals(1.0, conAperta.sommaInR(), 1e-12);
        assertEquals(0.5, conAperta.percentualeVincenti(), 1e-12);
    }

    private static Riepilogo riepilogo(Double... risultati) {
        List<OperazioneStorica> operazioni = new ArrayList<>();
        for (Double r : risultati) {
            operazioni.add(operazione(r));
        }
        return VerificaStorica.riepilogo(null, operazioni, 3);
    }

    private static OperazioneStorica operazione(Double risultato) {
        StatoOperazione stato = risultato == null ? StatoOperazione.APERTA
                : risultato > 0 ? StatoOperazione.CHIUSA_ALL_OBIETTIVO : StatoOperazione.CHIUSA_ALLO_STOP;
        return new OperazioneStorica("XBTEUR", INIZIO, 100, 95, 110, 2, stato, null, null, risultato);
    }

    /** Ottanta giorni piatti, la rottura, poi le chiusure date dalla funzione dal giorno dopo in poi. */
    private static List<Candela> rotturaPoi(java.util.function.IntToDoubleFunction dopo) {
        List<Candela> candele = new ArrayList<>();
        for (int g = 0; g < ROTTURA; g++) {
            double c = g % 2 == 0 ? 100.2 : 99.8;
            candele.add(new Candela(giorno(g), c, c + 0.5, c - 0.5, c, c, 1000));
        }
        candele.add(new Candela(giorno(ROTTURA), 100, 103.2, 99.8, 103, 103, 3000));
        for (int g = ROTTURA + 1; g < ROTTURA + 40; g++) {
            double c = dopo.applyAsDouble(g);
            double apertura = g == ROTTURA + 1 ? 103.2 : c;
            candele.add(new Candela(giorno(g), apertura, Math.max(apertura, c) * 1.005,
                    Math.min(apertura, c) * 0.995, c, c, 1000));
        }
        return candele;
    }

    private static Instant giorno(int g) {
        return INIZIO.plus(g, ChronoUnit.DAYS);
    }
}
