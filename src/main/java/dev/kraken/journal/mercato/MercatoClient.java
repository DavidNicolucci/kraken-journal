package dev.kraken.journal.mercato;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import dev.kraken.journal.kraken.KrakenApiException;
import dev.kraken.journal.kraken.Ritentativi;
import dev.kraken.journal.kraken.dto.KrakenEnvelope;
import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.mercato.dto.Quotazione;

/**
 * Client per gli endpoint PUBBLICI di Kraken: candele e quotazioni.
 *
 * Perche' una classe separata da KrakenClient: quello gestisce le chiamate
 * private, con nonce e firma HMAC, e il suo Javadoc dichiara quella
 * responsabilita'. Qui non serve nessuna chiave, quindi mescolarli
 * significherebbe far passare dati pubblici attraverso la catena di firma per
 * nessun motivo. Riusa pero' lo stesso bean RestClient, lo stesso involucro
 * KrakenEnvelope e la stessa eccezione: l'integrazione e' una sola.
 *
 * Gli endpoint pubblici hanno un limite di frequenza e la scansione chiama
 * da piu' thread insieme: qui le chiamate contemporanee sono al massimo
 * {@value #CHIAMATE_CONTEMPORANEE}, e un rifiuto per troppe richieste viene
 * ritentato invece di far sparire la coppia dalla scansione del giorno.
 */
@Component
public class MercatoClient {

    private static final Logger log = LoggerFactory.getLogger(MercatoClient.class);
    private static final String CANDELE = "/0/public/OHLC";
    private static final String QUOTAZIONI = "/0/public/Ticker";
    /** Kraken mette in coda ai risultati OHLC una chiave di servizio. */
    private static final String CHIAVE_DI_SERVIZIO = "last";
    private static final int CHIAMATE_CONTEMPORANEE = 2;
    private static final Duration ATTESA_LIMITE = Duration.ofSeconds(1);

    private final RestClient restClient;
    private final Duration attesaLimite;
    /** Vale per tutti i chiamanti, non solo per la scansione: il limite e' per indirizzo. */
    private final Semaphore permessi = new Semaphore(CHIAMATE_CONTEMPORANEE);
    /** Le candele giornaliere cambiano una volta al giorno: riscaricarle a ogni
     *  scansione e' l'unico spreco vero di questo modulo. */
    private final Map<ChiaveCache, List<Candela>> cache = new ConcurrentHashMap<>();

    private record ChiaveCache(String coppia, Intervallo intervallo, LocalDate giorno) {}

    @Autowired
    public MercatoClient(RestClient krakenRestClient) {
        this(krakenRestClient, ATTESA_LIMITE);
    }

    /** Per i test: un'attesa vera fra i tentativi li renderebbe lenti. */
    MercatoClient(RestClient krakenRestClient, Duration attesaLimite) {
        this.restClient = krakenRestClient;
        this.attesaLimite = attesaLimite;
    }

    /**
     * Candele CHIUSE, cioe' senza quella in formazione.
     *
     * Il nome dice la regola apposta, perche' e' l'errore piu' costoso del
     * dominio: la candela di oggi ha un volume parziale e una chiusura che non
     * e' ancora una chiusura. Calcolarci sopra medie e RSI produce numeri che
     * sembrano giusti e cambiano da soli nel pomeriggio. Non esiste un metodo
     * che restituisca anche la candela in corso: se servira', andra' chiesta
     * esplicitamente con un altro nome.
     */
    public List<Candela> candeleChiuse(String coppia, Intervallo intervallo) {
        ChiaveCache chiave = new ChiaveCache(coppia, intervallo, LocalDate.now(ZoneOffset.UTC));
        List<Candela> inCache = cache.get(chiave);
        if (inCache != null) {
            return inCache;
        }
        List<Candela> candele = scarica(coppia, intervallo);
        cache.keySet().removeIf(k -> !k.giorno().equals(chiave.giorno()));
        cache.put(chiave, candele);
        return candele;
    }

    /** Riepilogo a 24 ore delle coppie richieste, in una sola chiamata. */
    public Map<String, Quotazione> quotazioni(List<String> coppie) {
        JsonNode risultato = chiama(QUOTAZIONI + "?pair=" + String.join(",", coppie));
        Map<String, Quotazione> quotazioni = new LinkedHashMap<>();
        risultato.fields().forEachRemaining(campo ->
                quotazioni.put(campo.getKey(), leggiQuotazione(campo.getKey(), campo.getValue())));
        return quotazioni;
    }

    private List<Candela> scarica(String coppia, Intervallo intervallo) {
        JsonNode risultato = chiama(CANDELE + "?pair=" + coppia + "&interval=" + intervallo.minuti());
        List<Candela> candele = new ArrayList<>();
        risultato.fields().forEachRemaining(campo -> {
            if (!CHIAVE_DI_SERVIZIO.equals(campo.getKey())) {
                campo.getValue().forEach(riga -> candele.add(leggiCandela(riga)));
            }
        });
        if (candele.isEmpty()) {
            throw new KrakenApiException(CANDELE, List.of("nessuna candela per " + coppia));
        }
        log.debug("Scaricate {} candele {} per {}", candele.size(), intervallo, coppia);
        return List.copyOf(candele.subList(0, candele.size() - 1));
    }

    private JsonNode chiama(String uri) {
        return Ritentativi.conRitentativi(uri, attesaLimite, () -> conPermesso(uri));
    }

    /** Il permesso si tiene solo durante la chiamata, non durante l'attesa fra due tentativi. */
    private JsonNode conPermesso(String uri) {
        try {
            permessi.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chiamata a Kraken interrotta", e);
        }
        try {
            return chiamaUnaVolta(uri);
        } finally {
            permessi.release();
        }
    }

    private JsonNode chiamaUnaVolta(String uri) {
        KrakenEnvelope<JsonNode> busta = restClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<KrakenEnvelope<JsonNode>>() {});

        if (busta == null) {
            throw new KrakenApiException(uri, List.of("risposta vuota"));
        }
        if (busta.hasError()) {
            throw new KrakenApiException(uri, busta.error());
        }
        return busta.result();
    }

    /** Riga OHLC: [istante, apertura, massimo, minimo, chiusura, vwap, volume, scambi]. */
    private static Candela leggiCandela(JsonNode riga) {
        return new Candela(
                Instant.ofEpochSecond(riga.get(0).asLong()),
                riga.get(1).asDouble(),
                riga.get(2).asDouble(),
                riga.get(3).asDouble(),
                riga.get(4).asDouble(),
                riga.get(5).asDouble(),
                riga.get(6).asDouble());
    }

    /**
     * Nel ticker ogni campo e' una coppia [oggi, ultime 24 ore]: l'indice 1 e'
     * quello che serve, perche' l'indice 0 riparte da zero a mezzanotte UTC e
     * alle otto del mattino vale un terzo del vero.
     */
    private static Quotazione leggiQuotazione(String coppia, JsonNode n) {
        return new Quotazione(
                coppia,
                n.get("c").get(0).asDouble(),
                n.get("v").get(1).asDouble(),
                n.get("p").get(1).asDouble(),
                n.get("l").get(1).asDouble(),
                n.get("h").get(1).asDouble(),
                n.get("t").get(1).asLong());
    }
}
