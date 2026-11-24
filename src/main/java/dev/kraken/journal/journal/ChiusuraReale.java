package dev.kraken.journal.journal;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

import dev.kraken.journal.journal.dto.JournalStats;
import dev.kraken.journal.journal.dto.OperazioneChiusa;
import dev.kraken.journal.journal.dto.Ordine;
import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.registro.OperazioneRegistrata;
import dev.kraken.journal.registro.RisolutoreOperazione;
import dev.kraken.journal.registro.StatoOperazione;
import dev.kraken.journal.scansione.MatematicaOperazione;
import dev.kraken.journal.scansione.TipoOrdine;

/**
 * Come e' finita un'operazione reale, letto dagli ordini su Kraken invece che
 * simulato sulle candele: il prezzo vero di uscita, lo slittamento allo stop,
 * un'uscita anticipata. Funzione pura, come {@link RisolutoreOperazione}.
 *
 * L'operazione e' il giro che contiene il primo acquisto sulla moneta nei
 * giorni dopo la registrazione (gli stessi di E8). Il risultato in R e' quello
 * del giro diviso il rischio pianificato: quantita' registrata per perdita per
 * unita'. Se si e' comprato il doppio, l'R lo dice.
 *
 * Resta aperta, e si chiude a mano, quando Kraken non basta: giro ancora in
 * corso, risultato non calcolabile, o giro in una valuta diversa da quella
 * registrata.
 */
final class ChiusuraReale {

    /** L'esito: una chiusura, oppure un'operazione mai aperta. */
    record Esito(RisolutoreOperazione.Chiusura chiusura, boolean nonEseguita) {
        static final Esito NON_ESEGUITA = new Esito(null, true);
    }

    private ChiusuraReale() {
    }

    /**
     * @param stats calcolate su tutto lo storico, con il ledger letto: senza,
     *        un acquisto dall'app non si vedrebbe e l'operazione risulterebbe
     *        mai eseguita.
     * @return vuoto se l'operazione e' ancora aperta o va chiusa a mano.
     */
    static Optional<Esito> di(OperazioneRegistrata operazione, TipoOrdine tipoOrdine, JournalStats stats,
                              Duration finestra, Instant adesso, PairResolver pairResolver) {
        PairResolver.Pair coppia = pairResolver.resolve(operazione.coppia());
        Instant registrazione = operazione.dataRegistrazione();
        Instant limite = registrazione.plus(finestra);

        Optional<Ordine> acquisto = stats.ordini().stream()
                .filter(o -> o.acquisto() && !o.conversione() && o.moneta().equals(coppia.base())
                        && !o.istante().isBefore(registrazione) && !o.istante().isAfter(limite))
                .min(Comparator.comparing(Ordine::istante));
        if (acquisto.isEmpty()) {
            return adesso.isAfter(limite) ? Optional.of(Esito.NON_ESEGUITA) : Optional.empty();
        }
        Instant comprato = acquisto.get().istante();
        Optional<OperazioneChiusa> giro = stats.operazioniChiuse().stream()
                .filter(g -> g.coppia().startsWith(coppia.base() + "/")
                        && !g.apertura().isAfter(comprato) && !g.chiusura().isBefore(comprato))
                .findFirst();
        double rischio = operazione.quantita()
                * MatematicaOperazione.rischioPerUnita(operazione.entrata(), operazione.stop(), tipoOrdine);
        if (giro.isEmpty() || giro.get().risultato() == null || giro.get().prezzoMedioUscita() == null
                || !giro.get().valuta().equals(coppia.quote()) || rischio <= 0) {
            return Optional.empty();
        }

        double uscita = giro.get().prezzoMedioUscita().doubleValue();
        BigDecimal risultato = giro.get().risultato();
        return Optional.of(new Esito(new RisolutoreOperazione.Chiusura(
                StatoOperazione.dallUscita(uscita, operazione.stop(), operazione.obiettivo()),
                giro.get().chiusura(), uscita, risultato.doubleValue() / rischio), false));
    }
}
