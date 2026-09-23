package dev.kraken.journal.scansione.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Fotografia tecnica di una coppia, calcolata sull'ultima candela CHIUSA.
 *
 * @param istanteUltimaCandela l'istante della candela su cui sono calcolati
 *        questi numeri. E' in uscita apposta: se il front-end mostra una data
 *        di ieri, si vede subito che non e' un dato in tempo reale.
 * @param mediaMobileInSalita confronto fra la media di oggi e quella della
 *        candela precedente: e' il "trend" della guida, ridotto a un booleano.
 */
public record Indicatori(
        Instant istanteUltimaCandela,
        double chiusura,
        double mediaMobile20,
        boolean mediaMobileInSalita,
        double rsi14,
        double atr14,
        double volumeMedio20,
        double massimo30,
        double minimo10,
        double minimoPerStop) {

    /** Distanza percentuale della chiusura dalla media mobile. */
    @JsonProperty("distanzaDallaMedia")
    public double distanzaDallaMedia() {
        return (chiusura / mediaMobile20 - 1) * 100;
    }

    /** ATR in percentuale del prezzo: serve a confrontare coppie diverse. */
    @JsonProperty("atrPercentuale")
    public double atrPercentuale() {
        return atr14 / chiusura * 100;
    }
}
