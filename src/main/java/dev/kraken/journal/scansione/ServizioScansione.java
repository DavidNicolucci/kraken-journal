package dev.kraken.journal.scansione;

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

    public ServizioScansione(MercatoClient mercatoClient, ValutatoreSetup valutatore,
                             ProprietaScansione proprieta) {
        this.mercatoClient = mercatoClient;
        this.valutatore = valutatore;
        this.proprieta = proprieta;
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
                proprieta.giorniMinimoStop());
        Piano piano = valutatore.valuta(quotazione.ultimo(), indicatori);
        return Candidato.di(coppia, quotazione, indicatori, piano, controlla(quotazione, indicatori, piano));
    }

    private List<EsitoControllo> controlla(Quotazione quotazione, Indicatori i, Piano piano) {
        return List.of(
                EsitoControllo.di(Controllo.LIQUIDITA,
                        quotazione.controvalore24h() >= proprieta.volumeMinimo(),
                        arrotonda(quotazione.controvalore24h(), 0),
                        ">= " + arrotonda(proprieta.volumeMinimo(), 0)),
                EsitoControllo.di(Controllo.TREND,
                        i.mediaMobileInSalita() && i.chiusura() > i.mediaMobile20(),
                        i.mediaMobileInSalita() ? "media in salita" : "media in discesa",
                        "media in salita e prezzo sopra"),
                EsitoControllo.di(Controllo.MOMENTO,
                        i.rsi14() >= proprieta.rsiMinimo() && i.rsi14() <= proprieta.rsiMassimo(),
                        arrotonda(i.rsi14(), 1),
                        "tra " + arrotonda(proprieta.rsiMinimo(), 0) + " e " + arrotonda(proprieta.rsiMassimo(), 0)),
                EsitoControllo.di(Controllo.DISTANZA_DALLA_MEDIA,
                        i.distanzaDallaMedia() <= proprieta.distanzaMassimaDallaMedia(),
                        arrotonda(i.distanzaDallaMedia(), 1) + "%",
                        "<= " + arrotonda(proprieta.distanzaMassimaDallaMedia(), 0) + "%"),
                EsitoControllo.di(Controllo.AMPIEZZA_STOP,
                        piano.ampiezzaStop() >= proprieta.ampiezzaStopMinima()
                                && piano.ampiezzaStop() <= proprieta.ampiezzaStopMassima(),
                        arrotonda(piano.ampiezzaStop(), 1) + "%",
                        "tra " + arrotonda(proprieta.ampiezzaStopMinima(), 0) + "% e "
                                + arrotonda(proprieta.ampiezzaStopMassima(), 0) + "%"),
                EsitoControllo.di(Controllo.RESISTENZA_INTERMEDIA,
                        i.massimo30() <= piano.entrata() || i.massimo30() >= piano.obiettivo(),
                        "massimo 30g " + arrotonda(i.massimo30(), 4),
                        "fuori da " + arrotonda(piano.entrata(), 4) + " - " + arrotonda(piano.obiettivo(), 4)));
    }

    /**
     * Kraken risponde con il nome interno della coppia, che non sempre coincide
     * con quello richiesto: si chiede XBTUSD e torna XXBTZUSD.
     *
     * @return null se la coppia non c'e'.
     */
    private static Quotazione trovaQuotazione(String coppia, Map<String, Quotazione> quotazioni) {
        Quotazione diretta = quotazioni.get(coppia);
        if (diretta != null) {
            return diretta;
        }
        return quotazioni.values().stream()
                .filter(q -> q.coppia().replace("X", "").replace("Z", "")
                        .equals(coppia.replace("X", "").replace("Z", "")))
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
}
