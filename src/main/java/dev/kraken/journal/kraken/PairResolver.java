package dev.kraken.journal.kraken;

import java.util.List;
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
                String base = normalize(upper.substring(0, upper.length() - quote.length()));
                String normalizedQuote = normalize(quote);
                boolean fx = FIAT.contains(base) && FIAT.contains(normalizedQuote);
                return new Pair(rawPair, base, normalizedQuote, fx);
            }
        }
        return new Pair(rawPair, upper, "?", false);
    }

    /**
     * Rimuove il prefisso storico X/Z solo quando cio' che resta e' un codice
     * valuta di tre lettere: XXBT -> XBT, ZEUR -> EUR, ma SOL resta SOL.
     */
    private static String normalize(String code) {
        if (code.length() == 4 && (code.charAt(0) == 'X' || code.charAt(0) == 'Z')) {
            return code.substring(1);
        }
        return code;
    }
}
