package dev.kraken.journal.simulatore;

/**
 * L'esito della verifica sul passato: il modello stima meglio della semplice
 * frequenza di rialzo? Si guarda il caso peggiore, cioe' il vantaggio meno il
 * margine d'errore, come per il risultato medio del registro.
 */
public enum Verdetto {

    /** Batte la frequenza di base anche togliendo il margine d'errore. */
    VANTAGGIO,
    /** La differenza dalla frequenza di base sta dentro il margine: e' rumore. */
    NESSUN_VANTAGGIO,
    /** Fa peggio della frequenza di base anche nel caso migliore. */
    PEGGIORE_DELLA_BASE,
    /** Troppo pochi giorni per dire qualcosa. */
    DATI_INSUFFICIENTI
}
