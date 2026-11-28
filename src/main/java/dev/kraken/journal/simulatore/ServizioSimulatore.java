package dev.kraken.journal.simulatore;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.kraken.journal.mercato.Intervallo;
import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.simulatore.CalcolatoreSimulazione.Osservazione;
import dev.kraken.journal.simulatore.dto.Previsione;
import dev.kraken.journal.simulatore.dto.RispostaSimulatore;

/**
 * Scarica le candele giornaliere della watchlist e ci fa girare il
 * simulatore. Sono le stesse candele della scansione e passano dalla stessa
 * cache: se la scansione di oggi e' gia' girata, qui non parte nessuna chiamata.
 *
 * Kraken restituisce al massimo 720 candele: circa un anno serve alla
 * verifica, il resto a dare al modello una storia da cui partire.
 */
@Service
public class ServizioSimulatore {

    private final MercatoClient mercatoClient;
    private final ProprietaScansione scansione;
    private final ProprietaSimulatore proprieta;

    public ServizioSimulatore(MercatoClient mercatoClient, ProprietaScansione scansione, ProprietaSimulatore proprieta) {
        this.mercatoClient = mercatoClient;
        this.scansione = scansione;
        this.proprieta = proprieta;
    }

    public RispostaSimulatore simula() {
        int orizzonte = proprieta.orizzonte();
        List<Osservazione> tutte = new ArrayList<>();
        Map<String, Osservazione> oggi = new LinkedHashMap<>();
        List<String> escluse = new ArrayList<>();

        mercatoClient.candeleChiusePerCoppia(scansione.watchlist(), Intervallo.GIORNALIERO).forEach((coppia, candele) -> {
            if (candele == null) {
                escluse.add(coppia + ": candele non disponibili");
                return;
            }
            List<Osservazione> osservazioni = CalcolatoreSimulazione.osservazioni(coppia, candele, orizzonte);
            if (osservazioni.stream().filter(Osservazione::conEsito).count() < proprieta.casiMinimi()) {
                escluse.add(coppia + ": " + candele.size() + " candele, ne servono almeno "
                        + (CalcolatoreSimulazione.RISCALDAMENTO + orizzonte + proprieta.casiMinimi()));
                return;
            }
            tutte.addAll(osservazioni);
            oggi.put(coppia, osservazioni.get(osservazioni.size() - 1));
        });

        Instant dal = oggi.values().stream().map(Osservazione::istante).max(Instant::compareTo)
                .orElse(Instant.now()).minus(proprieta.giorniVerifica(), ChronoUnit.DAYS);
        List<Previsione> previsioni = oggi.values().stream()
                .map(o -> CalcolatoreSimulazione.previsione(o, tutte, dal, orizzonte, proprieta.casiMinimi()))
                .toList();
        return new RispostaSimulatore(Instant.now(), orizzonte,
                CalcolatoreSimulazione.verifica(tutte, dal, orizzonte, proprieta.casiMinimi()),
                previsioni, List.copyOf(escluse));
    }
}
