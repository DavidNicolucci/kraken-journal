package dev.kraken.journal.kraken.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** La chiave della mappa e' l'id della riga del ledger. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgersResult(Map<String, KrakenLedgerEntry> ledger) {
}
