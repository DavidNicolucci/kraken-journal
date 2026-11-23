package dev.kraken.journal.kraken.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Una riga del ledger, come restituita da /0/private/Ledgers.
 *
 * Il ledger registra ogni variazione di saldo, non solo gli ordini: e' l'unico
 * posto in cui compaiono gli acquisti fatti dall'app Kraken (righe "spend" e
 * "receive"), i depositi e le ricompense. TradesHistory non li vede.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KrakenLedgerEntry(
        /** Uguale per le righe nate dalla stessa operazione: lega la spesa al suo incasso. */
        String refid,
        /** Unix time con frazione di secondo. */
        BigDecimal time,
        String type,
        String subtype,
        String asset,
        /** Negativo quando esce dal conto, positivo quando entra. Commissione esclusa. */
        BigDecimal amount,
        /** Nella stessa moneta della riga. */
        BigDecimal fee
) {
    public KrakenLedgerEntry {
        amount = amount == null ? BigDecimal.ZERO : amount;
        fee = fee == null ? BigDecimal.ZERO : fee;
    }

    public Instant timestamp() {
        return Instant.ofEpochMilli(time.movePointRight(3).longValue());
    }
}
