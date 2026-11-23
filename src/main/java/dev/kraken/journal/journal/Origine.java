package dev.kraken.journal.journal;

/**
 * Da dove e' passata un'operazione.
 *
 * Non e' un dettaglio: le due strade costano in modo diverso. Su Kraken Pro si
 * paga la tariffa maker o taker; dall'app si paga una commissione piu' alta e
 * uno spread che non compare da nessuna parte, perche' sta dentro il prezzo.
 */
public enum Origine {

    /** Ordine su Kraken Pro: compare in TradesHistory. */
    PRO,

    /** Acquisto o vendita dall'app Kraken: compare solo nel ledger. */
    APP
}
