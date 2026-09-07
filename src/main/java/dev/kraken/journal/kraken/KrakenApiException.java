package dev.kraken.journal.kraken;

import java.util.List;

public class KrakenApiException extends RuntimeException {

    private final transient List<String> krakenErrors;

    public KrakenApiException(String endpoint, List<String> krakenErrors) {
        super("Kraken ha risposto con errore su %s: %s".formatted(endpoint, krakenErrors));
        this.krakenErrors = krakenErrors;
    }

    public List<String> getKrakenErrors() {
        return krakenErrors;
    }
}
