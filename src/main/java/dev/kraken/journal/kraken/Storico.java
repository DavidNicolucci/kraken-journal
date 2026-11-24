package dev.kraken.journal.kraken;

import java.util.List;

/**
 * Uno storico scaricato da Kraken, trade o ledger.
 *
 * @param completo false quando lo scaricamento si e' fermato al tetto di
 *        pagine: restano le voci piu' recenti, e le vendite di monete comprate
 *        prima risultano senza costo di carico. Chi mostra i numeri deve dirlo.
 */
public record Storico<T>(List<T> voci, boolean completo) {
}
