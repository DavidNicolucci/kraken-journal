package dev.kraken.journal.simulatore;

/**
 * Cosa si puo' dire della direzione a fine orizzonte. Decide l'intervallo di
 * confidenza, non la stima: un 58% con intervallo 41-73% e' INCERTO, perche'
 * il 50% ci sta dentro.
 */
public enum Direzione {

    RIALZO,
    RIBASSO,
    INCERTO;

    private static final double META = 0.5;

    static Direzione di(double minimo, double massimo) {
        return minimo > META ? RIALZO : massimo < META ? RIBASSO : INCERTO;
    }
}
