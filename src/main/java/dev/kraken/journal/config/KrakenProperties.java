package dev.kraken.journal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Configurazione del client Kraken.
 * La chiave DEVE essere creata con i soli permessi di lettura che l'app usa:
 * "Query Closed Orders & Trades" (TradesHistory) e "Query Ledger Entries"
 * (Ledgers, per le operazioni fatte dall'app). Nessun permesso di trade,
 * nessun prelievo.
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

    /**
     * Chiave e segreto non finiscono in un log: il toString generato di un
     * record li stamperebbe in chiaro al primo log.info di questo oggetto.
     */
    @Override
    public String toString() {
        return "KrakenProperties[baseUrl=" + baseUrl + ", apiKey=***, apiSecret=***]";
    }
}
