package dev.kraken.journal.verifica;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import dev.kraken.journal.mercato.Intervallo;
import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.scansione.CalcolatoreIndicatori;
import dev.kraken.journal.scansione.Controllo;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.scansione.ValutatoreSetup;
import dev.kraken.journal.verifica.VerificaStorica.EsitoCoppia;
import dev.kraken.journal.verifica.dto.OperazioneStorica;
import dev.kraken.journal.verifica.dto.Riepilogo;
import dev.kraken.journal.verifica.dto.RispostaVerificaStorica;

/**
 * Fa girare il metodo di oggi sulle candele giornaliere della watchlist: le
 * stesse della scansione, dalla stessa cache. Kraken ne da' circa due anni,
 * quindi il passato contiene fasi di salita anche quando oggi il mercato
 * scende e la scansione non trova niente.
 */
@Service
public class ServizioVerificaStorica {

    private final MercatoClient mercatoClient;
    private final ProprietaScansione proprieta;
    private final ValutatoreSetup valutatore;

    public ServizioVerificaStorica(MercatoClient mercatoClient, ProprietaScansione proprieta, ValutatoreSetup valutatore) {
        this.mercatoClient = mercatoClient;
        this.proprieta = proprieta;
        this.valutatore = valutatore;
    }

    public RispostaVerificaStorica verifica() {
        int casiPerGiudizio = proprieta.registrazioniPerGiudizio();
        List<OperazioneStorica> operazioni = new ArrayList<>();
        List<Riepilogo> perCoppia = new ArrayList<>();
        Map<Controllo, Integer> bocciature = new EnumMap<>(Controllo.class);
        List<String> escluse = new ArrayList<>();
        List<Instant> inizi = new ArrayList<>();
        List<Instant> fini = new ArrayList<>();
        int giorni = 0;

        for (var voce : mercatoClient.candeleChiusePerCoppia(proprieta.watchlist(), Intervallo.GIORNALIERO).entrySet()) {
            String coppia = voce.getKey();
            var candele = voce.getValue();
            if (candele == null) {
                escluse.add(coppia + ": candele non disponibili");
                continue;
            }
            if (candele.size() <= CalcolatoreIndicatori.CANDELE_MINIME) {
                escluse.add(coppia + ": " + candele.size() + " candele, ne servono piu' di " + CalcolatoreIndicatori.CANDELE_MINIME);
                continue;
            }
            EsitoCoppia esito = VerificaStorica.simula(coppia, candele, proprieta, valutatore);
            operazioni.addAll(esito.operazioni());
            perCoppia.add(VerificaStorica.riepilogo(coppia, esito.operazioni(), casiPerGiudizio));
            esito.bocciature().forEach((controllo, volte) -> bocciature.merge(controllo, volte, Integer::sum));
            giorni += esito.giorni();
            inizi.add(esito.dal());
            fini.add(candele.get(candele.size() - 1).istante());
        }

        operazioni.sort(Comparator.comparing(OperazioneStorica::segnale).thenComparing(OperazioneStorica::coppia));
        return new RispostaVerificaStorica(Instant.now(),
                inizi.stream().filter(Objects::nonNull).min(Instant::compareTo).orElse(null),
                fini.stream().max(Instant::compareTo).orElse(null),
                giorni, bocciature, proprieta.rischioAmmesso(), casiPerGiudizio,
                VerificaStorica.riepilogo(null, operazioni, casiPerGiudizio),
                perCoppia, operazioni, List.copyOf(escluse));
    }
}
