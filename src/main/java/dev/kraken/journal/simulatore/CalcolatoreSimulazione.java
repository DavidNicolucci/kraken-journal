package dev.kraken.journal.simulatore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.registro.Statistica;
import dev.kraken.journal.scansione.CalcolatoreIndicatori;
import dev.kraken.journal.simulatore.dto.Previsione;
import dev.kraken.journal.simulatore.dto.Verifica;

/**
 * Il calcolo del simulatore. Funzioni pure come CalcolatoreIndicatori: niente
 * rete, niente Spring, stesse candele stessi numeri.
 *
 * Il metodo, in tre passi:
 *
 * 1. Ogni giorno di ogni coppia diventa un'osservazione: il suo stato (regime
 *    e fascia di RSI, calcolati solo sulle candele fino a quel giorno) e la
 *    chiusura dopo l'orizzonte, se c'e' gia'.
 *
 * 2. La stima di oggi e' una frequenza: fra tutti i giorni passati nello
 *    stesso stato, su tutta la watchlist, quante volte il prezzo era piu' alto
 *    dopo l'orizzonte. Nessun parametro adattato ai dati, quindi niente da
 *    sovra-adattare: quello che si vede e' quello che e' successo.
 *
 * 3. La verifica rifa' la stessa stima per ogni giorno dell'ultimo anno,
 *    usando ogni volta solo le osservazioni il cui esito era gia' noto quel
 *    giorno, e la confronta con la frequenza di base. Se la stima per stato
 *    non sbaglia meno della base, lo stato non porta informazione.
 *
 * L'orizzonte si conta in candele: sulle coppie della watchlist e' un giorno
 * per candela, perche' si scambiano tutti i giorni.
 */
final class CalcolatoreSimulazione {

    /**
     * Candele prima della prima osservazione: il momento a 60 giorni ne vuole
     * 60 indietro, ed e' anche la soglia a cui l'RSI di Wilder e' a regime.
     */
    static final int RISCALDAMENTO = 60;
    private static final int MEDIA_BREVE = 20;
    private static final int MEDIA_LUNGA = 50;
    private static final int PERIODO_RSI = 14;
    private static final double PERCENTILE_BASSO = 0.05;
    private static final double PERCENTILE_MEDIANO = 0.50;
    private static final double PERCENTILE_ALTO = 0.95;

    private CalcolatoreSimulazione() {
    }

    record Cella(Regime regime, FasciaRsi fascia) {}

    /**
     * Lo stato di una coppia in un giorno.
     *
     * @param chiusuraFutura la chiusura dopo l'orizzonte; null se non e' ancora arrivata
     * @param nota quando quella chiusura e' diventata nota; null come sopra
     */
    record Osservazione(String coppia, Instant istante, Cella cella, double rsi, double momento60,
                        double chiusura, Double chiusuraFutura, Instant nota) {

        boolean conEsito() {
            return chiusuraFutura != null;
        }

        boolean salita() {
            return chiusuraFutura > chiusura;
        }

        double movimento() {
            return chiusuraFutura / chiusura;
        }
    }

    /** Un'osservazione per candela, dalla {@value #RISCALDAMENTO}-esima in poi; vuota se le candele non bastano. */
    static List<Osservazione> osservazioni(String coppia, List<Candela> candele, int orizzonte) {
        List<Osservazione> osservazioni = new ArrayList<>();
        for (int t = RISCALDAMENTO; t < candele.size(); t++) {
            List<Candela> finoAOggi = candele.subList(0, t + 1);
            double chiusura = candele.get(t).chiusura();
            boolean mediaInSalita = CalcolatoreIndicatori.mediaMobile(finoAOggi, MEDIA_BREVE)
                    > CalcolatoreIndicatori.mediaMobile(candele.subList(0, t), MEDIA_BREVE);
            double momento60 = (chiusura / candele.get(t - RISCALDAMENTO).chiusura() - 1) * 100;
            double rsi = CalcolatoreIndicatori.rsi(finoAOggi, PERIODO_RSI);
            Regime regime = Regime.di(chiusura, CalcolatoreIndicatori.mediaMobile(finoAOggi, MEDIA_LUNGA),
                    mediaInSalita, momento60);
            Candela futura = t + orizzonte < candele.size() ? candele.get(t + orizzonte) : null;
            osservazioni.add(new Osservazione(coppia, candele.get(t).istante(), new Cella(regime, FasciaRsi.di(rsi)),
                    rsi, momento60, chiusura,
                    futura == null ? null : futura.chiusura(),
                    futura == null ? null : futura.istante()));
        }
        return osservazioni;
    }

    /**
     * Walk-forward sui giorni da {@code dal} in poi. Un'osservazione entra
     * nei conteggi solo quando il suo esito e' noto, cioe' quando la candela
     * dopo l'orizzonte e' chiusa: senza questa regola la verifica userebbe il
     * futuro e qualunque modello sembrerebbe funzionare.
     *
     * Il vantaggio e' calcolato su tutti i casi; il margine invece su un giorno
     * ogni orizzonte, con la media delle coppie di quel giorno, perche' giorni
     * vicini e coppie dello stesso giorno sbagliano insieme e non sono prove
     * indipendenti.
     */
    static Verifica verifica(List<Osservazione> tutte, Instant dal, int orizzonte, int casiMinimi) {
        List<Osservazione> note = tutte.stream().filter(Osservazione::conEsito)
                .sorted(Comparator.comparing(Osservazione::nota)).toList();
        List<Osservazione> daStimare = tutte.stream().filter(o -> o.conEsito() && !o.istante().isBefore(dal))
                .sorted(Comparator.comparing(Osservazione::istante)).toList();

        Map<Cella, Conteggio> celle = new HashMap<>();
        Conteggio base = new Conteggio();
        TreeMap<Instant, double[]> differenzePerGiorno = new TreeMap<>();
        double erroreModello = 0;
        double erroreBase = 0;
        int giustiModello = 0;
        int giustiBase = 0;
        int salite = 0;
        int casi = 0;
        int giaNote = 0;

        for (Osservazione o : daStimare) {
            while (giaNote < note.size() && !note.get(giaNote).nota().isAfter(o.istante())) {
                Osservazione nota = note.get(giaNote++);
                base.aggiungi(nota.salita());
                celle.computeIfAbsent(nota.cella(), c -> new Conteggio()).aggiungi(nota.salita());
            }
            if (base.casi < casiMinimi) {
                continue;
            }
            double stimaBase = base.frequenza();
            Conteggio cella = celle.get(o.cella());
            double stimaModello = cella != null && cella.casi >= casiMinimi ? cella.frequenza() : stimaBase;
            boolean salita = o.salita();
            double esito = salita ? 1 : 0;
            double eModello = (stimaModello - esito) * (stimaModello - esito);
            double eBase = (stimaBase - esito) * (stimaBase - esito);

            erroreModello += eModello;
            erroreBase += eBase;
            giustiModello += (stimaModello >= 0.5) == salita ? 1 : 0;
            giustiBase += (stimaBase >= 0.5) == salita ? 1 : 0;
            salite += salita ? 1 : 0;
            casi++;
            double[] giorno = differenzePerGiorno.computeIfAbsent(o.istante(), i -> new double[2]);
            giorno[0] += eBase - eModello;
            giorno[1]++;
        }

        double[] valori = distanziati(List.copyOf(differenzePerGiorno.keySet()), orizzonte).stream()
                .map(differenzePerGiorno::get)
                .mapToDouble(g -> g[0] / g[1])
                .toArray();
        double brierModello = casi == 0 ? 0 : erroreModello / casi;
        double brierBase = casi == 0 ? 0 : erroreBase / casi;
        double vantaggio = brierBase - brierModello;
        Double margine = Statistica.margine95(valori);
        return new Verifica(dal, casi, valori.length, brierModello, brierBase, vantaggio, margine,
                casi == 0 ? 0 : (double) giustiModello / casi,
                casi == 0 ? 0 : (double) giustiBase / casi,
                casi == 0 ? 0 : (double) salite / casi,
                verdetto(vantaggio, margine));
    }

    /**
     * La stima di oggi per una coppia, contata sugli esiti noti di tutta la
     * watchlist. L'intervallo di prezzo invece e' solo della coppia: BTC e
     * HYPE non si muovono della stessa ampiezza.
     */
    static Previsione previsione(Osservazione oggi, List<Osservazione> tutte, Instant dal, int orizzonte, int casiMinimi) {
        List<Osservazione> note = tutte.stream().filter(Osservazione::conEsito).toList();
        List<Osservazione> simili = note.stream().filter(o -> o.cella().equals(oggi.cella())).toList();
        boolean dallaBase = simili.size() < casiMinimi;
        List<Osservazione> usate = dallaBase ? note : simili;

        double probabilita = usate.isEmpty() ? 0.5
                : (double) usate.stream().filter(Osservazione::salita).count() / usate.size();
        int indipendenti = distanziati(usate.stream().map(Osservazione::istante).distinct().sorted().toList(),
                orizzonte).size();
        double[] intervallo = Statistica.wilson95(probabilita, indipendenti);

        // L'ultimo anno della coppia; se ha meno storia, tutta quella che ha.
        List<Osservazione> dellaCoppia = note.stream().filter(o -> o.coppia().equals(oggi.coppia())).toList();
        List<Osservazione> recenti = dellaCoppia.stream().filter(o -> !o.istante().isBefore(dal)).toList();
        double[] movimenti = (recenti.isEmpty() ? dellaCoppia : recenti).stream()
                .mapToDouble(Osservazione::movimento).sorted().toArray();
        if (movimenti.length == 0) {
            throw new IllegalArgumentException("Nessun esito noto per " + oggi.coppia());
        }

        return new Previsione(oggi.coppia(), oggi.istante(), oggi.chiusura(), oggi.cella().regime(),
                oggi.rsi(), oggi.momento60(), probabilita, intervallo[0], intervallo[1],
                usate.size(), indipendenti, dallaBase, Direzione.di(intervallo[0], intervallo[1]),
                oggi.chiusura() * percentile(movimenti, PERCENTILE_BASSO),
                oggi.chiusura() * percentile(movimenti, PERCENTILE_MEDIANO),
                oggi.chiusura() * percentile(movimenti, PERCENTILE_ALTO));
    }

    private static Verdetto verdetto(double vantaggio, Double margine) {
        if (margine == null) {
            return Verdetto.DATI_INSUFFICIENTI;
        }
        return vantaggio - margine > 0 ? Verdetto.VANTAGGIO
                : vantaggio + margine < 0 ? Verdetto.PEGGIORE_DELLA_BASE
                : Verdetto.NESSUN_VANTAGGIO;
    }

    /**
     * Dagli istanti in ordine tiene il primo, poi il primo che dista almeno
     * l'orizzonte dall'ultimo tenuto, e cosi' via: quelli tenuti non
     * condividono nessun giorno del movimento che misurano.
     */
    static List<Instant> distanziati(List<Instant> inOrdine, int orizzonte) {
        List<Instant> tenuti = new ArrayList<>();
        for (Instant istante : inOrdine) {
            if (tenuti.isEmpty() || !istante.isBefore(tenuti.get(tenuti.size() - 1).plus(orizzonte, ChronoUnit.DAYS))) {
                tenuti.add(istante);
            }
        }
        return tenuti;
    }

    /** Percentile con interpolazione lineare su valori gia' ordinati. */
    static double percentile(double[] ordinati, double quota) {
        double posizione = quota * (ordinati.length - 1);
        int sotto = (int) Math.floor(posizione);
        int sopra = Math.min(sotto + 1, ordinati.length - 1);
        return ordinati[sotto] + (ordinati[sopra] - ordinati[sotto]) * (posizione - sotto);
    }

    private static final class Conteggio {
        private int casi;
        private int salite;

        void aggiungi(boolean salita) {
            casi++;
            salite += salita ? 1 : 0;
        }

        double frequenza() {
            return (double) salite / casi;
        }
    }
}
