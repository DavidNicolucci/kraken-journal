package dev.kraken.journal.journal.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vista dei dati destinata al modello.
 *
 * Esiste per una ragione precisa: passare al modello lo stesso oggetto che
 * serve al frontend lo portava a citare i nomi dei campi Java nel testo e a
 * confondersi contando le conversioni valutarie insieme ai trade. Qui i nomi
 * sono in italiano e le conversioni sono un blocco separato, cosi' il modello
 * non deve ignorare nulla: semplicemente non lo riceve mescolato.
 */
public record AiView(
        String periodo,
        int giorniConOperazioni,
        int numeroTrade,
        BigDecimal tradeAlGiornoAttivo,
        BigDecimal volumeScambiato,
        BigDecimal commissioniDiTrading,
        BigDecimal commissioniInPercentualeSulVolume,
        BigDecimal risultatoRealizzato,
        String notaSulRisultato,
        Cambi conversioniValutarie,
        String notaSulleValute,
        List<Coppia> coppie
) {
    public record Coppia(
            String nome,
            int operazioni,
            int acquisti,
            int vendite,
            BigDecimal volume,
            String valuta,
            BigDecimal commissioni,
            BigDecimal commissioniInPercentuale,
            BigDecimal risultatoRealizzato,
            String statoDelRisultato,
            BigDecimal posizioneAncoraAperta
    ) {}

    public record Cambi(int numero, BigDecimal commissioni, BigDecimal commissioniInPercentuale) {}
}
