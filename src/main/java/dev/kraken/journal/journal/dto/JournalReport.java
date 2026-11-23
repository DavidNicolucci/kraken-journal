package dev.kraken.journal.journal.dto;

import java.util.List;

/**
 * Il commento del journal, scritto da GeneratoreCommento. Deliberatamente NON
 * contiene raccomandazioni operative ne' previsioni di prezzo: solo lettura
 * del comportamento passato.
 */
public record JournalReport(
        String summary,
        List<String> observedPatterns,
        List<String> questionsToAskYourself,
        String dataCaveat
) {
}
