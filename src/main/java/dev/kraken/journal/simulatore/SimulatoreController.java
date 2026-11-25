package dev.kraken.journal.simulatore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.kraken.journal.simulatore.dto.RispostaSimulatore;

@RestController
@RequestMapping("/api/simulatore")
public class SimulatoreController {

    private final ServizioSimulatore servizio;

    public SimulatoreController(ServizioSimulatore servizio) {
        this.servizio = servizio;
    }

    @GetMapping
    public RispostaSimulatore simula() {
        return servizio.simula();
    }
}
