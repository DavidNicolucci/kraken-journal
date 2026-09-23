package dev.kraken.journal.registro.dto;

import java.time.Instant;

import dev.kraken.journal.registro.OperazioneRegistrata;
import dev.kraken.journal.registro.StatoOperazione;
import dev.kraken.journal.registro.Trigger;

/**
 * Un'operazione cosi' come esce dall'API.
 *
 * Esiste per un motivo concreto, non per simmetria: OperazioneRegistrata e' una
 * classe con accessor in stile record (coppia(), entrata()), che Jackson NON
 * riconosce come getter. Serializzata direttamente uscirebbe vuota, o farebbe
 * fallire la chiamata. Il resto del progetto espone record, quindi la strada
 * coerente e' questa e non annotare l'entita'.
 *
 * Effetto collaterale utile: l'entita' JPA smette di essere il contratto verso
 * l'esterno, e si puo' cambiare senza rompere la pagina.
 */
public record OperazioneInRegistro(
        Long id,
        String coppia,
        Instant dataRegistrazione,
        Trigger trigger,
        double entrata,
        double stop,
        double obiettivo,
        double quantita,
        double rapporto,
        String motivo,
        StatoOperazione stato,
        boolean reale,
        Instant dataChiusura,
        Double prezzoUscita,
        Double risultatoInR) {

    public static OperazioneInRegistro da(OperazioneRegistrata o) {
        return new OperazioneInRegistro(o.id(), o.coppia(), o.dataRegistrazione(), o.trigger(),
                o.entrata(), o.stop(), o.obiettivo(), o.quantita(), o.rapporto(), o.motivo(),
                o.stato(), o.reale(), o.dataChiusura(), o.prezzoUscita(), o.risultatoInR());
    }
}
