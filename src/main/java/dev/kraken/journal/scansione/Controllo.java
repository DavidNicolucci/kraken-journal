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
    /**
     * Il trigger provvisorio della guida. Prima non c'era: il lavoro delle sette
     * registrava come BREAKOUT_20_GIORNI ogni coppia che passava i filtri, e il
     * registro misurava un'ipotesi diversa da quella scritta sull'etichetta.
     */
    BREAKOUT("chiusura sopra il massimo dei giorni precedenti, con volume sopra la media"),
    DISTANZA_DALLA_MEDIA("quanto il prezzo e' gia' scappato dalla media"),
    /**
     * I controlli sopra guardano la chiusura di ieri, l'entrata e' il prezzo
     * di stamattina. Se nella notte il prezzo e' tornato sotto la rottura o
     * sotto la media, o e' scappato troppo, il segnale di ieri non vale piu'.
     */
    PREZZO_ATTUALE("il prezzo di stamattina conferma la chiusura: sopra media e rottura, non troppo lontano"),
    AMPIEZZA_STOP("lo stop cade nella finestra compatibile con le commissioni");

    private final String descrizione;

    Controllo(String descrizione) {
        this.descrizione = descrizione;
    }

    public String descrizione() {
        return descrizione;
    }
}
