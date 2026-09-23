package dev.kraken.journal.giornaliero;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

/**
 * Un cron sbagliato non fa rumore: il lavoro semplicemente non parte, e ci si
 * accorge settimane dopo che il registro e' rimasto vuoto. Questi test leggono
 * la stessa espressione che finisce in application.yml.
 */
class PianificazioneTest {

    private static final String CRON = "0 0 7 * * *";
    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");

    @Test
    void scattaAlleSetteDelMattinoDelGiornoDopo() {
        ZonedDateTime prossima = CronExpression.parse(CRON)
                .next(LocalDateTime.of(2026, 9, 23, 12, 0).atZone(ROMA));

        assertEquals(24, prossima.getDayOfMonth());
        assertEquals(7, prossima.getHour());
        assertEquals(0, prossima.getMinute());
    }

    @Test
    void restaAlleSetteDiRomaAncheDopoIlCambioDiOra() {
        // L'ora legale 2026 finisce domenica 25 ottobre.
        ZonedDateTime dopoIlCambio = CronExpression.parse(CRON)
                .next(LocalDateTime.of(2026, 10, 25, 12, 0).atZone(ROMA));

        assertEquals(7, dopoIlCambio.getHour(), "le sette restano le sette, non diventano le sei");
        assertEquals(ZoneOffset.ofHours(1), dopoIlCambio.getOffset(), "il 26 ottobre si e' gia' in ora solare");
    }

    @Test
    void alleSetteLaCandelaDiIeriEGiaChiusaInEntrambeLeStagioni() {
        // Kraken chiude le candele giornaliere a mezzanotte UTC.
        assertEquals(5, aSetteDiRoma(2026, 7, 15).getHour(), "in ora legale sono le 05:00 UTC");
        assertEquals(6, aSetteDiRoma(2027, 1, 15).getHour(), "in ora solare sono le 06:00 UTC");
    }

    private static LocalTime aSetteDiRoma(int anno, int mese, int giorno) {
        return LocalDateTime.of(anno, mese, giorno, 7, 0).atZone(ROMA)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalTime();
    }
}
