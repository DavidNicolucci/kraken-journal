package dev.kraken.journal.journal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Come si scrivono numeri, importi e elenchi nel commento.
 *
 * Tutto in un posto perche' sono le regole di formato del documento delle
 * regole (§6): virgola decimale, codice della valuta, segno esplicito,
 * articolo giusto davanti alle percentuali. Sparse nelle regole, ognuna le
 * avrebbe applicate un po' a modo suo.
 */
final class TestoItaliano {

    private static final DecimalFormatSymbols SIMBOLI = DecimalFormatSymbols.getInstance(Locale.ITALY);
    private static final String MENO = "−";

    private TestoItaliano() {
    }

    /** 1234.5 -> "1.234,50" con due decimali. */
    static String numero(BigDecimal valore, int decimali) {
        DecimalFormat formato = new DecimalFormat(decimali == 0 ? "#,##0" : "#,##0." + "0".repeat(decimali), SIMBOLI);
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valore);
    }

    static String numero(double valore, int decimali) {
        return numero(BigDecimal.valueOf(valore), decimali);
    }

    /** "1,95 EUR". */
    static String importo(BigDecimal valore, String valuta) {
        return numero(valore, 2) + " " + valuta;
    }

    /** "+1,95 EUR", "−0,80 EUR": il segno si vede sempre, anche sul guadagno. */
    static String conSegno(BigDecimal valore, String valuta) {
        String segno = valore.signum() > 0 ? "+" : valore.signum() < 0 ? MENO : "";
        return segno + importo(valore.abs(), valuta);
    }

    /** "0,72%": due decimali, per le commissioni. */
    static String percentuale(BigDecimal valore) {
        return numero(valore, 2) + "%";
    }

    /** "63%": senza decimali, per le quote di volume. */
    static String quota(double valore) {
        return numero(valore, 0) + "%";
    }

    /**
     * L'articolo davanti a un numero scritto: "lo 0,72%", "l'1,44%", "l'8%",
     * "l'11%", "l'80%", "il 63%". Si sceglie dalla prima parola che si
     * pronuncia: zero vuole "lo", una vocale vuole "l'".
     */
    static String articolo(String numeroScritto) {
        return forma(numeroScritto, "lo ", "l'", "il ");
    }

    /** "allo 0,72%", "all'1,44%", "al 63%". */
    static String a(String numeroScritto) {
        return forma(numeroScritto, "allo ", "all'", "al ");
    }

    /** "dello 0,72%", "dell'1,44%", "del 63%". */
    static String di(String numeroScritto) {
        return forma(numeroScritto, "dello ", "dell'", "del ");
    }

    private static String forma(String numeroScritto, String davantiAZero, String davantiAVocale, String altrimenti) {
        String cifre = numeroScritto.split(",")[0].replaceAll("[^0-9]", "");
        long intero = cifre.isEmpty() ? 0 : Long.parseLong(cifre);
        String articolo = intero == 0 ? davantiAZero : iniziaPerVocale(intero) ? davantiAVocale : altrimenti;
        return articolo + numeroScritto;
    }

    /** uno, otto, undici, ottanta..., ottocento..., ottomila..., undicimila... */
    private static boolean iniziaPerVocale(long n) {
        if (n == 1 || n == 8 || n == 11) {
            return true;
        }
        String cifre = Long.toString(n);
        return (cifre.charAt(0) == '8' && cifre.length() <= 4) || (cifre.startsWith("11") && cifre.length() == 5);
    }

    /** "1 ordine", "2 ordini". */
    static String plurale(long n, String singolare, String plurale) {
        return n + " " + (n == 1 ? singolare : plurale);
    }

    /** "a", "a e b", "a, b e c". */
    static String unisci(List<String> parti) {
        if (parti.size() <= 1) {
            return parti.isEmpty() ? "" : parti.get(0);
        }
        return String.join(", ", parti.subList(0, parti.size() - 1)) + " e " + parti.get(parti.size() - 1);
    }

    /**
     * Al massimo {@code massimo} voci, poi "e altre N".
     *
     * @param altre "altre" o "altri", secondo il genere delle voci.
     */
    static String elenco(List<String> voci, int massimo, String altre) {
        if (voci.size() <= massimo) {
            return unisci(voci);
        }
        return String.join(", ", voci.subList(0, massimo)) + " e " + altre + " " + (voci.size() - massimo);
    }

    /** "23 novembre 2026", nel fuso del lavoro giornaliero. */
    static String data(Instant istante, ZoneId fuso) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN).withZone(fuso).format(istante);
    }

    /** "in giornata", "in 1 giorno", "in 7 giorni". */
    static String durata(long giorni) {
        return giorni == 0 ? "in giornata" : "in " + plurale(giorni, "giorno", "giorni");
    }
}
