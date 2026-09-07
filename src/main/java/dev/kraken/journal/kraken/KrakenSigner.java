package dev.kraken.journal.kraken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import dev.kraken.journal.config.KrakenProperties;

/**
 * Firma delle richieste private Kraken.
 *
 * Algoritmo (da documentazione Kraken):
 *   API-Sign = Base64( HMAC-SHA512( uriPath || SHA256(nonce || postData), Base64Decode(secret) ) )
 *
 * Il nonce deve essere strettamente crescente per chiave API: se due richieste
 * partono nello stesso millisecondo, Kraken rifiuta la seconda con
 * "EAPI:Invalid nonce". L'AtomicLong garantisce la monotonicita' all'interno
 * del processo. Con piu' istanze usa una chiave API per istanza.
 */
@Component
public class KrakenSigner {

    private static final String HMAC_SHA512 = "HmacSHA512";

    private final byte[] decodedSecret;
    private final AtomicLong nonceCounter;

    public KrakenSigner(KrakenProperties properties) {
        try {
            this.decodedSecret = Base64.getDecoder().decode(properties.apiSecret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("kraken.api-secret non e' una stringa Base64 valida", e);
        }
        this.nonceCounter = new AtomicLong(System.currentTimeMillis());
    }

    /** Nonce monotono crescente, in millisecondi. */
    public long nextNonce() {
        return nonceCounter.updateAndGet(previous -> Math.max(previous + 1, System.currentTimeMillis()));
    }

    /**
     * @param uriPath  percorso completo, es. "/0/private/TradesHistory"
     * @param nonce    lo stesso nonce presente in postData
     * @param postData body gia' url-encoded, es. "nonce=123&type=all"
     */
    public String sign(String uriPath, long nonce, String postData) {
        byte[] sha256 = sha256(nonce + postData);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA512);
            mac.init(new SecretKeySpec(decodedSecret, HMAC_SHA512));
            mac.update(uriPath.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(mac.doFinal(sha256));
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile firmare la richiesta Kraken", e);
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }
}
