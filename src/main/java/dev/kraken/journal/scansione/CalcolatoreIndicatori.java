package dev.kraken.journal.scansione;

import java.util.List;

import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.scansione.dto.Indicatori;

/**
 * Indicatori tecnici. Funzioni pure e statiche: nessuno stato, quindi
 * testabili senza contesto Spring.
 *
 * Due scelte che cambiano i numeri e che e' meglio conoscere prima di leggerli:
 *
 * 1. RSI con lo smoothing di Wilder, non con media semplice. Non sono la stessa
 *    cosa e la differenza non e' marginale: sulle stesse venti chiusure di
 *    HYPE/EUR Wilder dava 60,7 e la media semplice 48,2. Wilder e' quello che
 *    disegnano le piattaforme, quindi e' quello che serve per confrontare i
 *    numeri con il grafico.
 *
 * 2. ATR sul true range vero, cioe' includendo il salto rispetto alla chiusura
 *    precedente: max(massimo-minimo, |massimo-chiusuraPrec|, |minimo-chiusuraPrec|).
 *    Usare solo massimo-minimo sottostima la volatilita' proprio nei giorni di
 *    gap, che sono quelli in cui lo stop viene saltato.
 *
 * Tutti i metodi assumono candele CHIUSE e in ordine cronologico, come le
 * restituisce MercatoClient.
 */
public final class CalcolatoreIndicatori {

    private static final int PERIODO_MEDIA = 20;
    private static final int PERIODO_RSI = 14;
    private static final int PERIODO_ATR = 14;
    /** Sotto questo numero di candele l'RSI di Wilder non e' ancora a regime. */
    private static final int CANDELE_MINIME = 60;

    private CalcolatoreIndicatori() {
    }

    /**
     * Tutti gli indicatori che servono alla scansione, in un passaggio solo.
     *
     * @param giorniMinimoStop su quante candele cercare il minimo che fara' da
     *        stop. Arriva dalla configurazione e non e' fissato qui: se il
     *        numero vivesse in due posti, cambiarne uno solo darebbe uno stop
     *        diverso da quello dichiarato nella guida.
     */
    public static Indicatori calcola(List<Candela> candele, int giorniMinimoStop) {
        verifica(candele, CANDELE_MINIME);
        double mediaMobile20 = mediaMobile(candele, PERIODO_MEDIA);
        double mediaMobilePrecedente = mediaMobile(candele.subList(0, candele.size() - 1), PERIODO_MEDIA);
        Candela ultima = candele.get(candele.size() - 1);

        return new Indicatori(
                ultima.istante(),
                ultima.chiusura(),
                mediaMobile20,
                mediaMobile20 > mediaMobilePrecedente,
                rsi(candele, PERIODO_RSI),
                atr(candele, PERIODO_ATR),
                volumeMedio(candele, PERIODO_MEDIA),
                massimo(candele, 30),
                minimo(candele, 10),
                minimo(candele, giorniMinimoStop));
    }

    /** Media semplice delle ultime {@code periodo} chiusure. */
    public static double mediaMobile(List<Candela> candele, int periodo) {
        return ultime(candele, periodo).stream().mapToDouble(Candela::chiusura).average().orElseThrow();
    }

    /** Controvalore medio scambiato nelle ultime {@code periodo} candele. */
    public static double volumeMedio(List<Candela> candele, int periodo) {
        return ultime(candele, periodo).stream().mapToDouble(Candela::controvalore).average().orElseThrow();
    }

    public static double massimo(List<Candela> candele, int periodo) {
        return ultime(candele, periodo).stream().mapToDouble(Candela::massimo).max().orElseThrow();
    }

    public static double minimo(List<Candela> candele, int periodo) {
        return ultime(candele, periodo).stream().mapToDouble(Candela::minimo).min().orElseThrow();
    }

    /**
     * RSI di Wilder su tutta la serie disponibile: piu' storia c'e', piu' il
     * valore e' stabile, perche' lo smoothing ha memoria lunga.
     */
    public static double rsi(List<Candela> candele, int periodo) {
        verifica(candele, periodo + 1);
        double guadagno = 0;
        double perdita = 0;

        for (int i = 1; i <= periodo; i++) {
            double variazione = candele.get(i).chiusura() - candele.get(i - 1).chiusura();
            guadagno += Math.max(variazione, 0);
            perdita += Math.max(-variazione, 0);
        }
        guadagno /= periodo;
        perdita /= periodo;

        for (int i = periodo + 1; i < candele.size(); i++) {
            double variazione = candele.get(i).chiusura() - candele.get(i - 1).chiusura();
            guadagno = (guadagno * (periodo - 1) + Math.max(variazione, 0)) / periodo;
            perdita = (perdita * (periodo - 1) + Math.max(-variazione, 0)) / periodo;
        }
        return perdita == 0 ? 100 : 100 - 100 / (1 + guadagno / perdita);
    }

    /** ATR di Wilder sul true range. */
    public static double atr(List<Candela> candele, int periodo) {
        verifica(candele, periodo + 1);
        double atr = 0;
        for (int i = 1; i <= periodo; i++) {
            atr += trueRange(candele.get(i), candele.get(i - 1));
        }
        atr /= periodo;

        for (int i = periodo + 1; i < candele.size(); i++) {
            atr = (atr * (periodo - 1) + trueRange(candele.get(i), candele.get(i - 1))) / periodo;
        }
        return atr;
    }

    private static double trueRange(Candela candela, Candela precedente) {
        return Math.max(candela.escursione(),
                Math.max(Math.abs(candela.massimo() - precedente.chiusura()),
                         Math.abs(candela.minimo() - precedente.chiusura())));
    }

    private static List<Candela> ultime(List<Candela> candele, int periodo) {
        verifica(candele, periodo);
        return candele.subList(candele.size() - periodo, candele.size());
    }

    private static void verifica(List<Candela> candele, int minime) {
        if (candele == null || candele.size() < minime) {
            throw new IllegalArgumentException(
                    "Servono almeno " + minime + " candele chiuse, ricevute "
                            + (candele == null ? 0 : candele.size()));
        }
    }
}
