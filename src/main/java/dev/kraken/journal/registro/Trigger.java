package dev.kraken.journal.registro;

/**
 * Da quale regola nasce l'operazione.
 *
 * Serve a una cosa sola, ed e' il motivo per cui il registro esiste: poter
 * separare i risultati per regola. Senza questo campo si finisce con un mucchio
 * unico di operazioni che non dice quale ipotesi stia funzionando.
 *
 * MANUALE non e' una scappatoia, e' una misura: se le operazioni manuali sono
 * la maggioranza, non si sta testando nessuna regola.
 */
public enum Trigger {

    /** Le soglie stanno in scansione.giorni-breakout e scansione.multiplo-volume-breakout. */
    BREAKOUT_20_GIORNI("chiusura sopra il massimo dei giorni precedenti, con volume sopra la media"),
    PULLBACK_MEDIA("ritorno sulla media a 20 in trend, con candela di ripresa"),
    MANUALE("decisione presa fuori da una regola scritta");

    private final String descrizione;

    Trigger(String descrizione) {
        this.descrizione = descrizione;
    }

    public String descrizione() {
        return descrizione;
    }
}
