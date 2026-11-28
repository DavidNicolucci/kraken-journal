package dev.kraken.journal.verifica;

/**
 * Cosa dicono le operazioni simulate sul metodo, con lo stesso criterio del
 * registro: la media in R conta solo tolto il margine d'errore.
 */
public enum Giudizio {

    /** Media positiva anche togliendo il margine: sul passato il metodo ha guadagnato. */
    POSITIVO,
    /** Media negativa anche aggiungendo il margine: sul passato il metodo ha perso. */
    NEGATIVO,
    /** Lo zero sta dentro il margine: guadagno e perdita sono entrambi possibili. */
    INCERTO,
    /** Meno operazioni chiuse di scansione.registrazioni-per-giudizio. */
    POCHI_CASI
}
