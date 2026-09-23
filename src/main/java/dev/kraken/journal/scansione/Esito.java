package dev.kraken.journal.scansione;

/** Esito di un singolo controllo della checklist. */
public enum Esito {

    SUPERATO,
    BOCCIATO;

    public static Esito di(boolean condizione) {
        return condizione ? SUPERATO : BOCCIATO;
    }
}
