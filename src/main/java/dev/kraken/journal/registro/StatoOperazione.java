package dev.kraken.journal.registro;

/**
 * Stato di un'operazione registrata.
 *
 * CHIUSA_PER_TEMPO non e' un dettaglio burocratico: e' il time stop della
 * guida. Un'operazione che dopo quindici giorni non e' andata ne' allo stop ne'
 * all'obiettivo va chiusa comunque, perche' tiene occupati capitale e
 * attenzione senza dire niente. Tenerla come stato separato permette di sapere
 * quante volte succede, che e' un'informazione sul metodo.
 */
public enum StatoOperazione {

    APERTA,
    CHIUSA_ALLO_STOP,
    CHIUSA_ALL_OBIETTIVO,
    CHIUSA_PER_TEMPO;

    public boolean chiusa() {
        return this != APERTA;
    }
}
