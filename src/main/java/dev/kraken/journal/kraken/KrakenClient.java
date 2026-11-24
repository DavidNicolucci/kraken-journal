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
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.kraken.journal.config.KrakenProperties;
import dev.kraken.journal.kraken.dto.KrakenEnvelope;
import dev.kraken.journal.kraken.dto.KrakenLedgerEntry;
import dev.kraken.journal.kraken.dto.KrakenTrade;
import dev.kraken.journal.kraken.dto.LedgersResult;
import dev.kraken.journal.kraken.dto.TradesHistoryResult;

/**
 * Client per gli endpoint privati di Kraken.
 * Sola lettura: non espone alcun metodo che possa creare o annullare ordini.
 */
@Component
public class KrakenClient {

    private static final Logger log = LoggerFactory.getLogger(KrakenClient.class);
    private static final String TRADES_HISTORY = "/0/private/TradesHistory";
    private static final String LEDGERS = "/0/private/Ledgers";
    /** Kraken restituisce al massimo 50 voci per pagina, trade o righe del ledger. */
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 40;
    /**
     * TradesHistory e Ledgers costano due punti del contatore privato, che si
     * scarica di un punto ogni due o tre secondi a seconda del livello del conto. Dopo
     * qualche pagina di fila il contatore e' pieno: la prima attesa deve gia'
     * bastare a recuperare almeno una chiamata.
     */
    private static final Duration ATTESA_LIMITE = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final KrakenSigner signer;
    private final KrakenProperties properties;
    private final Duration attesaLimite;
    private final ReentrantLock chiamatePrivate = new ReentrantLock();

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
    public Storico<KrakenTrade> fetchTrades(Instant from, Instant to) {
        Storico<KrakenTrade> scaricati = scaricaPagine(TRADES_HISTORY, Map.of("trades", "true"), from, to,
                new ParameterizedTypeReference<KrakenEnvelope<TradesHistoryResult>>() {}, TradesHistoryResult::trades);
        List<KrakenTrade> trade = new ArrayList<>(scaricati.voci());
        trade.sort(Comparator.comparing(KrakenTrade::timestamp));
        log.info("Recuperati {} trade da Kraken tra {} e {}", trade.size(), from, to);
        return new Storico<>(List.copyOf(trade), scaricati.completo());
    }

    /**
     * Scarica le righe del ledger nell'intervallo indicato, paginando.
     *
     * Serve alla chiave il permesso "Query ledger entries", sempre di sola
     * lettura. Senza, Kraken risponde con un errore e chi chiama decide come
     * farne a meno.
     */
    public Storico<KrakenLedgerEntry> fetchLedger(Instant from, Instant to) {
        Storico<KrakenLedgerEntry> righe = scaricaPagine(LEDGERS, Map.of(), from, to,
                new ParameterizedTypeReference<KrakenEnvelope<LedgersResult>>() {}, LedgersResult::ledger);
        log.info("Recuperate {} righe del ledger da Kraken tra {} e {}", righe.voci().size(), from, to);
        return righe;
    }

    /**
     * La paginazione comune ai due storici: stessa dimensione di pagina, stesso
     * scorrimento con "ofs", stessi ritentativi.
     *
     * Le voci si raccolgono per id: se fra una pagina e l'altra arriva
     * un'operazione nuova tutto scorre di un posto, e la stessa voce tornerebbe
     * due volte. Per il ledger Kraken preferisce ormai lo scorrimento a cursore;
     * "ofs" resta valido ed e' quello gia' in uso per i trade.
     */
    private <R, T> Storico<T> scaricaPagine(String percorso, Map<String, String> filtri, Instant from, Instant to,
                                         ParameterizedTypeReference<KrakenEnvelope<R>> tipo,
                                         Function<R, Map<String, T>> voci) {
        Map<String, T> raccolte = new LinkedHashMap<>();
        boolean completo = false;

        for (int numero = 0, offset = 0; numero < MAX_PAGES; numero++) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("type", "all");
            params.putAll(filtri);
            params.put("start", String.valueOf(from.getEpochSecond()));
            params.put("end", String.valueOf(to.getEpochSecond()));
            params.put("ofs", String.valueOf(offset));

            R risultato = Ritentativi.conRitentativi(percorso, attesaLimite,
                    () -> callPrivate(percorso, params, tipo));
            Map<String, T> pagina = risultato == null ? null : voci.apply(risultato);

            if (pagina == null || pagina.isEmpty()) {
                completo = true;
                break;
            }
            raccolte.putAll(pagina);

            if (pagina.size() < PAGE_SIZE) {
                completo = true;
                break;
            }
            offset += pagina.size();
        }

        if (!completo) {
            log.warn("{}: raggiunto il tetto di {} pagine, il journal usa solo le {} voci piu' recenti",
                    percorso, MAX_PAGES, raccolte.size());
        }
        return new Storico<>(List.copyOf(raccolte.values()), completo);
    }

    /**
     * Una chiamata privata alla volta, dal nonce alla risposta. Il nonce e'
     * crescente, ma due richieste partite insieme (30 g e subito 1 anno nella
     * pagina) possono arrivare a Kraken in ordine inverso: la seconda viene
     * rifiutata con "EAPI:Invalid nonce", che non si ritenta. Il lucchetto e'
     * un ReentrantLock e non synchronized, che bloccherebbe il thread virtuale
     * durante la chiamata HTTP.
     */
    private <T> T callPrivate(String path, Map<String, String> params,
                              ParameterizedTypeReference<KrakenEnvelope<T>> type) {
        chiamatePrivate.lock();
        try {
            return chiamaUnaVolta(path, params, type);
        } finally {
            chiamatePrivate.unlock();
        }
    }

    private <T> T chiamaUnaVolta(String path, Map<String, String> params,
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
