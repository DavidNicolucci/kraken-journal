package dev.kraken.journal.verifica.dto;

import dev.kraken.journal.verifica.Giudizio;

/**
 * I risultati di un gruppo di operazioni simulate: tutte, o quelle di una coppia.
 *
 * @param coppia null per il totale
 * @param mediaInR null senza operazioni chiuse
 * @param margineInR mezza ampiezza dell'intervallo al 95%; null sotto le due chiuse
 * @param sommaInR la somma dei risultati: moltiplicata per il rischio di una
 *        operazione da' il risultato in euro
 * @param percentualeVincenti quota di chiuse con risultato positivo
 */
public record Riepilogo(
        String coppia,
        int operazioni,
        int chiuse,
        int allObiettivo,
        int alloStop,
        int perTempo,
        Double mediaInR,
        Double margineInR,
        double sommaInR,
        double percentualeVincenti,
        Giudizio giudizio) {
}
