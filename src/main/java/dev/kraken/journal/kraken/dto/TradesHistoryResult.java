package dev.kraken.journal.kraken.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** La chiave della mappa e' il transaction id del trade. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TradesHistoryResult(Map<String, KrakenTrade> trades, int count) {
}
