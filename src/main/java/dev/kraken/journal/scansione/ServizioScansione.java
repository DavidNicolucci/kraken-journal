package dev.kraken.journal.scansione;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.kraken.journal.kraken.KrakenApiException;
import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.mercato.Intervallo;
import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.mercato.dto.Quotazione;
import dev.kraken.journal.scansione.dto.Candidato;
import dev.kraken.journal.scansione.dto.EsitoControllo;
import dev.kraken.journal.scansione.dto.Indicatori;
import dev.kraken.journal.scansione.dto.Piano;
import dev.kraken.journal.scansione.dto.RispostaScansione;

/**
 * Applica la checklist della guida a tutta la watchlist.
 *
 * Sulle prestazioni: il calcolo non conta nulla, sono qualche migliaio di
 * righe. Il costo sta tutto nelle chiamate HTTP, quindi le coppie vengono
 * esaminate in parallelo su thread virtuali e le candele sono in cache per
 * giornata dentro MercatoClient, che limita anche quante chiamate partono
 * insieme. Ottimizzare i cicli qui sarebbe lavoro sprecato nel posto sbagliato.
 *
 * Una coppia che fallisce non ferma la scansione: viene loggata e saltata, e
 * il conteggio in uscita rende visibile che manca. Vale anche per le
 * quotazioni: se Kraken rifiuta la richiesta in blocco, per esempio perche'
 * una coppia della watchlist e' stata tolta dal listino, si chiedono una per
 * una, e solo quella che non esiste piu' resta fuori.
 */
@Service
public class ServizioScansione {

    private static final Logger log = LoggerFactory.getLogger(ServizioScansione.class);

    private final MercatoClient mercatoClient;
    private final ValutatoreSetup valutatore;
    private final ProprietaScansione proprieta;
    private final PairResolver pairResolver;

    public ServizioScansione(MercatoClient mercatoClient, ValutatoreSetup valutatore,
                             ProprietaScansione proprieta, PairResolver pairResolver) {
        this.mercatoClient = mercatoClient;
        this.valutatore = valutatore;
        this.proprieta = proprieta;
        this.pairResolver = pairResolver;
    }

    public RispostaScansione scansiona() {
        Map<String, Quotazione> quotazioni = quotazioniInBlocco();

        List<Candidato> candidati;
        try (var esecutore = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Candidato>> futuri = proprieta.watchlist().stream()
                    .map(coppia -> esecutore.submit(() -> esamina(coppia, quotazioni)))
                    .toList();
            candidati = futuri.stream().map(ServizioScansione::attendi).filter(Objects::nonNull).toList();
        }

        return new RispostaScansione(
                Instant.now(),
                candidati.size(),
                proprieta.watchlist().size(),
                candidati.stream()
                        .sorted(Comparator.comparingLong(Candidato::superati).reversed()
                                .thenComparingDouble(c -> -c.quotazione().controvalore24h()))
                        .toList());
    }

    /** Mappa vuota se la richiesta in blocco viene rifiutata: ogni coppia chiedera' la sua. */
    private Map<String, Quotazione> quotazioniInBlocco() {
        try {
            return mercatoClient.quotazioni(proprieta.watchlist());
        } catch (KrakenApiException e) {
            log.warn("Quotazioni in blocco rifiutate, le chiedo una coppia per volta: {}", e.getMessage());
            return Map.of();
        }
    }

    private Candidato esamina(String coppia, Map<String, Quotazione> quotazioni) {
        Quotazione quotazione = trovaQuotazione(coppia, quotazioni);
        if (quotazione == null) {
            quotazione = trovaQuotazione(coppia, mercatoClient.quotazioni(List.of(coppia)));
        }
        if (quotazione == null) {
            throw new IllegalStateException("Quotazione mancante per " + coppia);
        }
        Indicatori indicatori = CalcolatoreIndicatori.calcola(
                mercatoClient.candeleChiuse(coppia, Intervallo.GIORNALIERO),
                proprieta.giorniMinimoStop(), proprieta.giorniBreakout());
        Piano piano = valutatore.valuta(quotazione.ultimo(), indicatori);
        return Candidato.di(coppia, quotazione, indicatori, piano, controlla(quotazione, indicatori, piano));
    }

    /**
     * Le soglie si confrontano con il valore che la pagina mostra, arrotondato
     * come lei: uno stop al 4,96% si legge 5,0%, e bocciarlo contro "tra 5% e 10%"
     * sembrava un errore. I prezzi restano interi, con sei cifre significative
     * a video: con quattro decimali fissi PEPE diventava zero.
     */
    private List<EsitoControllo> controlla(Quotazione quotazione, Indicatori i, Piano piano) {
        double controvalore = arrotondato(quotazione.controvalore24h(), 0);
        double multiploVolume = arrotondato(i.multiploVolume(), 1);
        double distanza = arrotondato(Math.abs(i.distanzaDallaMedia()), 1);
        double ultimo = quotazione.ultimo();
        double distanzaAttuale = arrotondato(Math.abs(ultimo / i.mediaMobile20() - 1) * 100, 1);
        double ampiezzaStop = arrotondato(piano.ampiezzaStop(), 1);
        return List.of(
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
                                + arrotonda(proprieta.ampiezzaStopMassima(), 0) + "%"));
    }

    /**
     * Kraken risponde con il nome interno della coppia, che non sempre coincide
     * con quello richiesto: si chiede XBTUSD e torna XXBTZUSD. Il confronto
     * passa da PairResolver, che toglie solo i prefissi storici: togliere
     * tutte le X e le Z faceva di AVAXEUR la quotazione di AVAEUR.
     *
     * @return null se la coppia non c'e'.
     */
    Quotazione trovaQuotazione(String coppia, Map<String, Quotazione> quotazioni) {
        Quotazione diretta = quotazioni.get(coppia);
        if (diretta != null) {
            return diretta;
        }
        String cercata = pairResolver.resolve(coppia).display();
        return quotazioni.values().stream()
                .filter(q -> pairResolver.resolve(q.coppia()).display().equals(cercata))
                .findFirst()
                .orElse(null);
    }

    private static Candidato attendi(Future<Candidato> futuro) {
        try {
            return futuro.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("Coppia saltata durante la scansione: {}", e.getMessage());
            return null;
        }
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
