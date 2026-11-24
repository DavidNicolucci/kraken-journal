package dev.kraken.journal.journal;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tutto lo storico del conto, pronto per il calcolo.
 *
 * @param movimenti acquisti e vendite in ordine cronologico, dall'inizio del
 *        conto e non dall'inizio del periodo: il costo di carico di una vendita
 *        di oggi dipende da un acquisto che puo' essere di mesi fa.
 * @param arrivi per ogni moneta, come e' arrivata sul conto quando non e' stata
 *        comprata.
 * @param ledgerLetto false se Kraken non ha fatto leggere il ledger: in quel
 *        caso mancano le operazioni fatte dall'app e gli arrivi, e chi mostra i
 *        numeri deve dirlo.
 * @param completa false se trade o ledger si sono fermati al tetto di pagine:
 *        mancano le operazioni piu' vecchie.
 */
public record Storia(List<Movimento> movimenti, Map<String, Set<Provenienza>> arrivi, boolean ledgerLetto,
                     boolean completa) {

    /** La stessa storia, segnata come incompleta. */
    public Storia troncata() {
        return new Storia(movimenti, arrivi, ledgerLetto, false);
    }
}
