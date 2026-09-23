package dev.kraken.journal.scansione;

/**
 * I controlli della checklist, nell'ordine in cui vanno letti.
 *
 * Manca volutamente il settimo della guida, "l'ho scritto sul registro":
 * non e' verificabile dai dati di mercato e verra' aggiunto insieme al
 * registro. Inventarlo qui come sempre superato sarebbe peggio che ometterlo.
 */
public enum Controllo {

    LIQUIDITA("controvalore scambiato nelle ultime 24 ore"),
    TREND("media mobile a 20 in salita e prezzo sopra la media"),
    MOMENTO("RSI a 14 nella fascia utile"),
    DISTANZA_DALLA_MEDIA("quanto il prezzo e' gia' scappato dalla media"),
    AMPIEZZA_STOP("lo stop cade nella finestra compatibile con le commissioni"),
    RESISTENZA_INTERMEDIA("il massimo a 30 giorni non sta fra entrata e obiettivo");

    private final String descrizione;

    Controllo(String descrizione) {
        this.descrizione = descrizione;
    }

    public String descrizione() {
        return descrizione;
    }
}
