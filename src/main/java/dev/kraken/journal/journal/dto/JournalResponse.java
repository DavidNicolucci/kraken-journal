package dev.kraken.journal.journal.dto;

/** Risposta dell'endpoint: metriche verificabili e il commento scritto dalle regole. */
public record JournalResponse(JournalStats stats, JournalReport report) {
}
