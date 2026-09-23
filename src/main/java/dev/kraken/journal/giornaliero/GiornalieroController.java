package dev.kraken.journal.giornaliero;

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
     * Avvio a mano, per i giorni in cui il computer era spento alle sette.
     * Le coppie gia' registrate oggi non vengono raddoppiate: un'operazione
     * aperta sulla stessa coppia blocca la seconda.
     */
    @PostMapping
    public EsitoLavoro esegui() {
        return lavoro.esegui();
    }
}
