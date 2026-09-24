package dev.kraken.journal.kraken;

import java.time.Duration;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Ritenta una chiamata a Kraken quando l'errore e' temporaneo, aspettando ogni
 * volta il doppio.
 *
 * Perche' serve: Kraken limita la frequenza delle chiamate sia pubbliche sia
 * private, e risponde con un errore invece di rallentare. Senza ritentare, il
 * lavoro delle sette perdeva in silenzio le coppie rifiutate (la scansione le
 * salta e le registra solo nel log) e il journal falliva per intero oltre
 * qualche centinaio di trade.
 *
 * La chiamata va passata intera, non il solo risultato: per gli endpoint
 * privati ogni tentativo deve avere un nonce nuovo e quindi una firma nuova.
 */
public final class Ritentativi {

    private static final Logger log = LoggerFactory.getLogger(Ritentativi.class);
    public static final int TENTATIVI = 4;

    private Ritentativi() {
    }

    public static <T> T conRitentativi(String cosa, Duration attesaIniziale, Supplier<T> chiamata) {
        Duration attesa = attesaIniziale;
        for (int tentativo = 1; ; tentativo++) {
            try {
                return chiamata.get();
            } catch (RuntimeException e) {
                if (!temporaneo(e) || tentativo >= TENTATIVI) {
                    throw e;
                }
                log.info("{}: errore temporaneo al tentativo {} di {}, riprovo fra {} ms ({})",
                        cosa, tentativo, TENTATIVI, attesa.toMillis(), e.getMessage());
                dormi(attesa);
                attesa = attesa.multipliedBy(2);
            }
        }
    }

    private static boolean temporaneo(RuntimeException e) {
        return (e instanceof KrakenApiException k && k.temporanea())
                || e instanceof HttpClientErrorException.TooManyRequests
                || e instanceof HttpServerErrorException;
    }

    private static void dormi(Duration attesa) {
        try {
            Thread.sleep(attesa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Attesa fra due tentativi interrotta", e);
        }
    }
}
