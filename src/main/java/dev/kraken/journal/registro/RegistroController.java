package dev.kraken.journal.registro;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.kraken.journal.registro.dto.NuovaOperazione;
import dev.kraken.journal.registro.dto.OperazioneInRegistro;
import dev.kraken.journal.registro.dto.StatisticheRegistro;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/registro")
public class RegistroController {

    private final ServizioRegistro servizio;

    public RegistroController(ServizioRegistro servizio) {
        this.servizio = servizio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OperazioneInRegistro registra(@Valid @RequestBody NuovaOperazione nuova) {
        return OperazioneInRegistro.da(servizio.registra(nuova));
    }

    @GetMapping
    public List<OperazioneInRegistro> tutte() {
        return servizio.tutte().stream().map(OperazioneInRegistro::da).toList();
    }

    @GetMapping("/statistiche")
    public StatisticheRegistro statistiche() {
        return servizio.statistiche();
    }

    /** Da chiamare una volta al giorno, dopo la chiusura delle candele. */
    @PostMapping("/risoluzione")
    public Map<String, Integer> risolvi() {
        return Map.of("chiuse", servizio.risolviAperte());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> incoerente(IllegalArgumentException e) {
        return Map.of("errore", e.getMessage());
    }
}
