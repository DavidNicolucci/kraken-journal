package dev.kraken.journal.simulatore;

/**
 * L'RSI a 14 diviso in tre fasce. Tre e non di piu': con 9 celle (3 regimi
 * per 3 fasce) un anno di dieci coppie da' qualche centinaio di casi per
 * cella; con fasce piu' fini la frequenza di ogni cella sarebbe rumore.
 */
public enum FasciaRsi {

    BASSA,
    CENTRALE,
    ALTA;

    private static final double SOGLIA_BASSA = 40;
    private static final double SOGLIA_ALTA = 60;

    static FasciaRsi di(double rsi) {
        return rsi < SOGLIA_BASSA ? BASSA : rsi > SOGLIA_ALTA ? ALTA : CENTRALE;
    }
}
