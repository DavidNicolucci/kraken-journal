package dev.kraken.journal.registro;

import java.util.Arrays;

/**
 * Media e margine d'errore dei risultati in R, e intervallo di una proporzione.
 *
 * Perche' il margine: con 15 operazioni e risultati da -1 R a +2 R, la media
 * si sposta di quasi mezza R a ogni operazione in piu'. Dire "sopra zero" senza
 * dire di quanto puo' sbagliare la media e' il modo di vedere un vantaggio che
 * non c'e'.
 */
public final class Statistica {

    private static final double Z_95 = 1.959964;

    /**
     * Quantile 0,975 della t di Student da 1 a 30 gradi di liberta', dalle
     * tavole. Cornish-Fisher qui sbaglia proprio dove conta: con 1 grado da'
     * 7,16 invece di 12,71, con 2 il 10% in meno, e il margine delle prime
     * operazioni sembrava piu' stretto di quello che e'.
     */
    private static final double[] T_95 = {
            12.7062, 4.3027, 3.1824, 2.7764, 2.5706, 2.4469, 2.3646, 2.3060, 2.2622, 2.2281,
            2.2010, 2.1788, 2.1604, 2.1448, 2.1314, 2.1199, 2.1098, 2.1009, 2.0930, 2.0860,
            2.0796, 2.0739, 2.0687, 2.0639, 2.0595, 2.0555, 2.0518, 2.0484, 2.0452, 2.0423};

    private Statistica() {
    }

    public static double media(double[] valori) {
        return Arrays.stream(valori).average().orElse(0);
    }

    /**
     * Mezza ampiezza dell'intervallo di confidenza al 95% sulla media, con la t
     * di Student: con pochi casi la normale lo restringerebbe troppo.
     *
     * @return null sotto i due valori: con uno solo la dispersione non esiste.
     */
    public static Double margine95(double[] valori) {
        int n = valori.length;
        if (n < 2) {
            return null;
        }
        double media = media(valori);
        double scarti = Arrays.stream(valori).map(v -> (v - media) * (v - media)).sum();
        double deviazione = Math.sqrt(scarti / (n - 1));
        return quantileT(n - 1) * deviazione / Math.sqrt(n);
    }

    /**
     * Intervallo di Wilson al 95% di una proporzione.
     *
     * Wilson e non la formula da manuale p +- 1,96 radice(p(1-p)/n): quella con
     * pochi casi esce dallo zero e dal cento, e con 10 successi su 10 dice
     * "100% senza margine". Wilson resta dentro la scala e con pochi casi si
     * allarga, che e' proprio quello che deve fare.
     *
     * @param proporzione la frequenza osservata, fra 0 e 1
     * @param casi su quanti casi indipendenti contarla: puo' essere meno dei
     *        casi da cui e' calcolata la proporzione, quando quelli si sovrappongono
     * @return {minimo, massimo}; {0, 1} senza casi, perche' allora non si sa niente
     */
    public static double[] wilson95(double proporzione, int casi) {
        if (casi <= 0) {
            return new double[] {0, 1};
        }
        double z2 = Z_95 * Z_95;
        double denominatore = 1 + z2 / casi;
        double centro = (proporzione + z2 / (2.0 * casi)) / denominatore;
        double meta = Z_95 * Math.sqrt(proporzione * (1 - proporzione) / casi + z2 / (4.0 * casi * casi)) / denominatore;
        return new double[] {Math.max(0, centro - meta), Math.min(1, centro + meta)};
    }

    /** Dalle tavole fino a 30 gradi; oltre, Cornish-Fisher, che da 31 in su sbaglia meno di un millesimo. */
    static double quantileT(int gradi) {
        if (gradi <= T_95.length) {
            return T_95[gradi - 1];
        }
        double z = Z_95;
        double z3 = z * z * z;
        double z5 = z3 * z * z;
        return z + (z3 + z) / (4.0 * gradi) + (5 * z5 + 16 * z3 + 3 * z) / (96.0 * gradi * gradi);
    }
}
