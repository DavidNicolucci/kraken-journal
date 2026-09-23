package dev.kraken.journal.calcolatore;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.kraken.journal.calcolatore.dto.EsitoValutazione;
import dev.kraken.journal.calcolatore.dto.RichiestaValutazione;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/valutazione")
public class CalcolatoreController {

    private final ServizioCalcolatore servizio;

    public CalcolatoreController(ServizioCalcolatore servizio) {
        this.servizio = servizio;
    }

    @PostMapping
    public EsitoValutazione valuta(@Valid @RequestBody RichiestaValutazione richiesta) {
        return servizio.valuta(richiesta);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> incoerente(IllegalArgumentException e) {
        return Map.of("errore", e.getMessage());
    }
}
