package dev.kraken.journal.verifica;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.kraken.journal.verifica.dto.RispostaVerificaStorica;

@RestController
@RequestMapping("/api/verifica-storica")
public class VerificaStoricaController {

    private final ServizioVerificaStorica servizio;

    public VerificaStoricaController(ServizioVerificaStorica servizio) {
        this.servizio = servizio;
    }

    @GetMapping
    public RispostaVerificaStorica verifica() {
        return servizio.verifica();
    }
}
