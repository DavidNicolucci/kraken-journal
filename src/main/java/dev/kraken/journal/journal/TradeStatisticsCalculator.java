package dev.kraken.journal.journal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.kraken.journal.journal.dto.JournalStats;
import dev.kraken.journal.journal.dto.OperazioneChiusa;
import dev.kraken.journal.journal.dto.Ordine;
import dev.kraken.journal.journal.dto.PairStats;
import dev.kraken.journal.journal.dto.PosizioneAperta;
import dev.kraken.journal.journal.dto.TotaliValuta;
import dev.kraken.journal.kraken.PairResolver;

/**
 * Calcolo deterministico delle metriche del journal.
 *
 * Le scelte che vale la pena conoscere prima di leggere i numeri:
 *
 * 1. P&L a costo medio ponderato. NON e' il criterio fiscale italiano, che per
 *    le cripto indica il LIFO. Serve a capire come hai operato, non a compilare
 *    la dichiarazione.
 *
 * 2. Il costo di carico si ricostruisce su TUTTO lo storico, il periodo decide
 *    solo cosa riportare. Prima si leggevano solo i trade del periodo, e ogni
 *    posizione aperta prima dell'inizio della finestra diventava una vendita
 *    "senza acquisto": con operazioni di 3-15 giorni, quasi una su due nella
 *    vista a trenta giorni.
 *
 * 3. Le posizioni si tengono per moneta, con un costo per ogni valuta. Una
 *    vendita consuma prima il carico nella sua valuta. SOL comprato in USD e
 *    venduto in EUR esce dal carico (altrimenti resterebbe aperto per sempre):
 *    il suo costo si converte al cambio del giorno della vendita, e se il
 *    cambio non c'e' quella parte resta senza risultato.
 *
 * 4. Una vendita senza acquisto corrispondente non annulla il risultato del
 *    resto della coppia. Si dichiara la quantita' scoperta e il risultato
 *    copre la parte abbinata. Una versione precedente sottraeva solo le
 *    commissioni, producendo un P&L che sembrava reale ed era un artefatto;
 *    quella successiva scartava tutta la coppia, perdendo giri completi veri.
 *
 * 5. Le conversioni valutarie (EUR/USD) sono contate a parte. Non sono trade
 *    e includerle gonfia frequenza e conteggi.
 *
 * 6. Si contano gli ordini, non le esecuzioni: un ordine limite riempito in
 *    tre pezzi e' una decisione sola.
 *
 * 7. Una posizione e' chiusa quando ne resta meno di
 *    {@code journal.residuo-trascurabile} per cento della quantita' massima
 *    tenuta. Il resto e' polvere, non una posizione aperta: resta nel carico,
 *    perche' le monete sono ancora sul conto, e il suo costo passa dal giro
 *    chiuso al giro successivo.
 */
@Component
public class TradeStatisticsCalculator {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final MathContext PRICE_MC = new MathContext(6, RoundingMode.HALF_UP);
    private static final int SCALE = 8;
    private static final BigDecimal CENTO = BigDecimal.valueOf(100);

    private final ProprietaJournal proprieta;
    private final ZoneId fuso;

    /**
     * @param fuso quello del lavoro giornaliero: un'operazione a mezzanotte e
     *        mezza di Roma e' di oggi, non di ieri come direbbe UTC.
     */
    public TradeStatisticsCalculator(ProprietaJournal proprieta, @Value("${giornaliero.fuso}") ZoneId fuso) {
        this.proprieta = proprieta;
        this.fuso = fuso;
    }

    public JournalStats compute(Storia storia, Cambi cambi, Instant from, Instant to) {
        Map<String, Moneta> monete = new TreeMap<>();
        Map<String, Accumulo> coppie = new LinkedHashMap<>();
        Map<String, List<Movimento>> ordini = new LinkedHashMap<>();
        List<OperazioneChiusa> chiuse = new ArrayList<>();
        Set<LocalDate> giorniAttivi = new HashSet<>();

        for (Movimento m : storia.movimenti()) {
            if (m.istante().isAfter(to)) {
                break;
            }
            Vendita vendita = Vendita.NESSUNA;
            if (!m.coppia().fxConversion()) {
                Moneta moneta = monete.computeIfAbsent(m.coppia().base(), k -> new Moneta(cambi));
                vendita = moneta.applica(m);
                if (moneta.chiusaDa(m)) {
                    OperazioneChiusa chiusa = moneta.chiudi(m.istante());
                    if (!m.istante().isBefore(from)) {
                        chiuse.add(chiusa);
                    }
                }
            }
            if (m.istante().isBefore(from)) {
                continue;
            }
            String chiave = chiaveOrdine(m, ordini.size());
            ordini.computeIfAbsent(chiave, k -> new ArrayList<>()).add(m);
            coppie.computeIfAbsent(m.coppia().display(), k -> new Accumulo(m.coppia())).aggiungi(m, vendita, chiave);
            if (!m.coppia().fxConversion()) {
                giorniAttivi.add(m.istante().atZone(fuso).toLocalDate());
            }
        }

        List<PairStats> pairStats = coppie.values().stream()
                .map(a -> a.stats(monete.get(a.coppia.base()), storia.arrivi()))
                .sorted(Comparator.comparing(PairStats::volumeQuote).reversed())
                .toList();
        List<Accumulo> trading = coppie.values().stream().filter(a -> !a.coppia.fxConversion()).toList();
        List<PairStats> tradingStats = pairStats.stream().filter(p -> !p.fxConversion()).toList();

        int esecuzioniTrading = trading.stream().mapToInt(a -> a.esecuzioni).sum();
        int ordiniTrading = trading.stream().mapToInt(a -> a.ordini.size()).sum();
        int incomplete = (int) tradingStats.stream().filter(p -> !p.pnlComplete()).count();

        Set<String> valute = trading.stream().map(a -> a.coppia.quote()).collect(Collectors.toCollection(TreeSet::new));

        return new JournalStats(
                from, to,
                coppie.values().stream().mapToInt(a -> a.esecuzioni).sum(),
                esecuzioniTrading,
                coppie.values().stream().filter(a -> a.coppia.fxConversion()).mapToInt(a -> a.esecuzioni).sum(),
                giorniAttivi.size(),
                giorniAttivi.isEmpty() ? BigDecimal.ZERO
                        : BigDecimal.valueOf(ordiniTrading).divide(BigDecimal.valueOf(giorniAttivi.size()), 2,
                                RoundingMode.HALF_UP),
                incomplete,
                valute.size() > 1,
                List.copyOf(valute),
                pairStats,
                storia.ledgerLetto(),
                storia.completa(),
                ordini.values().stream().map(TradeStatisticsCalculator::ordine).toList(),
                valute.stream().map(v -> totali(v, trading.stream().filter(a -> a.coppia.quote().equals(v)).toList()))
                        .toList(),
                List.copyOf(chiuse),
                posizioniAperte(monete));
    }

    /** Senza un id si considera ogni esecuzione un ordine a se': meglio contarne troppi che fonderne due diversi. */
    private static String chiaveOrdine(Movimento m, int progressivo) {
        return m.idOrdine() == null || m.idOrdine().isBlank()
                ? "#" + progressivo
                : m.idOrdine() + "|" + m.coppia().display() + "|" + m.acquisto();
    }

    private static Ordine ordine(List<Movimento> esecuzioni) {
        Movimento prima = esecuzioni.get(0);
        boolean qualcunaTaker = esecuzioni.stream().anyMatch(e -> Boolean.FALSE.equals(e.maker()));
        boolean tutteNote = esecuzioni.stream().allMatch(e -> e.maker() != null);
        return new Ordine(
                prima.idOrdine(),
                prima.istante(),
                prima.coppia().display(),
                prima.coppia().base(),
                prima.coppia().quote(),
                prima.acquisto(),
                prima.coppia().fxConversion(),
                prima.origine(),
                qualcunaTaker ? Boolean.TRUE : tutteNote ? Boolean.FALSE : null,
                esecuzioni.size(),
                scaled(somma(esecuzioni, Movimento::quantita)),
                scaled(somma(esecuzioni, Movimento::controvalore)),
                scaled(somma(esecuzioni, Movimento::commissione)));
    }

    private static TotaliValuta totali(String valuta, List<Accumulo> coppie) {
        BigDecimal volume = somma(coppie, a -> a.volume);
        BigDecimal commissioni = somma(coppie, a -> a.commissioni);
        List<Accumulo> conRisultato = coppie.stream().filter(a -> a.abbinata.signum() > 0).toList();
        return new TotaliValuta(
                valuta,
                coppie.stream().mapToInt(a -> a.ordini.size()).sum(),
                scaled(volume),
                scaled(commissioni),
                percentOf(commissioni, volume),
                scaled(somma(coppie, a -> a.volumeMaker)),
                scaled(somma(coppie, a -> a.volumeTaker)),
                scaled(somma(coppie, a -> a.commissioniTaker)),
                scaled(somma(coppie, a -> a.volumeApp)),
                scaled(somma(coppie, a -> a.commissioniApp)),
                conRisultato.isEmpty() ? null : scaled(somma(conRisultato, a -> a.realizzato)),
                (int) coppie.stream().filter(a -> !a.completa()).count());
    }

    private List<PosizioneAperta> posizioniAperte(Map<String, Moneta> monete) {
        List<PosizioneAperta> aperte = new ArrayList<>();
        monete.forEach((codice, moneta) -> {
            if (moneta.giro != null) {
                moneta.carichi.forEach((valuta, carico) -> {
                    if (carico.quantita.signum() > 0) {
                        aperte.add(new PosizioneAperta(codice + "/" + valuta, codice, valuta,
                                scaled(carico.quantita), scaled(carico.costo), moneta.giro.apertura));
                    }
                });
            }
        });
        return List.copyOf(aperte);
    }

    /** Quantita' e costo di carico di una moneta in una valuta. */
    private static final class Carico {
        BigDecimal quantita = BigDecimal.ZERO;
        BigDecimal costo = BigDecimal.ZERO;

        /** Toglie una quantita' al suo costo medio e restituisce quel costo. */
        BigDecimal togli(BigDecimal parte) {
            BigDecimal costoTolto = costo.multiply(parte, MC).divide(quantita, MC);
            quantita = quantita.subtract(parte);
            costo = costo.subtract(costoTolto);
            if (quantita.signum() <= 0) {
                quantita = BigDecimal.ZERO;
                costo = BigDecimal.ZERO;
            }
            return costoTolto;
        }
    }

    /**
     * Quello che una vendita ha prodotto.
     *
     * @param realizzato risultato della parte abbinata, null se non ce n'e'.
     * @param abbinata venduta a fronte di un acquisto, nella stessa valuta o
     *        convertito: e' la parte con un risultato.
     * @param convertita la parte di {@code abbinata} comprata in un'altra
     *        valuta, con il costo convertito al cambio del giorno.
     * @param altraValuta venduta a fronte di un acquisto in un'altra valuta,
     *        senza un cambio per quel giorno.
     * @param scoperta venduta senza nessun acquisto nello storico.
     */
    private record Vendita(BigDecimal realizzato, BigDecimal abbinata, BigDecimal convertita,
                           BigDecimal altraValuta, BigDecimal scoperta) {
        static final Vendita NESSUNA =
                new Vendita(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Un giro completo in corso su una moneta: dalla prima entrata fino a che
     * ne resta solo polvere. Investito e risultato sono nella valuta del primo
     * acquisto: quello che passa per un'altra valuta si converte al cambio del
     * giorno.
     */
    private static final class Giro {
        final Instant apertura;
        final String coppia;
        final String valuta;
        /** Null appena entra un'altra valuta senza un cambio, come il risultato: un totale parziale sembrerebbe giusto. */
        BigDecimal investito;
        /** Null appena entra un'altra valuta senza un cambio: i due risultati non si sommano. */
        BigDecimal risultato = BigDecimal.ZERO;
        BigDecimal massimo = BigDecimal.ZERO;
        /** Il costo piu' alto tenuto insieme: la dimensione vera della posizione, anche se comprata in piu' volte. */
        BigDecimal costoMassimo = BigDecimal.ZERO;
        BigDecimal venduta = BigDecimal.ZERO;
        /** Il lordo delle vendite nella valuta del giro: diviso per la quantita', il prezzo medio di uscita. */
        BigDecimal incassato = BigDecimal.ZERO;

        /** @param polvere il costo della polvere lasciata dal giro prima, che questo giro eredita. */
        Giro(Instant apertura, PairResolver.Pair coppia, BigDecimal polvere) {
            this.apertura = apertura;
            this.coppia = coppia.display();
            this.valuta = coppia.quote();
            this.investito = polvere;
        }

        OperazioneChiusa chiudi(Instant chiusura) {
            return new OperazioneChiusa(coppia, valuta, apertura, chiusura,
                    Duration.between(apertura, chiusura).toDays(), scaled(investito), scaled(risultato),
                    scaled(costoMassimo), incassato == null || venduta.signum() == 0
                            ? null : scaled(incassato.divide(venduta, MC)));
        }
    }

    /** Tutto quello che si sa di una moneta, su tutto lo storico. */
    private final class Moneta {
        final Map<String, Carico> carichi = new TreeMap<>();
        final Cambi cambi;
        Giro giro;

        Moneta(Cambi cambi) {
            this.cambi = cambi;
        }

        Vendita applica(Movimento m) {
            String valuta = m.coppia().quote();
            if (m.acquisto()) {
                if (giro == null) {
                    giro = new Giro(m.istante(), m.coppia(), costoIn(valuta, m.istante()).orElse(null));
                }
                Carico carico = carichi.computeIfAbsent(valuta, k -> new Carico());
                BigDecimal costo = m.controvalore().add(m.commissione());
                carico.quantita = carico.quantita.add(m.quantita());
                carico.costo = carico.costo.add(costo);
                Optional<BigDecimal> cambio = cambi.tasso(valuta, giro.valuta, m.istante());
                giro.investito = cambio.isPresent() && giro.investito != null
                        ? giro.investito.add(costo.multiply(cambio.get(), MC)) : null;
                if (cambio.isEmpty()) {
                    giro.risultato = null;
                }
                giro.massimo = giro.massimo.max(totale());
                costoIn(giro.valuta, m.istante()).ifPresent(tenuto -> giro.costoMassimo = giro.costoMassimo.max(tenuto));
                return Vendita.NESSUNA;
            }
            Vendita vendita = vendi(m);
            if (giro != null) {
                Optional<BigDecimal> cambio = cambi.tasso(valuta, giro.valuta, m.istante());
                if (vendita.altraValuta().signum() > 0 || cambio.isEmpty()) {
                    giro.risultato = null;
                } else if (giro.risultato != null && vendita.realizzato() != null) {
                    giro.risultato = giro.risultato.add(vendita.realizzato().multiply(cambio.get(), MC));
                }
                giro.venduta = giro.venduta.add(m.quantita());
                giro.incassato = cambio.isPresent() && giro.incassato != null
                        ? giro.incassato.add(m.controvalore().multiply(cambio.get(), MC)) : null;
            }
            return vendita;
        }

        /** Dopo una vendita: e' rimasta solo polvere? */
        boolean chiusaDa(Movimento m) {
            return !m.acquisto() && giro != null
                    && totale().compareTo(giro.massimo.multiply(proprieta.residuoTrascurabile()).divide(CENTO, MC)) <= 0;
        }

        /**
         * Chiude il giro. La polvere non e' stata venduta in questo giro: il
         * suo costo esce dall'investito, cosi' investito e risultato parlano
         * della stessa quantita', e il giro dopo lo ritrova nel carico.
         */
        OperazioneChiusa chiudi(Instant quando) {
            Optional<BigDecimal> polvere = costoIn(giro.valuta, quando);
            giro.investito = giro.investito != null && polvere.isPresent() ? giro.investito.subtract(polvere.get()) : null;
            OperazioneChiusa chiusa = giro.chiudi(quando);
            giro = null;
            return chiusa;
        }

        /** Il costo di quello che resta nel carico, in una valuta; vuoto se manca un cambio. */
        private Optional<BigDecimal> costoIn(String valuta, Instant quando) {
            BigDecimal totale = BigDecimal.ZERO;
            for (var voce : carichi.entrySet()) {
                if (voce.getValue().costo.signum() != 0) {
                    Optional<BigDecimal> cambio = cambi.tasso(voce.getKey(), valuta, quando);
                    if (cambio.isEmpty()) {
                        return Optional.empty();
                    }
                    totale = totale.add(voce.getValue().costo.multiply(cambio.get(), MC));
                }
            }
            return Optional.of(totale);
        }

        /**
         * Prima il carico nella valuta della vendita, poi quello nelle altre:
         * il loro costo si converte al cambio del giorno della vendita. Si usa
         * lo stesso giorno per ricavo e costo, cosi' il risultato e' quello
         * che avresti avuto riportando subito il ricavo nella valuta spesa.
         */
        private Vendita vendi(Movimento m) {
            BigDecimal quantita = m.quantita();
            if (quantita.signum() <= 0) {
                return Vendita.NESSUNA;
            }
            String valuta = m.coppia().quote();
            BigDecimal ricavo = m.controvalore().subtract(m.commissione());
            Carico stessaValuta = carichi.get(valuta);
            BigDecimal abbinata = stessaValuta == null ? BigDecimal.ZERO : quantita.min(stessaValuta.quantita);
            BigDecimal realizzato = abbinata.signum() > 0
                    ? parte(ricavo, abbinata, quantita).subtract(stessaValuta.togli(abbinata)) : null;
            BigDecimal resto = quantita.subtract(abbinata);
            BigDecimal convertita = BigDecimal.ZERO;
            BigDecimal altraValuta = BigDecimal.ZERO;
            for (var voce : carichi.entrySet()) {
                if (resto.signum() <= 0) {
                    break;
                }
                Carico carico = voce.getValue();
                if (!voce.getKey().equals(valuta) && carico.quantita.signum() > 0) {
                    BigDecimal presa = resto.min(carico.quantita);
                    BigDecimal costo = carico.togli(presa);
                    Optional<BigDecimal> cambio = cambi.tasso(voce.getKey(), valuta, m.istante());
                    if (cambio.isPresent()) {
                        BigDecimal risultato = parte(ricavo, presa, quantita).subtract(costo.multiply(cambio.get(), MC));
                        realizzato = realizzato == null ? risultato : realizzato.add(risultato);
                        convertita = convertita.add(presa);
                    } else {
                        altraValuta = altraValuta.add(presa);
                    }
                    resto = resto.subtract(presa);
                }
            }
            return new Vendita(realizzato, abbinata.add(convertita), convertita, altraValuta, resto);
        }

        /** La quota del ricavo che spetta a una parte della quantita' venduta. */
        private static BigDecimal parte(BigDecimal ricavo, BigDecimal parte, BigDecimal quantita) {
            return ricavo.multiply(parte, MC).divide(quantita, MC);
        }

        private BigDecimal totale() {
            return carichi.values().stream().map(c -> c.quantita).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /** I numeri di una coppia nel periodo. */
    private static final class Accumulo {
        final PairResolver.Pair coppia;
        final Set<String> ordini = new HashSet<>();
        int esecuzioni, acquisti, vendite, daApp;
        BigDecimal volume = BigDecimal.ZERO, commissioni = BigDecimal.ZERO;
        BigDecimal volumeMaker = BigDecimal.ZERO, volumeTaker = BigDecimal.ZERO, commissioniTaker = BigDecimal.ZERO;
        BigDecimal volumeApp = BigDecimal.ZERO, commissioniApp = BigDecimal.ZERO;
        BigDecimal quantitaComprata = BigDecimal.ZERO, valoreComprato = BigDecimal.ZERO;
        BigDecimal quantitaVenduta = BigDecimal.ZERO, valoreVenduto = BigDecimal.ZERO;
        BigDecimal realizzato = BigDecimal.ZERO, abbinata = BigDecimal.ZERO;
        BigDecimal convertita = BigDecimal.ZERO, altraValuta = BigDecimal.ZERO, scoperta = BigDecimal.ZERO;

        Accumulo(PairResolver.Pair coppia) {
            this.coppia = coppia;
        }

        void aggiungi(Movimento m, Vendita vendita, String chiaveOrdine) {
            esecuzioni++;
            ordini.add(chiaveOrdine);
            volume = volume.add(m.controvalore());
            commissioni = commissioni.add(m.commissione());
            if (m.origine() == Origine.APP) {
                daApp++;
                volumeApp = volumeApp.add(m.controvalore());
                commissioniApp = commissioniApp.add(m.commissione());
            } else if (Boolean.TRUE.equals(m.maker())) {
                volumeMaker = volumeMaker.add(m.controvalore());
            } else if (Boolean.FALSE.equals(m.maker())) {
                volumeTaker = volumeTaker.add(m.controvalore());
                commissioniTaker = commissioniTaker.add(m.commissione());
            }
            if (m.acquisto()) {
                acquisti++;
                quantitaComprata = quantitaComprata.add(m.quantita());
                valoreComprato = valoreComprato.add(m.controvalore());
                return;
            }
            vendite++;
            quantitaVenduta = quantitaVenduta.add(m.quantita());
            valoreVenduto = valoreVenduto.add(m.controvalore());
            if (vendita.realizzato() != null) {
                realizzato = realizzato.add(vendita.realizzato());
            }
            abbinata = abbinata.add(vendita.abbinata());
            convertita = convertita.add(vendita.convertita());
            altraValuta = altraValuta.add(vendita.altraValuta());
            scoperta = scoperta.add(vendita.scoperta());
        }

        /** Una conversione valutaria non ha un costo di carico: non e' mai "completa". */
        boolean completa() {
            return !coppia.fxConversion() && scoperta.signum() == 0 && altraValuta.signum() == 0;
        }

        PairStats stats(Moneta moneta, Map<String, Set<Provenienza>> arrivi) {
            Carico carico = moneta == null || moneta.giro == null ? null : moneta.carichi.get(coppia.quote());
            BigDecimal risultato = vendite == 0 ? BigDecimal.ZERO : abbinata.signum() > 0 ? realizzato : null;
            return new PairStats(
                    coppia.raw(),
                    coppia.display(),
                    coppia.quote(),
                    coppia.fxConversion(),
                    esecuzioni, acquisti, vendite,
                    scaled(volume),
                    scaled(commissioni),
                    percentOf(commissioni, volume),
                    scaled(risultato),
                    completa(),
                    scaled(scoperta),
                    price(valoreComprato, quantitaComprata),
                    price(valoreVenduto, quantitaVenduta),
                    carico == null ? BigDecimal.ZERO : scaled(carico.quantita),
                    ordini.size(),
                    daApp,
                    scaled(volumeTaker),
                    scaled(altraValuta),
                    scaled(convertita),
                    carico == null ? BigDecimal.ZERO : scaled(carico.costo),
                    scoperta.signum() > 0 ? Set.copyOf(arrivi.getOrDefault(coppia.base(), Set.of())) : Set.of());
        }
    }

    /**
     * Scala dinamica: un token che vale frazioni di centesimo arrotondato a due
     * decimali diventa 0.00, che si legge come "prezzo zero" invece che come
     * "arrotondamento". Sotto l'unita' uso cifre significative.
     */
    private static BigDecimal price(BigDecimal value, BigDecimal qty) {
        if (qty == null || qty.signum() == 0) return null;
        BigDecimal p = value.divide(qty, MC);
        if (p.abs().compareTo(BigDecimal.ONE) >= 0) {
            return p.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal rounded = p.round(PRICE_MC).stripTrailingZeros();
        return rounded.scale() < 0 ? rounded.setScale(0, RoundingMode.UNNECESSARY) : rounded;
    }

    private static BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return null;
        return part.divide(whole, 6, RoundingMode.HALF_UP)
                   .multiply(CENTO)
                   .setScale(2, RoundingMode.HALF_UP);
    }

    private static <T> BigDecimal somma(List<T> voci, Function<T, BigDecimal> campo) {
        return voci.stream().map(campo).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal scaled(BigDecimal v) {
        if (v == null) return null;
        BigDecimal s = v.setScale(SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
        if (s.signum() == 0) return BigDecimal.ZERO;
        // scale negativa significa notazione esponenziale in JSON (3E+3): la
        // riporto a zero cosi' il numero resta leggibile in forma piana.
        return s.scale() < 0 ? s.setScale(0, RoundingMode.UNNECESSARY) : s;
    }
}
