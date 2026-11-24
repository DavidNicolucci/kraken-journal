package dev.kraken.journal.kraken;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Kraken usa una nomenclatura storica per le coppie: prefisso X per le cripto
 * e Z per le valute fiat nei mercati piu' vecchi (XETHZEUR), nessun prefisso
 * in quelli recenti (SOLUSD). Questa classe separa base e quote e riconosce
 * le conversioni valutarie.
 *
 * Perche' serve: una conversione EUR->USD non e' un trade. Contarla come tale
 * gonfia il numero di operazioni e falsa ogni metrica di frequenza.
 */
@Component
public class PairResolver {

    /** Ordine importante: i suffissi piu' lunghi vanno testati per primi. */
    private static final List<String> QUOTES = List.of(
            "ZUSD", "ZEUR", "ZGBP", "ZJPY", "ZCAD", "ZAUD", "ZCHF",
            "USDT", "USDC", "USDG", "DAI",
            "XXBT", "XETH",
            "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "XBT", "ETH");

    private static final Set<String> FIAT = Set.of(
            "USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF");

    /**
     * I soli codici che Kraken scrive con il prefisso storico. Un elenco e non
     * una regola sulla lunghezza: "ogni codice di quattro lettere che inizia
     * per X o Z" tagliava anche ZETA e ZEUS, che diventavano ETA ed EUS.
     */
    private static final Set<String> CODICI_STORICI = Set.of(
            "XXBT", "XETH", "XLTC", "XXRP", "XXLM", "XZEC", "XXMR", "XETC", "XREP", "XMLN", "XXDG",
            "ZUSD", "ZEUR", "ZGBP", "ZJPY", "ZCAD", "ZAUD", "ZCHF");

    /**
     * I nomi che usa il resto del mondo, e l'interfaccia di Kraken stessa, per
     * le monete che l'API chiama in un altro modo: chi scrive BTC/EUR nel
     * registro intende la XBT/EUR dei trade.
     */
    private static final Map<String, String> ALIAS = Map.of("BTC", "XBT", "DOGE", "XDG");

    public record Pair(String raw, String base, String quote, boolean fxConversion) {
        public String display() {
            return base + "/" + quote;
        }
    }

    public Pair resolve(String rawPair) {
        if (rawPair == null || rawPair.isBlank()) {
            return new Pair(rawPair, "?", "?", false);
        }
        String upper = rawPair.toUpperCase();

        for (String quote : QUOTES) {
            if (upper.length() > quote.length() && upper.endsWith(quote)) {
                String base = upper.substring(0, upper.length() - quote.length());
                // Una quotazione col prefisso storico non segue mai una base di
                // una o due lettere: in XTZEUR la Z appartiene a XTZ, non a ZEUR.
                if (CODICI_STORICI.contains(quote) && base.length() < 3) {
                    continue;
                }
                return componi(rawPair, base, quote);
            }
        }
        return new Pair(rawPair, upper, "?", false);
    }

    /**
     * La coppia a partire dai due codici separati, come li riporta il ledger
     * (ZEUR, XXBT, SOL.F): serve per le operazioni fatte dall'app, che non
     * hanno un nome di coppia.
     */
    public Pair coppia(String base, String quote) {
        return componi(asset(base) + asset(quote), base, quote);
    }

    /** Il codice di una moneta come lo si legge: ZEUR -> EUR, XXBT -> XBT, SOL.F -> SOL, BTC -> XBT. */
    public String asset(String codice) {
        String pulito = codice.toUpperCase().replaceFirst("\\..*$", "");
        String senzaPrefisso = CODICI_STORICI.contains(pulito) ? pulito.substring(1) : pulito;
        return ALIAS.getOrDefault(senzaPrefisso, senzaPrefisso);
    }

    /**
     * Il nome con cui la coppia si chiede a Kraken e si scrive nel registro,
     * lo stesso della watchlist: XBT/EUR, btceur e XXBTZEUR diventano XBTEUR.
     *
     * @return null se non e' una coppia: quotazione sconosciuta o caratteri
     *         che non stanno nel nome di una coppia, e che finirebbero tali e
     *         quali nell'indirizzo della chiamata a Kraken.
     */
    public String altname(String coppia) {
        Pair pair = resolve(coppia == null ? null : coppia.strip().replace("/", ""));
        String nome = pair.base() + pair.quote();
        return "?".equals(pair.quote()) || !nome.matches("[A-Z0-9]+") ? null : nome;
    }

    public boolean fiat(String codice) {
        return FIAT.contains(asset(codice));
    }

    private Pair componi(String raw, String base, String quote) {
        return new Pair(raw, asset(base), asset(quote), fiat(base) && fiat(quote));
    }
}
