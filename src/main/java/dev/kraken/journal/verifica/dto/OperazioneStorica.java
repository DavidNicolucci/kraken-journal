package dev.kraken.journal.verifica.dto;

import java.time.Instant;

import dev.kraken.journal.registro.StatoOperazione;

/**
 * Un'operazione che il metodo avrebbe registrato nel passato.
 *
 * @param segnale la candela chiusa su cui tutti i controlli erano superati
 * @param entrata l'apertura della candela dopo: e' il prezzo del mattino dopo
 *        la rottura, quello a cui gira il lavoro giornaliero
 * @param stato APERTA se alla fine dei dati non era ancora chiusa
 * @param risultatoInR gia' al netto delle commissioni; null se aperta
 */
public record OperazioneStorica(
        String coppia,
        Instant segnale,
        double entrata,
        double stop,
        double obiettivo,
        double rapporto,
        StatoOperazione stato,
        Instant chiusura,
        Double prezzoUscita,
        Double risultatoInR) {
}
