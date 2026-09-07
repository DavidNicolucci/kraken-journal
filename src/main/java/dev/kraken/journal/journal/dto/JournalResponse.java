package dev.kraken.journal.journal.dto;

/** Risposta dell'endpoint: metriche verificabili + lettura del modello. */
public record JournalResponse(JournalStats stats, JournalReport report) {
}
