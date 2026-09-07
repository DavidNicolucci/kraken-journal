package dev.kraken.journal.journal.dto;

import java.util.List;

/**
 * Output del modello. Deliberatamente NON contiene raccomandazioni operative
 * ne' previsioni di prezzo: solo lettura del comportamento passato.
 */
public record JournalReport(
        String summary,
        List<String> observedPatterns,
        List<String> questionsToAskYourself,
        String dataCaveat
) {
}
