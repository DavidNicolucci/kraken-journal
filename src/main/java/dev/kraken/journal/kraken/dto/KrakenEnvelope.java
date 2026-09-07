package dev.kraken.journal.kraken.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Involucro comune a tutte le risposte Kraken.
 * Attenzione: Kraken risponde HTTP 200 anche sugli errori applicativi,
 * che finiscono nell'array "error". Va sempre controllato.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KrakenEnvelope<T>(List<String> error, T result) {

    public boolean hasError() {
        return error != null && !error.isEmpty();
    }
}
