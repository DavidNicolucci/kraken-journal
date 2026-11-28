package dev.kraken.journal.scansione;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Stream;

import dev.kraken.journal.scansione.dto.EsitoControllo;
import dev.kraken.journal.scansione.dto.Indicatori;
import dev.kraken.journal.scansione.dto.Piano;

/**
 * La checklist della guida: gli stessi controlli per la scansione di oggi e
 * per la verifica storica. Sta in un posto solo apposta: se la verifica sul
 * passato avesse una copia sua dei controlli, al primo ritocco misurerebbe un
 * metodo diverso da quello che si usa.
 *
 * Funzione pura: il controvalore e l'ultimo prezzo arrivano come numeri, cosi'
 * la scansione li prende dal ticker e la verifica storica dalle candele.
 *
 * Restituisce solo i controlli in scansione.controlli-attivi: uno spento non
 * boccia e non compare, cosi' "superati su totale" conta le regole in vigore.
 */
public final class Checklist {

    private Checklist() {
    }

    /**
     * Le soglie si confrontano con il valore che la pagina mostra, arrotondato
     * come lei: uno stop al 4,96% si legge 5,0%, e bocciarlo contro "tra 5% e 10%"
     * sembrava un errore. I prezzi restano interi, con sei cifre significative
     * a video: con quattro decimali fissi PEPE diventava zero.
     */
    public static List<EsitoControllo> controlla(double controvalore24h, double ultimo, Indicatori i, Piano piano,
                                                 ProprietaScansione proprieta) {
        double controvalore = arrotondato(controvalore24h, 0);
        double multiploVolume = arrotondato(i.multiploVolume(), 1);
        double distanza = arrotondato(Math.abs(i.distanzaDallaMedia()), 1);
        double distanzaAttuale = arrotondato(Math.abs(ultimo / i.mediaMobile20() - 1) * 100, 1);
        double ampiezzaStop = arrotondato(piano.ampiezzaStop(), 1);
        return Stream.of(
                EsitoControllo.di(Controllo.LIQUIDITA,
                        controvalore >= proprieta.volumeMinimo(),
                        arrotonda(controvalore, 0),
                        ">= " + arrotonda(proprieta.volumeMinimo(), 0)),
                EsitoControllo.di(Controllo.TREND,
                        i.mediaMobileInSalita() && i.chiusura() > i.mediaMobile20(),
                        i.mediaMobileInSalita() ? "media in salita" : "media in discesa",
                        "media in salita e prezzo sopra"),
                EsitoControllo.di(Controllo.BREAKOUT,
                        i.chiusura() > i.massimoBreakout()
                                && multiploVolume >= proprieta.multiploVolumeBreakout(),
                        "chiusura " + prezzo(i.chiusura()) + ", volume " + arrotonda(multiploVolume, 1) + "x",
                        "sopra " + prezzo(i.massimoBreakout()) + " con volume >= "
                                + arrotonda(proprieta.multiploVolumeBreakout(), 1) + "x"),
                // Valore assoluto: la distanza conta in entrambe le direzioni.
                // Col segno, un prezzo molto SOTTO la media superava il controllo
                // senza che volesse dire niente. Indicatori resta col segno:
                // serve alla colonna della tabella e al riepilogo giornaliero.
                EsitoControllo.di(Controllo.DISTANZA_DALLA_MEDIA,
                        distanza <= proprieta.distanzaMassimaDallaMedia(),
                        arrotonda(distanza, 1) + "%",
                        "<= " + arrotonda(proprieta.distanzaMassimaDallaMedia(), 0) + "%"),
                EsitoControllo.di(Controllo.PREZZO_ATTUALE,
                        ultimo > i.mediaMobile20() && ultimo > i.massimoBreakout()
                                && distanzaAttuale <= proprieta.distanzaMassimaDallaMedia(),
                        prezzo(ultimo) + ", " + arrotonda(distanzaAttuale, 1) + "% dalla media",
                        "sopra " + prezzo(Math.max(i.mediaMobile20(), i.massimoBreakout())) + ", entro "
                                + arrotonda(proprieta.distanzaMassimaDallaMedia(), 0) + "%"),
                EsitoControllo.di(Controllo.AMPIEZZA_STOP,
                        ampiezzaStop >= proprieta.ampiezzaStopMinima()
                                && ampiezzaStop <= proprieta.ampiezzaStopMassima(),
                        arrotonda(ampiezzaStop, 1) + "%",
                        "tra " + arrotonda(proprieta.ampiezzaStopMinima(), 0) + "% e "
                                + arrotonda(proprieta.ampiezzaStopMassima(), 0) + "%"))
                .filter(esito -> proprieta.controlliAttivi().contains(esito.controllo()))
                .toList();
    }

    /**
     * Locale esplicito: senza, la virgola o il punto dipenderebbero dalla
     * macchina su cui gira il servizio, e lo stesso dato uscirebbe diverso in
     * sviluppo e in esecuzione.
     */
    private static String arrotonda(double valore, int decimali) {
        return String.format(java.util.Locale.ITALY, "%." + decimali + "f", valore);
    }

    private static double arrotondato(double valore, int decimali) {
        return BigDecimal.valueOf(valore).setScale(decimali, RoundingMode.HALF_UP).doubleValue();
    }

    /** Sei cifre significative con la virgola: 55409,8 e 0,0000123456 si leggono tutti e due. */
    private static String prezzo(double valore) {
        return BigDecimal.valueOf(valore).round(new MathContext(6)).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
