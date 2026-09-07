package dev.kraken.journal.kraken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.config.KrakenProperties;

class KrakenSignerTest {

    /**
     * Vettore di test della documentazione Kraken.
     * Se questo test passa, la firma e' corretta: un HMAC-SHA512 non coincide
     * per caso. Se un giorno fallisce, il problema e' nel codice di firma,
     * non nelle credenziali.
     */
    @Test
    void produceLaFirmaDiRiferimento() {
        var props = new KrakenProperties(
                "https://api.kraken.com",
                "chiave-non-usata-nella-firma",
                "kQH5HW/8p1uGOVjbgWA7FunAmGO8lsSUXNsu3eow76sz84Q18fWxnyRzBHCd3pd5nE9qa99HAZtuZuj6F1huXg=="
        );
        var signer = new KrakenSigner(props);

        String signature = signer.sign(
                "/0/private/AddOrder",
                1616492376594L,
                "nonce=1616492376594&ordertype=limit&pair=XBTUSD&price=37500&type=buy&volume=1.25");

        assertEquals(
                "4/dpxb3iT4tp/ZCVEwSnEsLxx0bqyhLpdfOpc6fn7OR8+UClSV5n9E6aSS8MPtnRfp32bAb0nmbRn6H8ndwLUQ==",
                signature);
    }

    @Test
    void ilNonceEsempreCrescente() {
        var props = new KrakenProperties(null, "k",
                "kQH5HW/8p1uGOVjbgWA7FunAmGO8lsSUXNsu3eow76sz84Q18fWxnyRzBHCd3pd5nE9qa99HAZtuZuj6F1huXg==");
        var signer = new KrakenSigner(props);

        long previous = signer.nextNonce();
        for (int i = 0; i < 10_000; i++) {
            long current = signer.nextNonce();
            assertTrue(current > previous, "nonce non crescente: %d dopo %d".formatted(current, previous));
            previous = current;
        }
    }
}
