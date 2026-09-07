package dev.kraken.journal.kraken.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Singolo trade eseguito, come restituito da /0/private/TradesHistory. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KrakenTrade(
        @JsonProperty("ordertxid") String orderTxId,
        String pair,
        /** Unix time con frazione di secondo. */
        BigDecimal time,
        /** "buy" oppure "sell". */
        String type,
        @JsonProperty("ordertype") String orderType,
        BigDecimal price,
        /** Controvalore lordo nella valuta quotata. */
        BigDecimal cost,
        BigDecimal fee,
        BigDecimal vol,
        BigDecimal margin
) {
    public Instant timestamp() {
        long epochMillis = time.multiply(BigDecimal.valueOf(1000)).longValue();
        return Instant.ofEpochMilli(epochMillis);
    }

    public boolean isBuy() {
        return "buy".equalsIgnoreCase(type);
    }
}
