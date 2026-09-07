package dev.kraken.journal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Configurazione del client Kraken.
 * La chiave DEVE essere creata con i soli permessi "Query Funds" e
 * "Query Closed Orders & Trades". Nessun permesso di trade, nessun prelievo.
 */
@Validated
@ConfigurationProperties(prefix = "kraken")
public record KrakenProperties(
        String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String apiSecret
) {
    public KrakenProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.kraken.com";
        }
    }
}
