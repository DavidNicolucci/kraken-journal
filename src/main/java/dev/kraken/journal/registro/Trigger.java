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

    BREAKOUT_20_GIORNI("chiusura sopra il massimo a 20 giorni con volume >= 1,5x la media"),
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
