package dev.kraken.journal.giornaliero;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.kraken.journal.giornaliero.LavoroGiornaliero.EsitoLavoro;

@RestController
@RequestMapping("/api/giornaliero")
public class GiornalieroController {

    private final LavoroGiornaliero lavoro;

    public GiornalieroController(LavoroGiornaliero lavoro) {
        this.lavoro = lavoro;
    }

    /**
     * Avvio a mano, per i giorni in cui il computer era spento all'ora del lavoro.
     * Le coppie gia' registrate oggi non vengono raddoppiate: un'operazione
     * aperta sulla stessa coppia blocca la seconda.
     */
    @PostMapping
    public EsitoLavoro esegui() {
        return lavoro.esegui();
    }

    /**
     * Solo la chiusura delle operazioni risolte, senza scansione. Era in
     * /api/registro: e' passata qui perche' ora legge anche gli ordini su
     * Kraken, che il registro da solo non conosce.
     */
    @PostMapping("/risoluzione")
    public Map<String, Integer> risolvi() {
        return Map.of("chiuse", lavoro.risolvi());
    }
}
