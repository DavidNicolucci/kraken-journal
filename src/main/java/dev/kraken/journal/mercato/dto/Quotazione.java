package dev.kraken.journal.mercato.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Riepilogo a 24 ore di una coppia.
 *
 * Attenzione al campo che serve davvero: Kraken espone il volume in unita' di
 * base (quanti BTC), non in valuta. La soglia del milione della guida e' un
 * controvalore, quindi il campo da confrontare e' {@link #controvalore24h()},
 * mai {@link #volume24h()}.
 */
public record Quotazione(
        String coppia,
        double ultimo,
        double volume24h,
        double prezzoMedio24h,
        double minimo24h,
        double massimo24h,
        long scambi24h) {

    /** Controvalore scambiato in 24 ore, nella valuta di quotazione. */
    @JsonProperty("controvalore24h")
    public double controvalore24h() {
        return volume24h * prezzoMedio24h;
    }
}
