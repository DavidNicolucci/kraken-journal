package dev.kraken.journal.kraken;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.kraken.journal.config.KrakenProperties;
import dev.kraken.journal.kraken.dto.KrakenEnvelope;
import dev.kraken.journal.kraken.dto.KrakenTrade;
import dev.kraken.journal.kraken.dto.TradesHistoryResult;

/**
 * Client per gli endpoint privati di Kraken.
 * Sola lettura: non espone alcun metodo che possa creare o annullare ordini.
 */
@Component
public class KrakenClient {

    private static final Logger log = LoggerFactory.getLogger(KrakenClient.class);
    private static final String TRADES_HISTORY = "/0/private/TradesHistory";
    /** Kraken restituisce al massimo 50 trade per pagina. */
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 40;
    /**
     * TradesHistory costa due punti del contatore privato, che si scarica di
     * un punto ogni due o tre secondi a seconda del livello del conto. Dopo
     * qualche pagina di fila il contatore e' pieno: la prima attesa deve gia'
     * bastare a recuperare almeno una chiamata.
     */
    private static final Duration ATTESA_LIMITE = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final KrakenSigner signer;
    private final KrakenProperties properties;
    private final Duration attesaLimite;

    @Autowired
    public KrakenClient(RestClient krakenRestClient, KrakenSigner signer, KrakenProperties properties) {
        this(krakenRestClient, signer, properties, ATTESA_LIMITE);
    }

    /** Per i test: un'attesa vera fra i tentativi li renderebbe lenti. */
    KrakenClient(RestClient krakenRestClient, KrakenSigner signer, KrakenProperties properties,
                 Duration attesaLimite) {
        this.restClient = krakenRestClient;
        this.signer = signer;
        this.properties = properties;
        this.attesaLimite = attesaLimite;
    }

    /**
     * Scarica tutti i trade eseguiti nell'intervallo indicato, paginando.
     * I risultati arrivano dal piu' recente al piu' vecchio: li riordino
     * cronologicamente perche' il journal ragiona in avanti nel tempo.
     */
    public List<KrakenTrade> fetchTrades(Instant from, Instant to) {
        List<KrakenTrade> collected = new ArrayList<>();
        int offset = 0;
        boolean completo = false;

        for (int page = 0; page < MAX_PAGES; page++) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("type", "all");
            params.put("trades", "true");
            params.put("start", String.valueOf(from.getEpochSecond()));
            params.put("end", String.valueOf(to.getEpochSecond()));
            params.put("ofs", String.valueOf(offset));

            TradesHistoryResult result = Ritentativi.conRitentativi(TRADES_HISTORY, attesaLimite,
                    () -> callPrivate(TRADES_HISTORY, params,
                            new ParameterizedTypeReference<KrakenEnvelope<TradesHistoryResult>>() {}));

            if (result == null || result.trades() == null || result.trades().isEmpty()) {
                completo = true;
                break;
            }
            collected.addAll(result.trades().values());

            if (collected.size() >= result.count() || result.trades().size() < PAGE_SIZE) {
                completo = true;
                break;
            }
            offset += result.trades().size();
        }

        if (!completo) {
            log.warn("Raggiunto il tetto di {} pagine: il journal usa solo i {} trade piu' recenti del periodo",
                    MAX_PAGES, collected.size());
        }

        collected.sort(Comparator.comparing(KrakenTrade::timestamp));
        log.info("Recuperati {} trade da Kraken tra {} e {}", collected.size(), from, to);
        return collected;
    }

    private <T> T callPrivate(String path, Map<String, String> params,
                              ParameterizedTypeReference<KrakenEnvelope<T>> type) {
        long nonce = signer.nextNonce();

        Map<String, String> withNonce = new LinkedHashMap<>();
        withNonce.put("nonce", String.valueOf(nonce));
        withNonce.putAll(params);

        String postData = formUrlEncode(withNonce);
        String signature = signer.sign(path, nonce, postData);

        KrakenEnvelope<T> envelope = restClient.post()
                .uri(path)
                .header("API-Key", properties.apiKey())
                .header("API-Sign", signature)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(postData)
                .retrieve()
                .body(type);

        if (envelope == null) {
            throw new KrakenApiException(path, List.of("risposta vuota"));
        }
        if (envelope.hasError()) {
            throw new KrakenApiException(path, envelope.error());
        }
        return envelope.result();
    }

    /**
     * Encoding manuale: la stringa firmata e quella inviata devono coincidere
     * byte per byte, quindi non posso delegare la serializzazione al RestClient.
     */
    private static String formUrlEncode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }
}
