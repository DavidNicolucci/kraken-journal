package dev.kraken.journal.scansione;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le tariffe di Kraken per le pagine.
 *
 * Lo 0,40 e lo 0,80 erano scritti anche nel calcolatore e nelle tacche del
 * journal: al primo cambio di livello su Kraken si sarebbero aggiornati in un
 * posto solo dei tre. Adesso la fonte e' una, {@link TipoOrdine}.
 */
@RestController
@RequestMapping("/api/tariffe")
public class TariffeController {

    /** In percentuale, come le mostra Kraken: 0.40 e non 0.004. */
    @GetMapping
    public Map<TipoOrdine, BigDecimal> tariffe() {
        Map<TipoOrdine, BigDecimal> tariffe = new EnumMap<>(TipoOrdine.class);
        for (TipoOrdine tipo : TipoOrdine.values()) {
            tariffe.put(tipo, BigDecimal.valueOf(tipo.commissione()).movePointRight(2).stripTrailingZeros());
        }
        return tariffe;
    }
}
