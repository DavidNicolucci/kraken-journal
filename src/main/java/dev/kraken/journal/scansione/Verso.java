package dev.kraken.journal.scansione;

/**
 * Direzione dell'operazione.
 *
 * Serve al calcolatore, che ha sempre gestito anche la vendita allo scoperto.
 * La scansione invece produce solo acquisti: il metodo della guida e' costruito
 * su trend al rialzo e su spot senza leva, dove lo scoperto non esiste.
 */
public enum Verso {

    ACQUISTO,
    VENDITA_ALLO_SCOPERTO;

    /** Nello scoperto il guadagno arriva dalla discesa: i segni si invertono. */
    public double variazioneFavorevole(double entrata, double uscita) {
        return this == ACQUISTO ? uscita - entrata : entrata - uscita;
    }
}
