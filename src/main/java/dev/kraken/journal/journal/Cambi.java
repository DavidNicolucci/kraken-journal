package dev.kraken.journal.journal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Cambi giornalieri fra valute, presi dalle candele pubbliche di Kraken.
 *
 * Servono a una cosa sola: dare un risultato a una vendita fatta in una valuta
 * diversa da quella dell'acquisto (LIGHTER comprato in EUR dall'app e venduto
 * in USD su Kraken Pro). Senza, quella vendita esce dal carico ma resta senza
 * risultato.
 *
 * Il cambio di un giorno e' il prezzo medio della candela giornaliera (UTC):
 * l'ora esatta dell'operazione non c'e', e la media del giorno sbaglia meno
 * della chiusura. Un giorno senza candela prende l'ultima prima di lui; un
 * giorno prima della prima candela non ha cambio.
 *
 * @param perCoppia per ogni cambio quotato da Kraken (EURUSD), il valore di
 *        ogni giorno: quante unita' della seconda valuta vale una della prima.
 */
public record Cambi(Map<String, NavigableMap<LocalDate, BigDecimal>> perCoppia) {

    public static final Cambi NESSUNO = new Cambi(Map.of());
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    /** Quante unita' di {@code a} vale una unita' di {@code da} nel giorno di {@code quando}. */
    public Optional<BigDecimal> tasso(String da, String a, Instant quando) {
        if (da.equals(a)) {
            return Optional.of(BigDecimal.ONE);
        }
        LocalDate giorno = LocalDate.ofInstant(quando, ZoneOffset.UTC);
        return valore(da + a, giorno).or(() -> valore(a + da, giorno).map(t -> BigDecimal.ONE.divide(t, MC)));
    }

    private Optional<BigDecimal> valore(String coppia, LocalDate giorno) {
        return Optional.ofNullable(perCoppia.get(coppia)).map(valori -> valori.floorEntry(giorno)).map(Map.Entry::getValue);
    }
}
