package dev.kraken.journal.scansione;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.kraken.journal.scansione.dto.Candidato;
import dev.kraken.journal.scansione.dto.RispostaScansione;

@RestController
@RequestMapping("/api/scansione")
public class ScansioneController {

    private final ServizioScansione servizio;

    public ScansioneController(ServizioScansione servizio) {
        this.servizio = servizio;
    }

    /**
     * @param soloIdonee se true restituisce le sole coppie che superano tutti i
     *        controlli. Il valore predefinito e' false apposta: vedere anche
     *        quelle bocciate, e su quale controllo, insegna molto di piu' che
     *        ricevere una lista corta senza spiegazione.
     */
    @GetMapping
    public RispostaScansione scansiona(@RequestParam(defaultValue = "false") boolean soloIdonee) {
        RispostaScansione risposta = servizio.scansiona();
        if (!soloIdonee) {
            return risposta;
        }
        return new RispostaScansione(risposta.istante(), risposta.esaminate(), risposta.inWatchlist(),
                risposta.candidati().stream().filter(Candidato::tuttiSuperati).toList());
    }
}
