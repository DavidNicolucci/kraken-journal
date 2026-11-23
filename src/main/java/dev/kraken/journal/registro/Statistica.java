package dev.kraken.journal.registro;

import java.util.Arrays;

/**
 * Media e margine d'errore dei risultati in R.
 *
 * Perche' il margine: con 15 operazioni e risultati da -1 R a +2 R, la media
 * si sposta di quasi mezza R a ogni operazione in piu'. Dire "sopra zero" senza
 * dire di quanto puo' sbagliare la media e' il modo di vedere un vantaggio che
 * non c'e'.
 */
final class Statistica {

    private static final double Z_95 = 1.959964;

    private Statistica() {
    }

    static double media(double[] valori) {
        return Arrays.stream(valori).average().orElse(0);
    }

    /**
     * Mezza ampiezza dell'intervallo di confidenza al 95% sulla media, con la t
     * di Student: con pochi casi la normale lo restringerebbe troppo. Il
     * quantile e' l'espansione di Cornish-Fisher, che a 14 gradi di liberta'
     * da' 2,144 contro il 2,145 delle tavole.
     *
     * @return null sotto i due valori: con uno solo la dispersione non esiste.
     */
    static Double margine95(double[] valori) {
        int n = valori.length;
        if (n < 2) {
            return null;
        }
        double media = media(valori);
        double scarti = Arrays.stream(valori).map(v -> (v - media) * (v - media)).sum();
        double deviazione = Math.sqrt(scarti / (n - 1));
        return quantileT(n - 1) * deviazione / Math.sqrt(n);
    }

    static double quantileT(int gradi) {
        double z = Z_95;
        double z3 = z * z * z;
        double z5 = z3 * z * z;
        return z + (z3 + z) / (4.0 * gradi) + (5 * z5 + 16 * z3 + 3 * z) / (96.0 * gradi * gradi);
    }
}
