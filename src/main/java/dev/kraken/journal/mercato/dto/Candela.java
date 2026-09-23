package dev.kraken.journal.mercato.dto;

import java.time.Instant;

/**
 * Una candela OHLC.
 *
 * Tipi primitivi e non BigDecimal: qui non si fa contabilita' ma statistica di
 * prezzo, e un double regge quindici cifre significative, piu' che sufficienti
 * anche per i token sotto il centesimo di millesimo. Il BigDecimal resta dove
 * era gia' in uso, cioe' sul P&L del journal.
 *
 * @param prezzoMedio il VWAP della candela, usato per stimare il controvalore
 *        scambiato: volume x prezzoMedio e' molto piu' vicino al vero che
 *        volume x chiusura.
 */
public record Candela(
        Instant istante,
        double apertura,
        double massimo,
        double minimo,
        double chiusura,
        double prezzoMedio,
        double volume) {

    /** Controvalore scambiato nella candela, nella valuta di quotazione. */
    public double controvalore() {
        return volume * prezzoMedio;
    }

    /** Escursione della candela, senza considerare il salto dalla precedente. */
    public double escursione() {
        return massimo - minimo;
    }
}
