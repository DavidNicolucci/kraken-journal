package dev.kraken.journal.scansione.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import dev.kraken.journal.mercato.dto.Quotazione;
import dev.kraken.journal.scansione.Esito;

/**
 * Una coppia esaminata, con tutto quello che serve per decidere da soli.
 *
 * Nessun campo dice se entrare. E' una scelta, non una dimenticanza: un
 * vantaggio statistico non e' stato dimostrato, quindi un programma che
 * scrivesse "compra" starebbe affermando qualcosa che nessuno ha verificato.
 * Qui c'e' lo stato; il giudizio resta a chi legge.
 */
public record Candidato(
        String coppia,
        Quotazione quotazione,
        Indicatori indicatori,
        Piano piano,
        List<EsitoControllo> controlli,
        long superati,
        int totale) {

    public static Candidato di(String coppia, Quotazione quotazione, Indicatori indicatori,
                               Piano piano, List<EsitoControllo> controlli) {
        return new Candidato(coppia, quotazione, indicatori, piano, controlli,
                controlli.stream().filter(c -> c.esito() == Esito.SUPERATO).count(),
                controlli.size());
    }

    @JsonProperty("tuttiSuperati")
    public boolean tuttiSuperati() {
        return superati == totale;
    }
}
