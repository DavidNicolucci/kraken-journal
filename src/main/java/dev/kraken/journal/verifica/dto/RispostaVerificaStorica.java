package dev.kraken.journal.verifica.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dev.kraken.journal.scansione.Controllo;

/**
 * @param dal la prima candela su cui il metodo poteva dare un segnale
 * @param al l'ultima candela chiusa
 * @param giorniEsaminati coppie per giorni su cui e' girata la checklist
 * @param bocciature quante volte ogni controllo e' fallito su quei giorni:
 *        dice quale regola tiene fuori il metodo, e quanto spesso
 * @param rischioPerOperazione quanto vale una R in euro, col capitale di oggi
 * @param casiPerGiudizio sotto quante operazioni chiuse il giudizio e' POCHI_CASI
 * @param escluse coppie senza abbastanza candele o non scaricate, col motivo
 */
public record RispostaVerificaStorica(
        Instant istante,
        Instant dal,
        Instant al,
        int giorniEsaminati,
        Map<Controllo, Integer> bocciature,
        double rischioPerOperazione,
        int casiPerGiudizio,
        Riepilogo totale,
        List<Riepilogo> perCoppia,
        List<OperazioneStorica> operazioni,
        List<String> escluse) {
}
