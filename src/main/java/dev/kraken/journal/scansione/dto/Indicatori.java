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
 * @param massimo30Precedente il massimo dei 30 giorni PRIMA dell'ultima
 *        candela. Esclude la candela di rottura: dopo un breakout il suo
 *        massimo sta quasi sempre sopra il prezzo di stamattina, e contarlo
 *        come resistenza boccerebbe proprio i setup che il trigger cerca.
 * @param massimoBreakout il massimo dei {@code giorni-breakout} giorni prima
 *        dell'ultima candela: la chiusura deve superarlo.
 * @param controvaloreUltima controvalore scambiato nell'ultima candela chiusa.
 * @param controvaloreMedioBreakout controvalore medio degli stessi giorni del
 *        massimo, per il confronto sul volume della rottura.
 */
public record Indicatori(
        Instant istanteUltimaCandela,
        double chiusura,
        double mediaMobile20,
        boolean mediaMobileInSalita,
        double rsi14,
        double atr14,
        double volumeMedio20,
        double massimo30Precedente,
        double minimo10,
        double minimoPerStop,
        double massimoBreakout,
        double controvaloreUltima,
        double controvaloreMedioBreakout) {

    /** Distanza percentuale della chiusura dalla media mobile. */
    @JsonProperty("distanzaDallaMedia")
    public double distanzaDallaMedia() {
        return (chiusura / mediaMobile20 - 1) * 100;
    }

    /** Quante volte il controvalore della rottura supera la media dei giorni prima. */
    @JsonProperty("multiploVolume")
    public double multiploVolume() {
        return controvaloreMedioBreakout <= 0 ? 0 : controvaloreUltima / controvaloreMedioBreakout;
    }

    /** ATR in percentuale del prezzo: serve a confrontare coppie diverse. */
    @JsonProperty("atrPercentuale")
    public double atrPercentuale() {
        return atr14 / chiusura * 100;
    }
}
