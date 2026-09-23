package dev.kraken.journal.mercato;

/**
 * Intervalli delle candele, con il valore che Kraken si aspetta in minuti.
 * Enum e non costanti sparse: il valore "1440" ricorre nel client, nella cache
 * e nei test.
 */
public enum Intervallo {

    ORA(60),
    QUATTRO_ORE(240),
    GIORNALIERO(1440);

    private final int minuti;

    Intervallo(int minuti) {
        this.minuti = minuti;
    }

    public int minuti() {
        return minuti;
    }
}
