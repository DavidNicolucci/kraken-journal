package dev.kraken.journal.kraken;

import java.util.List;
import java.util.Set;

public class KrakenApiException extends RuntimeException {

    /**
     * Errori che dicono "non adesso", non "mai": limite di frequenza superato o
     * servizio sotto carico. Solo questi vale la pena ritentare; una coppia
     * sconosciuta resta sconosciuta anche fra dieci secondi.
     */
    private static final Set<String> TEMPORANEI = Set.of(
            "EAPI:Rate limit exceeded",
            "EGeneral:Too many requests",
            "EService:Unavailable",
            "EService:Busy",
            "EService:Throttled");

    private final transient List<String> krakenErrors;

    public KrakenApiException(String endpoint, List<String> krakenErrors) {
        super("Kraken ha risposto con errore su %s: %s".formatted(endpoint, krakenErrors));
        this.krakenErrors = krakenErrors;
    }

    public List<String> getKrakenErrors() {
        return krakenErrors;
    }

    /** Kraken a volte aggiunge un dettaglio dopo il codice: conta il prefisso. */
    public boolean temporanea() {
        return krakenErrors != null && krakenErrors.stream()
                .anyMatch(errore -> TEMPORANEI.stream().anyMatch(errore::startsWith));
    }
}
