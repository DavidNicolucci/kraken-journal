package dev.kraken.journal.simulatore;

/**
 * Che mercato c'e' adesso su una coppia. Descrive, non prevede: un BULL dice
 * che le ultime settimane sono salite, non che le prossime saliranno. Quanto
 * spesso dopo un BULL si e' saliti davvero lo misura il simulatore.
 *
 * Tre condizioni insieme e non una sola, perche' ognuna da sola cambia segno
 * per un giorno di rumore: il prezzo rispetto alla media a 50, la pendenza
 * della media a 20 (la stessa del controllo TREND della scansione) e il
 * rendimento degli ultimi 60 giorni.
 */
public enum Regime {

    BULL,
    BEAR,
    LATERALE;

    static Regime di(double chiusura, double media50, boolean mediaInSalita, double momento60) {
        if (chiusura > media50 && mediaInSalita && momento60 > 0) {
            return BULL;
        }
        if (chiusura < media50 && !mediaInSalita && momento60 < 0) {
            return BEAR;
        }
        return LATERALE;
    }
}
