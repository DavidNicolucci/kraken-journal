package dev.kraken.journal.simulatore.dto;

import java.time.Instant;
import java.util.List;

/**
 * @param escluse le coppie che non e' stato possibile analizzare, col motivo:
 *        una coppia che sparisce in silenzio fa sembrare completa un'analisi che non lo e'
 */
public record RispostaSimulatore(
        Instant istante,
        int orizzonte,
        Verifica verifica,
        List<Previsione> previsioni,
        List<String> escluse) {
}
