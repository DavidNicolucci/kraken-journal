package dev.kraken.journal.journal.dto;

import java.math.BigDecimal;

/**
 * I totali del periodo per una valuta di quotazione. Sommare euro e dollari
 * da' un numero che non e' ne' l'uno ne' l'altro: qui ogni valuta ha i suoi,
 * e sono esatti.
 *
 * @param volumeMaker volume eseguito con l'ordine in coda sul book.
 * @param volumeTaker volume eseguito subito, alla tariffa piu' alta.
 * @param volumeApp volume passato dall'app, che ha una tariffa sua.
 * @param risultato somma dei risultati delle vendite abbinate a un acquisto
 *        nella stessa valuta. Null se nel periodo non ce n'e' nessuna.
 * @param coppieIncomplete coppie con vendite che non trovano il loro acquisto,
 *        o che lo trovano in un'altra valuta: per quelle il risultato e'
 *        parziale o assente.
 */
public record TotaliValuta(
        String valuta,
        int ordini,
        BigDecimal volume,
        BigDecimal commissioni,
        BigDecimal commissioniPercentuale,
        BigDecimal volumeMaker,
        BigDecimal volumeTaker,
        BigDecimal commissioniTaker,
        BigDecimal volumeApp,
        BigDecimal commissioniApp,
        BigDecimal risultato,
        int coppieIncomplete) {
}
