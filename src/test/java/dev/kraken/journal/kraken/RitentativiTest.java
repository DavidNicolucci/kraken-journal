package dev.kraken.journal.kraken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class RitentativiTest {

    private static final KrakenApiException LIMITE =
            new KrakenApiException("/0/public/OHLC", List.of("EGeneral:Too many requests"));
    private static final KrakenApiException COPPIA_SCONOSCIUTA =
            new KrakenApiException("/0/public/Ticker", List.of("EQuery:Unknown asset pair"));

    @Test
    void riconosceGliErroriTemporanei() {
        assertTrue(LIMITE.temporanea());
        assertTrue(new KrakenApiException("x", List.of("EAPI:Rate limit exceeded")).temporanea());
        assertFalse(COPPIA_SCONOSCIUTA.temporanea());
    }

    @Test
    void ritentaUnErroreTemporaneoFinoARiuscire() {
        AtomicInteger chiamate = new AtomicInteger();

        String esito = Ritentativi.conRitentativi("prova", Duration.ZERO, () -> {
            if (chiamate.incrementAndGet() < 3) throw LIMITE;
            return "ok";
        });

        assertEquals("ok", esito);
        assertEquals(3, chiamate.get());
    }

    @Test
    void nonRitentaUnErroreDefinitivo() {
        AtomicInteger chiamate = new AtomicInteger();

        var errore = assertThrows(KrakenApiException.class, () ->
                Ritentativi.conRitentativi("prova", Duration.ZERO, () -> {
                    chiamate.incrementAndGet();
                    throw COPPIA_SCONOSCIUTA;
                }));

        assertSame(COPPIA_SCONOSCIUTA, errore);
        assertEquals(1, chiamate.get(), "una coppia sconosciuta resta sconosciuta");
    }

    @Test
    void siArrendeDopoIlNumeroMassimoDiTentativi() {
        AtomicInteger chiamate = new AtomicInteger();

        assertThrows(KrakenApiException.class, () ->
                Ritentativi.conRitentativi("prova", Duration.ZERO, () -> {
                    chiamate.incrementAndGet();
                    throw LIMITE;
                }));

        assertEquals(Ritentativi.TENTATIVI, chiamate.get());
    }
}
