package dev.kraken.journal.journal;

import static dev.kraken.journal.journal.TestoItaliano.a;
import static dev.kraken.journal.journal.TestoItaliano.articolo;
import static dev.kraken.journal.journal.TestoItaliano.conSegno;
import static dev.kraken.journal.journal.TestoItaliano.data;
import static dev.kraken.journal.journal.TestoItaliano.di;
import static dev.kraken.journal.journal.TestoItaliano.durata;
import static dev.kraken.journal.journal.TestoItaliano.elenco;
import static dev.kraken.journal.journal.TestoItaliano.importo;
import static dev.kraken.journal.journal.TestoItaliano.numero;
import static dev.kraken.journal.journal.TestoItaliano.percentuale;
import static dev.kraken.journal.journal.TestoItaliano.plurale;
import static dev.kraken.journal.journal.TestoItaliano.quota;
import static dev.kraken.journal.journal.TestoItaliano.unisci;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.kraken.journal.journal.dto.JournalReport;
import dev.kraken.journal.journal.dto.JournalStats;
import dev.kraken.journal.journal.dto.OperazioneChiusa;
import dev.kraken.journal.journal.dto.Ordine;
import dev.kraken.journal.journal.dto.PairStats;
import dev.kraken.journal.journal.dto.PosizioneAperta;
import dev.kraken.journal.journal.dto.TotaliValuta;
import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.registro.OperazioneRegistrata;
import dev.kraken.journal.scansione.MatematicaOperazione;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.scansione.TipoOrdine;
import dev.kraken.journal.scansione.Verso;

/**
 * Il commento del journal, scritto da regole fisse invece che da un modello.
 *
 * Ogni regola e' descritta in docs/regole-commento-journal.md con lo stesso
 * codice (A1, B2, D1, E8...). Valgono i principi di quel documento: solo
 * numeri gia' calcolati, niente previsioni ne' consigli, prima i limiti e poi
 * le conclusioni, ogni importo con la sua valuta. Stessi dati, stesso testo.
 */
@Component
public class GeneratoreCommento {

    private static final int VOCI_MASSIME = 5;
    private static final int DOMANDE_MASSIME = 3;
    private static final int ELENCO_MASSIMO = 6;
    /** Sotto il 2% della quantita' registrata un'operazione e' riempita: il resto sono commissioni e arrotondamenti. */
    private static final double QUOTA_ESAURITA = 0.02;
    private static final BigDecimal CENTO = BigDecimal.valueOf(100);
    private static final MathContext MC = MathContext.DECIMAL64;

    private final ProprietaCommento commento;
    private final ProprietaScansione scansione;
    private final PairResolver pairResolver;
    private final ZoneId fuso;

    public GeneratoreCommento(ProprietaCommento commento, ProprietaScansione scansione, PairResolver pairResolver,
                              @Value("${giornaliero.fuso}") ZoneId fuso) {
        this.commento = commento;
        this.scansione = scansione;
        this.pairResolver = pairResolver;
        this.fuso = fuso;
    }

    /** Una voce di "Cosa dicono i dati", con la sua domanda se la regola ne prevede una. */
    record Voce(String testo, String domanda) {
        static Optional<Voce> nuova(String testo) {
            return Optional.of(new Voce(testo, null));
        }

        static Optional<Voce> nuova(String testo, String domanda) {
            return Optional.of(new Voce(testo, domanda));
        }
    }

    /**
     * @param giorni il periodo chiesto dalla pagina, per il testo di A1 e A2.
     * @param reali le operazioni vere del registro, per E8.
     */
    public JournalReport genera(JournalStats s, int giorni, List<OperazioneRegistrata> reali) {
        Dati d = new Dati(s, reali);

        if (s.ordini().isEmpty()) {
            return report("Nessuna operazione negli ultimi %d giorni.".formatted(giorni),
                    Stream.of(e4(d), e7(d)),
                    Stream.of(b0(d), Optional.of("Nessun dato da analizzare nel periodo.")));
        }
        if (d.trading.isEmpty()) {
            return report((d.conversioni.size() == 1
                            ? "Negli ultimi %d giorni c'è solo una conversione valutaria e nessuna operazione di trading."
                                    .formatted(giorni)
                            : "Negli ultimi %d giorni ci sono solo %d conversioni valutarie e nessuna operazione di trading."
                                    .formatted(giorni, d.conversioni.size())),
                    Stream.of(d4(d), e4(d), e7(d)),
                    Stream.of(b0(d), Optional.of("Le conversioni valutarie non sono operazioni di trading: "
                            + "non c'è un comportamento operativo da leggere.")));
        }
        return report(
                String.join(" ", c1(d), c2(d), c3(d)),
                Stream.of(e8(d), d2(d), d1(d), e9(d), e10(d), e7(d), e1(d), e3(d), e2(d), d4(d), e4(d), e6(d), d3(d)),
                Stream.of(b0(d), b7(d), b1(d), b2(d), b3(d), b4(d), b5(d), b6()));
    }

    private static JournalReport report(String lettura, Stream<Optional<Voce>> regole, Stream<Optional<String>> limiti) {
        List<Voce> voci = regole.flatMap(Optional::stream).limit(VOCI_MASSIME).toList();
        return new JournalReport(
                lettura,
                voci.stream().map(Voce::testo).toList(),
                voci.stream().map(Voce::domanda).filter(Objects::nonNull).limit(DOMANDE_MASSIME).toList(),
                limiti.flatMap(Optional::stream).collect(Collectors.joining(" ")));
    }

    /** Quello che piu' regole leggono, calcolato una volta. */
    private final class Dati {
        final JournalStats s;
        final List<Ordine> trading;
        final List<Ordine> conversioni;
        final List<Ordine> vendite;
        /** Acquisti da quando c'e' il metodo: sono quelli che si confrontano con le sue regole. */
        final List<Ordine> acquistiNelMetodo;
        final Instant inizioMetodo;
        final Set<String> moneteWatchlist;
        /** La valuta della watchlist, se e' una sola: e' anche quella del capitale. */
        final String valutaWatchlist;
        final List<OperazioneRegistrata> reali;

        Dati(JournalStats s, List<OperazioneRegistrata> reali) {
            this.s = s;
            this.reali = reali;
            trading = s.ordini().stream().filter(o -> !o.conversione()).toList();
            conversioni = s.ordini().stream().filter(Ordine::conversione).toList();
            vendite = trading.stream().filter(o -> !o.acquisto()).toList();
            inizioMetodo = commento.metodoDal().atStartOfDay(fuso).toInstant();
            acquistiNelMetodo = trading.stream()
                    .filter(o -> o.acquisto() && !o.istante().isBefore(inizioMetodo)).toList();
            List<PairResolver.Pair> watchlist = scansione.watchlist().stream().map(pairResolver::resolve).toList();
            moneteWatchlist = watchlist.stream().map(PairResolver.Pair::base).collect(Collectors.toSet());
            Set<String> valute = watchlist.stream().map(PairResolver.Pair::quote).collect(Collectors.toSet());
            valutaWatchlist = valute.size() == 1 ? valute.iterator().next() : null;
        }

        boolean nelMetodo(Instant istante) {
            return !istante.isBefore(inizioMetodo);
        }

        /** L'istante, o l'inizio del metodo se viene prima. */
        Instant dalMetodo(Instant istante) {
            return nelMetodo(istante) ? istante : inizioMetodo;
        }
    }

    // ------------------------------------------------------------------ C. Lettura

    /** C1 — Attivita' del periodo. */
    private String c1(Dati d) {
        long coppie = d.trading.stream().map(Ordine::coppia).distinct().count();
        long dallApp = d.trading.stream().filter(o -> o.origine() == Origine.APP).count();
        int giorni = d.s.tradingDays();
        String app = dallApp == 0 ? ""
                : dallApp == d.trading.size() ? (dallApp == 1 ? " (dall'app)" : " (tutti dall'app)")
                : " (" + dallApp + " dall'app)";
        String testo = "Dal %s al %s hai fatto %s di trading%s su %s, %s.".formatted(
                data(d.s.periodStart(), fuso), data(d.s.periodEnd(), fuso),
                plurale(d.trading.size(), "ordine", "ordini"), app, plurale(coppie, "coppia", "coppie"),
                giorni == 1 ? "in un solo giorno" : "in " + giorni + " giorni diversi");
        return d.conversioni.isEmpty() ? testo
                : testo + " A parte, " + plurale(d.conversioni.size(), "conversione valutaria", "conversioni valutarie") + ".";
    }

    /** C2 — Costo del periodo, per valuta. */
    private String c2(Dati d) {
        List<TotaliValuta> totali = d.s.totaliPerValuta().stream()
                .filter(t -> t.commissioniPercentuale() != null).toList();
        if (totali.size() == 1) {
            TotaliValuta t = totali.get(0);
            return "Le commissioni di trading sono state %s, pari %s del volume."
                    .formatted(importo(t.commissioni(), t.valuta()), a(percentuale(t.commissioniPercentuale())));
        }
        List<String> parti = new ArrayList<>();
        for (int i = 0; i < totali.size(); i++) {
            TotaliValuta t = totali.get(i);
            parti.add("%s (%s%s)".formatted(importo(t.commissioni(), t.valuta()),
                    articolo(percentuale(t.commissioniPercentuale())), i == 0 ? " del volume" : ""));
        }
        return "Le commissioni di trading sono state " + unisci(parti) + ".";
    }

    /** C3 — Risultato. */
    private String c3(Dati d) {
        if (d.vendite.isEmpty()) {
            return "Nel periodo hai solo comprato: non c'è ancora un risultato realizzato.";
        }
        List<TotaliValuta> conRisultato = d.s.totaliPerValuta().stream().filter(t -> t.risultato() != null).toList();
        if (conRisultato.isEmpty()) {
            return "Il risultato non è calcolabile: nessuna vendita del periodo ha un acquisto corrispondente.";
        }
        String importi = unisci(conRisultato.stream().map(t -> conSegno(t.risultato(), t.valuta())).toList());
        List<OperazioneChiusa> chiuse = chiuseConRisultato(d);
        if (chiuse.isEmpty()) {
            return "Il risultato realizzato è " + importi
                    + ", da vendite parziali di posizioni ancora aperte: non basta per dire come è andato il periodo.";
        }
        if (chiuse.size() < scansione.registrazioniPerGiudizio()) {
            return "Il risultato realizzato è %s, su %s: non basta per dire come è andato il periodo."
                    .formatted(importi, plurale(chiuse.size(), "operazione chiusa", "operazioni chiuse"));
        }
        long inGuadagno = chiuse.stream().filter(o -> o.risultato().signum() > 0).count();
        return "Il risultato realizzato è %s: %s su %d in guadagno."
                .formatted(importi, plurale(inGuadagno, "operazione chiusa", "operazioni chiuse"), chiuse.size());
    }

    private static List<OperazioneChiusa> chiuseConRisultato(Dati d) {
        return d.s.operazioniChiuse().stream().filter(o -> o.risultato() != null).toList();
    }

    // ------------------------------------------------------------------ B. Limiti

    /** B0 — Ledger non letto. */
    private static Optional<String> b0(Dati d) {
        return d.s.ledgerLetto() ? Optional.empty() : Optional.of(
                "La chiave API non può leggere il ledger: mancano le operazioni fatte dall'app, e le vendite "
                        + "di monete comprate lì risultano senza acquisto. Aggiungi il permesso Query Ledger Entries.");
    }

    /** B7 — Storico troncato: Kraken ha restituito solo le voci piu' recenti. */
    private static Optional<String> b7(Dati d) {
        return d.s.storicoCompleto() ? Optional.empty() : Optional.of(
                "Lo storico scaricato da Kraken si ferma alle voci più recenti: le vendite di monete comprate "
                        + "prima risultano senza costo di carico, anche se un acquisto c'è stato.");
    }

    /** B1 — Poche operazioni chiuse. */
    private Optional<String> b1(Dati d) {
        int chiuse = chiuseConRisultato(d).size();
        int soglia = scansione.registrazioniPerGiudizio();
        if (d.vendite.isEmpty() || chiuse >= soglia) {
            return Optional.empty();
        }
        String quante = switch (chiuse) {
            case 0 -> "Nel periodo non c'è nessuna operazione chiusa con un risultato calcolabile";
            case 1 -> "C'è una sola operazione chiusa con un risultato calcolabile";
            default -> "Le operazioni chiuse con un risultato calcolabile sono " + chiuse;
        };
        return Optional.of(quante + ": sotto " + soglia + " il risultato dice poco sul metodo.");
    }

    /**
     * B2 — Monete vendute senza un acquisto, con la provenienza letta dal ledger.
     * Le coppie con la stessa provenienza stanno in un solo gruppo: la
     * provenienza si scrive una volta, non dopo ogni coppia.
     */
    private static Optional<String> b2(Dati d) {
        Map<String, List<String>> perProvenienza = trading(d).filter(p -> p.unmatchedSellVolume().signum() > 0)
                .collect(Collectors.groupingBy(p -> p.provenienze().isEmpty() ? "provenienza non registrata"
                                : unisci(p.provenienze().stream().map(Provenienza::descrizione).sorted().toList()),
                        LinkedHashMap::new, Collectors.mapping(PairStats::displayName, Collectors.toList())));
        return perProvenienza.isEmpty() ? Optional.empty() : Optional.of("Hai venduto monete che non risultano comprate "
                + unisci(perProvenienza.entrySet().stream()
                        .map(g -> "su " + elenco(g.getValue(), ELENCO_MASSIMO, "altre") + " (" + g.getKey() + ")").toList())
                + ": per quella parte il risultato non è calcolabile e non è sommato a zero.");
    }

    /** B3 — Vendite in un'altra valuta: col costo convertito al cambio del giorno, o senza un cambio. */
    private static Optional<String> b3(Dati d) {
        List<String> convertite = trading(d).filter(p -> p.quantitaConvertita().signum() > 0)
                .map(PairStats::displayName).toList();
        List<String> senzaCambio = trading(d).filter(p -> p.quantitaInAltraValuta().signum() > 0)
                .map(PairStats::displayName).toList();
        String testo = Stream.of(
                convertite.isEmpty() ? "" : "Su " + elenco(convertite, ELENCO_MASSIMO, "altre")
                        + " hai venduto monete comprate in un'altra valuta: il costo è convertito al cambio medio "
                        + "del giorno della vendita, preso da Kraken.",
                senzaCambio.isEmpty() ? "" : "Su " + elenco(senzaCambio, ELENCO_MASSIMO, "altre")
                        + " hai venduto monete comprate in un'altra valuta, in giorni senza un cambio: escono dalle "
                        + "posizioni, ma quella parte non ha un risultato.")
                .filter(t -> !t.isEmpty()).collect(Collectors.joining(" "));
        return testo.isEmpty() ? Optional.empty() : Optional.of(testo);
    }

    /** B4 — Valute diverse. */
    private static Optional<String> b4(Dati d) {
        return !d.s.mixedQuoteCurrencies() ? Optional.empty() : Optional.of(
                "Le operazioni sono in " + unisci(d.s.quoteCurrencies())
                        + ": ogni importo è dato nella sua valuta, e le valute non si sommano fra loro.");
    }

    /** B5 — Spread dell'app. */
    private static Optional<String> b5(Dati d) {
        boolean app = d.s.totaliPerValuta().stream().anyMatch(t -> t.volumeApp().signum() > 0);
        return !app ? Optional.empty() : Optional.of(
                "Sulle operazioni fatte dall'app il costo vero è più alto di quello mostrato: oltre alla "
                        + "commissione c'è lo spread, che sta dentro il prezzo e Kraken non riporta.");
    }

    /** B6 — Metodo di calcolo, sempre in fondo. */
    private static Optional<String> b6() {
        return Optional.of("Il risultato è calcolato a costo medio ponderato su tutto lo storico del conto: "
                + "serve a leggere come hai operato e non vale come calcolo fiscale.");
    }

    // ------------------------------------------------------------------ D. Costi

    /** D1 — Ordini eseguiti a tariffa taker. */
    private Optional<Voce> d1(Dati d) {
        BigDecimal maker = BigDecimal.valueOf(TipoOrdine.LIMITE.commissione());
        List<String> frasi = new ArrayList<>();
        long acquistiTaker = 0;
        for (TotaliValuta t : d.s.totaliPerValuta()) {
            BigDecimal noto = t.volumeMaker().add(t.volumeTaker());
            if (noto.signum() == 0) {
                continue;
            }
            double quotaTaker = t.volumeTaker().multiply(CENTO).divide(noto, MC).doubleValue();
            if (quotaTaker < commento.quotaTakerMassima()) {
                continue;
            }
            List<Ordine> taker = d.trading.stream().filter(o -> o.valuta().equals(t.valuta())
                    && o.origine() == Origine.PRO && Boolean.TRUE.equals(o.taker())).toList();
            long acquisti = taker.stream().filter(Ordine::acquisto).count();
            long vendite = taker.size() - acquisti;
            acquistiTaker += acquisti;
            List<String> lati = new ArrayList<>();
            if (acquisti > 0) lati.add(plurale(acquisti, "acquisto", "acquisti"));
            if (vendite > 0) lati.add(plurale(vendite, "vendita", "vendite"));
            BigDecimal inPiu = t.commissioniTaker().subtract(t.volumeTaker().multiply(maker));
            String costo = inPiu.signum() <= 0 || t.commissioni().signum() == 0 ? "" : "%s in più, %s delle commissioni"
                    .formatted(importo(inPiu, t.valuta()),
                            articolo(quota(inPiu.multiply(CENTO).divide(t.commissioni(), MC).doubleValue())));
            // La prima valuta porta la spiegazione intera, le altre solo i loro numeri.
            frasi.add(frasi.isEmpty()
                    ? "Su Kraken Pro %s del volume in %s è stato eseguito subito, alla tariffa taker: %s.%s".formatted(
                            articolo(quota(quotaTaker)), t.valuta(), unisci(lati), costo.isEmpty() ? ""
                                    : " Rispetto alla tariffa maker (%s) hai pagato %s.".formatted(
                                            percentuale(maker.multiply(CENTO)), costo))
                    : "In %s %s, con %s%s.".formatted(t.valuta(), articolo(quota(quotaTaker)), unisci(lati),
                            costo.isEmpty() ? "" : ": " + costo));
        }
        if (frasi.isEmpty()) {
            return Optional.empty();
        }
        String domanda = acquistiTaker == 0 ? null : (acquistiTaker == 1
                ? "Nell'acquisto eseguito subito, serviva entrare a quel prezzo o un ordine limite sarebbe bastato?"
                : "Nei " + acquistiTaker + " acquisti eseguiti subito, serviva entrare a quel prezzo "
                        + "o un ordine limite sarebbe bastato?");
        return Voce.nuova(String.join(" ", frasi), domanda);
    }

    /** D2 — Operazioni dall'app. */
    private static Optional<Voce> d2(Dati d) {
        long ordini = d.trading.stream().filter(o -> o.origine() == Origine.APP).count();
        if (ordini == 0) {
            return Optional.empty();
        }
        List<String> parti = d.s.totaliPerValuta().stream().filter(t -> t.volumeApp().signum() > 0)
                .map(t -> importo(t.volumeApp(), t.valuta()) + " con una commissione media "
                        + di(percentuale(t.commissioniApp().multiply(CENTO).divide(t.volumeApp(), MC))))
                .toList();
        String maker = percentuale(BigDecimal.valueOf(TipoOrdine.LIMITE.commissione()).multiply(CENTO));
        return Voce.nuova("%s dall'app: %s, più lo spread che sta nel prezzo, contro %s di un ordine limite su Kraken Pro."
                        .formatted(plurale(ordini, "ordine è passato", "ordini sono passati"), unisci(parti),
                                articolo(maker)),
                "Le operazioni fatte dall'app si potevano fare da Kraken Pro con un ordine limite?");
    }

    /** D3 — Pareggio, con la formula di piano e registro. */
    private static Optional<Voce> d3(Dati d) {
        List<TotaliValuta> totali = d.s.totaliPerValuta().stream()
                .filter(t -> t.commissioniPercentuale() != null).toList();
        Optional<TotaliValuta> scelta = totali.stream().filter(t -> t.valuta().equals(d.valutaWatchlist)).findFirst()
                .or(() -> totali.stream().max(Comparator.comparing(TotaliValuta::volume)));
        if (scelta.isEmpty()) {
            return Optional.empty();
        }
        TotaliValuta t = scelta.get();
        double perLato = t.commissioniPercentuale().doubleValue() / 100;
        double salita = (MatematicaOperazione.pareggio(1, Verso.ACQUISTO, perLato, perLato) - 1) * 100;
        return Voce.nuova("Con commissioni medie %s per lato%s, il prezzo deve salire almeno %s solo per chiudere in pari."
                .formatted(di(percentuale(t.commissioniPercentuale())), totali.size() > 1 ? " in " + t.valuta() : "",
                        di(percentuale(BigDecimal.valueOf(salita)))));
    }

    /** D4 — Conversioni valutarie. */
    private static Optional<Voce> d4(Dati d) {
        if (d.conversioni.isEmpty()) {
            return Optional.empty();
        }
        int n = d.conversioni.size();
        List<PairStats> cambi = d.s.byPair().stream().filter(PairStats::fxConversion).toList();
        String costo = unisci(cambi.stream().map(p -> importo(p.feesPaid(), p.quoteCurrency())).toList());
        String quota = cambi.size() == 1 && cambi.get(0).feesToVolumePercent() != null
                ? " (" + articolo(percentuale(cambi.get(0).feesToVolumePercent())) + " del convertito)" : "";
        String testo = "Hai fatto %s, %s %s%s: un costo che dipende dalla valuta delle coppie scelte, non dal mercato."
                .formatted(plurale(n, "conversione valutaria", "conversioni valutarie"), n == 1 ? "costata" : "costate",
                        costo, quota);
        String domanda = (n == 1 ? "Quella conversione era necessaria" : "Quelle conversioni erano necessarie")
                + (d.valutaWatchlist == null ? "?"
                : ", o la stessa operazione si poteva fare su una coppia in " + d.valutaWatchlist + "?");
        return Voce.nuova(testo, domanda);
    }

    // ------------------------------------------------------------------ E. Metodo e comportamento

    /** E8 — Acquisti senza un'operazione reale nel registro. */
    private Optional<Voce> e8(Dati d) {
        int totali = d.acquistiNelMetodo.size();
        List<Ordine> senza = nonRegistrati(d.acquistiNelMetodo, d.reali);
        if (senza.isEmpty()) {
            return Optional.empty();
        }
        List<String> voci = senza.stream().map(o -> o.coppia() + " del " + data(o.istante(), fuso)).toList();
        String testo = senza.size() == 1 && totali == 1
                ? "L'acquisto di " + voci.get(0) + " non ha un'operazione reale nel registro."
                : senza.size() == 1
                ? "1 acquisto su %d non ha un'operazione reale nel registro: %s.".formatted(totali, voci.get(0))
                : "%d acquisti su %d non hanno un'operazione reale nel registro: %s."
                        .formatted(senza.size(), totali, elenco(voci, ELENCO_MASSIMO, "altri"));
        return Voce.nuova(testo, "Dove erano scritti stop, obiettivo e motivo di "
                + (senza.size() == 1 ? "quell'acquisto?" : "quegli acquisti?"));
    }

    /**
     * Gli acquisti che nessuna operazione reale copre. Un'operazione copre gli
     * acquisti sulla stessa coppia, nei giorni dopo la registrazione, finche'
     * non ne ha esaurito la quantita': un'operazione eseguita in due ordini e'
     * coperta, un secondo acquisto dopo averla gia' riempita no. Un acquisto
     * piu' grande del registrato resta coperto: la dimensione la guarda E9.
     */
    private List<Ordine> nonRegistrati(List<Ordine> acquisti, List<OperazioneRegistrata> reali) {
        Duration finestra = Duration.ofDays(commento.giorniRegistrazione());
        Map<OperazioneRegistrata, BigDecimal> residuo = new IdentityHashMap<>();
        reali.forEach(r -> residuo.put(r, BigDecimal.valueOf(r.quantita())));
        List<Ordine> senza = new ArrayList<>();
        for (Ordine acquisto : acquisti.stream().sorted(Comparator.comparing(Ordine::istante)).toList()) {
            Optional<OperazioneRegistrata> copre = reali.stream()
                    .filter(r -> stessaCoppia(r, acquisto)
                            && !r.dataRegistrazione().isAfter(acquisto.istante())
                            && !r.dataRegistrazione().plus(finestra).isBefore(acquisto.istante())
                            && residuo.get(r).compareTo(BigDecimal.valueOf(r.quantita() * QUOTA_ESAURITA)) > 0)
                    .findFirst();
            copre.ifPresentOrElse(r -> residuo.merge(r, acquisto.quantita().negate(), BigDecimal::add),
                    () -> senza.add(acquisto));
        }
        return senza;
    }

    /** Il registro accetta XBTEUR, XBT/EUR e BTC/EUR: tutte la stessa coppia degli ordini. */
    private boolean stessaCoppia(OperazioneRegistrata registrata, Ordine acquisto) {
        PairResolver.Pair coppia = pairResolver.resolve(registrata.coppia().replace("/", ""));
        return coppia.base().equals(acquisto.moneta()) && coppia.quote().equals(acquisto.valuta());
    }

    /**
     * E9 — Posizioni oltre la dimensione che il metodo consente. Conta la
     * posizione, non il singolo ordine: due acquisti da 25 EUR sulla stessa
     * moneta sono una posizione da 50. Per un giro chiuso e' il costo piu'
     * alto tenuto insieme, per una posizione aperta il costo di oggi.
     */
    private Optional<Voce> e9(Dati d) {
        if (d.valutaWatchlist == null) {
            return Optional.empty();
        }
        double massimo = dimensioneMassima();
        List<String> oltre = Stream.concat(
                d.s.operazioniChiuse().stream()
                        .filter(o -> d.nelMetodo(o.apertura()) && o.valuta().equals(d.valutaWatchlist)
                                && o.costoMassimo() != null && o.costoMassimo().doubleValue() > massimo)
                        .map(o -> o.coppia() + " per " + importo(o.costoMassimo(), o.valuta())),
                d.s.posizioniAperte().stream()
                        .filter(p -> d.nelMetodo(p.apertaDal()) && p.valuta().equals(d.valutaWatchlist)
                                && p.costo().doubleValue() > massimo)
                        .map(p -> p.coppia() + " per " + importo(p.costo(), p.valuta()))).toList();
        if (oltre.isEmpty()) {
            return Optional.empty();
        }
        String testo = "%s %s i %s %s, la dimensione più alta che il metodo consente con il rischio %s "
                + "e lo stop più stretto ammesso (%s): %s.";
        return Voce.nuova(testo.formatted(plurale(oltre.size(), "posizione", "posizioni"), oltre.size() == 1 ? "supera" : "superano",
                        numero(massimo, 0), d.valutaWatchlist, di(quota(scansione.rischioPerOperazione())),
                        quota(scansione.ampiezzaStopMinima()), elenco(oltre, ELENCO_MASSIMO, "altre")),
                "Con quale stop avevi calcolato la quantità di " + (oltre.size() == 1 ? "quella posizione?" : "quelle posizioni?"));
    }

    /**
     * Il controvalore che, con lo stop piu' stretto ammesso, rischia esattamente
     * la quota scelta del capitale. Con uno stop piu' largo la dimensione giusta
     * e' piu' piccola: sopra questo numero il rischio e' oltre il metodo comunque.
     */
    double dimensioneMassima() {
        double rischioPerEuro = MatematicaOperazione.rischioPerUnita(1, 1 - scansione.ampiezzaStopMinima() / 100,
                scansione.tipoOrdine());
        return scansione.rischioAmmesso() / rischioPerEuro;
    }

    /** E10 — Piu' posizioni aperte insieme di quelle previste. */
    private Optional<Voce> e10(Dati d) {
        List<Instant[]> intervalli = new ArrayList<>();
        // Conta anche chi era aperta prima del metodo ed e' rimasta aperta dopo:
        // occupa un posto lo stesso. Si misura solo da quando vale il metodo.
        d.s.operazioniChiuse().stream().filter(o -> d.nelMetodo(o.chiusura()))
                .forEach(o -> intervalli.add(new Instant[] {d.dalMetodo(o.apertura()), o.chiusura()}));
        aperteUnaPerMoneta(d).values()
                .forEach(p -> intervalli.add(new Instant[] {d.dalMetodo(p.apertaDal()), d.s.periodEnd()}));

        // Chi chiude in un istante non si somma a chi apre nello stesso istante.
        List<Map.Entry<Instant, Integer>> eventi = new ArrayList<>();
        intervalli.forEach(i -> {
            eventi.add(Map.entry(i[0], 1));
            eventi.add(Map.entry(i[1], -1));
        });
        eventi.sort(Map.Entry.<Instant, Integer>comparingByKey().thenComparing(Map.Entry.<Instant, Integer>comparingByValue()));
        int aperte = 0;
        int massimo = 0;
        for (var evento : eventi) {
            aperte += evento.getValue();
            massimo = Math.max(massimo, aperte);
        }
        return massimo <= scansione.posizioniMassime() ? Optional.empty() : Voce.nuova(
                "Nel periodo hai tenuto fino a %d posizioni aperte insieme: la guida ne prevede al massimo %d."
                        .formatted(massimo, scansione.posizioniMassime()));
    }

    /** Una moneta in carico in due valute e' una posizione sola. */
    private static Map<String, PosizioneAperta> aperteUnaPerMoneta(Dati d) {
        return d.s.posizioniAperte().stream().collect(Collectors.toMap(PosizioneAperta::moneta, Function.identity(),
                (prima, seconda) -> prima, LinkedHashMap::new));
    }

    /** E7 — Oltre il time stop. */
    private Optional<Voce> e7(Dati d) {
        int limite = scansione.giorniTimeStop();
        List<String> chiuse = d.s.operazioniChiuse().stream()
                .filter(o -> d.nelMetodo(o.apertura()) && giorniIniziati(o.apertura(), o.chiusura()) > limite)
                .map(o -> o.coppia() + " (" + plurale(giorniIniziati(o.apertura(), o.chiusura()), "giorno", "giorni") + ")")
                .toList();
        List<String> aperte = aperteUnaPerMoneta(d).values().stream()
                .filter(p -> d.nelMetodo(p.apertaDal()) && giorniIniziati(p.apertaDal(), d.s.periodEnd()) > limite)
                .map(p -> p.coppia() + " (" + plurale(giorniIniziati(p.apertaDal(), d.s.periodEnd()), "giorno", "giorni") + ")")
                .toList();
        List<String> frasi = new ArrayList<>();
        if (!chiuse.isEmpty()) {
            frasi.add("%s più dei %d giorni del time stop: %s.".formatted(
                    plurale(chiuse.size(), "operazione è durata", "operazioni sono durate"), limite,
                    elenco(chiuse, ELENCO_MASSIMO, "altre")));
        }
        if (!aperte.isEmpty()) {
            frasi.add("%s da più di %d giorni: %s.".formatted(
                    aperte.size() == 1 ? "È aperta" : "Sono aperte", limite, elenco(aperte, ELENCO_MASSIMO, "altre")));
        }
        return frasi.isEmpty() ? Optional.empty()
                : Voce.nuova(String.join(" ", frasi), "Cosa ti ha tenuto dentro oltre il time stop?");
    }

    /**
     * I giorni toccati, contando quello in corso: 15 giorni e 23 ore sono gia'
     * il sedicesimo, oltre un time stop di 15. Troncando diventavano 15.
     */
    private static long giorniIniziati(Instant da, Instant a) {
        return Math.ceilDiv(Duration.between(da, a).getSeconds(), Duration.ofDays(1).getSeconds());
    }

    /** E1 — Acquisti fuori watchlist. */
    private static Optional<Voce> e1(Dati d) {
        List<Ordine> fuori = d.acquistiNelMetodo.stream().filter(o -> !d.moneteWatchlist.contains(o.moneta())).toList();
        if (fuori.isEmpty()) {
            return Optional.empty();
        }
        List<String> coppie = fuori.stream().map(Ordine::coppia).distinct().toList();
        int totali = d.acquistiNelMetodo.size();
        String quali = fuori.size() < totali
                ? fuori.size() + (fuori.size() == 1 ? " acquisto su " + totali + " è su una moneta" : " acquisti su " + totali + " sono su monete")
                : totali == 1 ? "L'unico acquisto è su una moneta" : "Tutti e " + totali + " gli acquisti sono su monete";
        return Voce.nuova(quali + " fuori dalla watchlist: " + elenco(coppie, ELENCO_MASSIMO, "altre") + ".",
                "Gli acquisti fuori watchlist sono passati dalla checklist della scansione, o sono nati fuori dal metodo?");
    }

    /** E3 — Troppi ordini in un giorno. */
    private Optional<Voce> e3(Dati d) {
        Map<LocalDate, Long> perGiorno = d.trading.stream().filter(o -> d.nelMetodo(o.istante()))
                .collect(Collectors.groupingBy(o -> o.istante().atZone(fuso).toLocalDate(), Collectors.counting()));
        int soglia = commento.ordiniAlGiornoMassimi();
        long oltre = perGiorno.values().stream().filter(n -> n > soglia).count();
        return oltre == 0 ? Optional.empty() : Voce.nuova(
                "In %s su %d hai fatto più di %s: la guida prevede una revisione al giorno, a orario fisso."
                        .formatted(plurale(oltre, "giorno", "giorni"), perGiorno.size(),
                                plurale(soglia, "ordine", "ordini")),
                "Quanti di quegli ordini erano previsti dalla revisione del mattino?");
    }

    /** E2 — Concentrazione su una coppia, dentro ogni valuta. */
    private Optional<Voce> e2(Dati d) {
        List<String> frasi = new ArrayList<>();
        String primaCoppia = null;
        for (TotaliValuta t : d.s.totaliPerValuta()) {
            List<PairStats> coppie = trading(d).filter(p -> p.quoteCurrency().equals(t.valuta())).toList();
            if (coppie.size() < 2 || t.volume().signum() == 0) {
                continue;
            }
            PairStats prima = coppie.stream().max(Comparator.comparing(PairStats::volumeQuote)).orElseThrow();
            double quotaCoppia = prima.volumeQuote().multiply(CENTO).divide(t.volume(), MC).doubleValue();
            if (quotaCoppia > commento.concentrazioneMassima()) {
                frasi.add("%s da sola fa %s del volume in %s.".formatted(prima.displayName(),
                        articolo(quota(quotaCoppia)), t.valuta()));
                primaCoppia = primaCoppia == null ? prima.displayName() : primaCoppia;
            }
        }
        return frasi.isEmpty() ? Optional.empty() : Voce.nuova(String.join(" ", frasi),
                "Concentrare il volume su " + primaCoppia + " è stata una scelta, o è successo?");
    }

    /** E4 — Posizioni ancora aperte. */
    private Optional<Voce> e4(Dati d) {
        List<PosizioneAperta> aperte = d.s.posizioniAperte();
        if (aperte.isEmpty()) {
            return Optional.empty();
        }
        if (aperte.size() == 1) {
            PosizioneAperta p = aperte.get(0);
            return Voce.nuova("Resta aperta una posizione su %s, dal %s, con un costo di carico di %s: "
                    .formatted(p.coppia(), data(p.apertaDal(), fuso), importo(p.costo(), p.valuta()))
                    + "il suo risultato non è ancora realizzato.");
        }
        List<String> voci = aperte.stream().map(p -> "%s dal %s (%s)".formatted(p.coppia(),
                data(p.apertaDal(), fuso), importo(p.costo(), p.valuta()))).toList();
        return Voce.nuova("Restano aperte %d posizioni: %s. Il loro risultato non è ancora realizzato."
                .formatted(aperte.size(), elenco(voci, ELENCO_MASSIMO, "altre")));
    }

    /** E6 — Risultato per operazione chiusa, dalla piu' recente. */
    private static Optional<Voce> e6(Dati d) {
        if (d.s.operazioniChiuse().size() < 2) {
            return Optional.empty();
        }
        List<String> voci = d.s.operazioniChiuse().stream()
                .sorted(Comparator.comparing(OperazioneChiusa::chiusura).reversed())
                .map(o -> o.coppia() + " " + (o.risultato() == null ? "non calcolabile" : conSegno(o.risultato(), o.valuta()))
                        + " " + durata(o.giorni()))
                .toList();
        return Voce.nuova("Operazioni chiuse nel periodo: " + elenco(voci, ELENCO_MASSIMO, "altre") + ".");
    }

    private static Stream<PairStats> trading(Dati d) {
        return d.s.byPair().stream().filter(p -> !p.fxConversion());
    }
}
