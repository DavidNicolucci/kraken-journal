package dev.kraken.journal.registro;

/**
 * Stato di un'operazione registrata.
 *
 * CHIUSA_PER_TEMPO non e' un dettaglio burocratico: e' il time stop della
 * guida. Un'operazione che dopo quindici giorni non e' andata ne' allo stop ne'
 * all'obiettivo va chiusa comunque, perche' tiene occupati capitale e
 * attenzione senza dire niente. Tenerla come stato separato permette di sapere
 * quante volte succede, che e' un'informazione sul metodo.
 *
 * CHIUSA_A_MANO e NON_ESEGUITA esistono solo per le operazioni reali, che si
 * chiudono dagli ordini veri: un'uscita fra stop e obiettivo e' una scelta
 * fatta durante l'operazione, e una registrazione senza nessun acquisto nei
 * giorni dopo e' un'operazione mai aperta. Quest'ultima non ha un esito: non
 * conta fra le chiuse e non occupa piu' un posto fra le posizioni.
 */
public enum StatoOperazione {

    APERTA,
    CHIUSA_ALLO_STOP,
    CHIUSA_ALL_OBIETTIVO,
    CHIUSA_PER_TEMPO,
    CHIUSA_A_MANO,
    NON_ESEGUITA;

    /** Ha un esito: e' finita allo stop, all'obiettivo, per tempo o a mano. */
    public boolean chiusa() {
        return this != APERTA && this != NON_ESEGUITA;
    }

    /** Lo stato di un'uscita vera, dal prezzo medio a cui si e' venduto. */
    public static StatoOperazione dallUscita(double prezzoUscita, double stop, double obiettivo) {
        return prezzoUscita >= obiettivo ? CHIUSA_ALL_OBIETTIVO
                : prezzoUscita <= stop ? CHIUSA_ALLO_STOP
                : CHIUSA_A_MANO;
    }
}
