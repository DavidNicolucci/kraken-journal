package dev.kraken.journal.registro;

import java.time.Instant;
import java.util.List;

import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.scansione.MatematicaOperazione;
import dev.kraken.journal.scansione.TipoOrdine;

/**
 * Decide come e' finita un'operazione, leggendo le candele successive alla
 * registrazione. Funzione pura: nessun database, nessuna rete, quindi
 * verificabile da sola.
 *
 * La scelta che conta e' l'ordine dei controlli. Quando in una stessa giornata
 * il prezzo tocca sia lo stop sia l'obiettivo, la candela giornaliera non dice
 * quale sia arrivato prima. Qui si assume SEMPRE lo stop. E' la lettura
 * pessimistica, ed e' voluta: assumere l'obiettivo trasformerebbe il registro
 * in una macchina che si autoassolve, che e' il modo classico in cui una
 * misurazione smette di servire a qualcosa.
 *
 * Vengono usate solo candele chiuse e solo successive alla registrazione: una
 * regola non si giudica sui dati che non aveva al momento della decisione.
 */
public final class RisolutoreOperazione {

    private RisolutoreOperazione() {
    }

    /** Esito di un'operazione chiusa. */
    public record Chiusura(StatoOperazione stato, Instant quando, double prezzoUscita, double risultatoInR) {
    }

    /**
     * @return la chiusura, oppure null se l'operazione e' ancora aperta.
     */
    public static Chiusura risolvi(OperazioneRegistrata operazione, List<Candela> candele,
                                   int giorniTimeStop, TipoOrdine tipoOrdine) {
        List<Candela> successive = candele.stream()
                .filter(c -> c.istante().isAfter(operazione.dataRegistrazione()))
                .toList();

        for (int giorno = 0; giorno < successive.size(); giorno++) {
            Candela candela = successive.get(giorno);

            if (candela.minimo() <= operazione.stop()) {
                return chiusura(StatoOperazione.CHIUSA_ALLO_STOP, operazione, candela,
                        operazione.stop(), tipoOrdine);
            }
            if (candela.massimo() >= operazione.obiettivo()) {
                return chiusura(StatoOperazione.CHIUSA_ALL_OBIETTIVO, operazione, candela,
                        operazione.obiettivo(), tipoOrdine);
            }
            if (giorno + 1 >= giorniTimeStop) {
                return chiusura(StatoOperazione.CHIUSA_PER_TEMPO, operazione, candela,
                        candela.chiusura(), tipoOrdine);
            }
        }
        return null;
    }

    private static Chiusura chiusura(StatoOperazione stato, OperazioneRegistrata operazione,
                                     Candela candela, double prezzoUscita, TipoOrdine tipoOrdine) {
        return new Chiusura(stato, candela.istante(), prezzoUscita,
                MatematicaOperazione.risultatoInR(operazione.entrata(), operazione.stop(),
                        prezzoUscita, tipoOrdine));
    }
}
