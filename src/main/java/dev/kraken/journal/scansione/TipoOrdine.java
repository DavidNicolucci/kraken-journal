package dev.kraken.journal.scansione;

/**
 * Tipo di ordine e commissione associata, al livello attuale su Kraken.
 *
 * Enum e non due costanti: lo 0,40 e lo 0,80 ricorrono nel calcolo della
 * quantita', in quello del pareggio e in quello del rapporto rischio
 * rendimento. Tenerli legati al loro significato evita di ritrovarsi il numero
 * giusto nel posto sbagliato.
 */
public enum TipoOrdine {

    LIMITE(0.0040),
    MERCATO(0.0080);

    private final double commissione;

    TipoOrdine(double commissione) {
        this.commissione = commissione;
    }

    /** Commissione su un singolo lato dell'operazione, in frazione di uno. */
    public double commissione() {
        return commissione;
    }
}
