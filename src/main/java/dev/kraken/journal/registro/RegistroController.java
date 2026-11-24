package dev.kraken.journal.registro;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.kraken.journal.registro.dto.NuovaOperazione;
import dev.kraken.journal.registro.dto.OperazioneInRegistro;
import dev.kraken.journal.registro.dto.StatisticheRegistro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

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

    /**
     * La chiusura a mano di un'operazione reale, quando gli ordini su Kraken
     * non bastano a chiuderla da soli: venduta in un'altra valuta, ledger non
     * leggibile.
     */
    @PostMapping("/{id}/chiusura")
    public OperazioneInRegistro chiudi(@PathVariable long id, @Valid @RequestBody ChiusuraAMano chiusura) {
        return OperazioneInRegistro.da(servizio.chiudiAMano(id, chiusura.prezzoUscita()));
    }

    /** @param prezzoUscita il prezzo medio a cui si e' venduto. */
    public record ChiusuraAMano(@Positive double prezzoUscita) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> incoerente(IllegalArgumentException e) {
        return Map.of("errore", e.getMessage());
    }
}
