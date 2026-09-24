package dev.kraken.journal.registro;

import java.time.Duration;
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
 * Vengono usate solo candele chiuse, e solo quelle che finiscono dopo la
 * registrazione. La candela del giorno di registrazione conta, ma a meta':
 * - lo stop si': il pomeriggio di quel giorno e' gia' operazione, e scartare
 *   la candela intera significava non vedere uno stop toccato poche ore dopo
 *   l'entrata. Il minimo puo' anche essere delle ore prima della
 *   registrazione: la candela non lo dice, e si assume il peggio;
 * - l'obiettivo no: il massimo potrebbe essere stato fatto prima
 *   dell'entrata, e contarlo sarebbe la lettura ottimistica.
 * Il time stop conta invece solo i giorni pieni successivi.
 *
 * Se una candela apre gia' sotto lo stop, l'uscita e' l'apertura e non lo
 * stop: nel salto non c'e' nessun prezzo a cui lo stop si sarebbe riempito.
 * Sull'obiettivo il salto non si premia: si esce all'obiettivo.
 */
public final class RisolutoreOperazione {

    /** Il registro ragiona su candele giornaliere, come il time stop. */
    private static final Duration DURATA_CANDELA = Duration.ofDays(1);

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
        Instant registrazione = operazione.dataRegistrazione();
        int giorniPieni = 0;

        for (Candela candela : candele) {
            if (!candela.istante().plus(DURATA_CANDELA).isAfter(registrazione)) {
                continue;
            }
            boolean diRegistrazione = !candela.istante().isAfter(registrazione);

            if (candela.minimo() <= operazione.stop()) {
                // Nella candela di registrazione l'apertura precede l'entrata:
                // non puo' essere un salto oltre lo stop.
                double uscita = diRegistrazione
                        ? operazione.stop()
                        : Math.min(candela.apertura(), operazione.stop());
                return chiusura(StatoOperazione.CHIUSA_ALLO_STOP, operazione,
                        diRegistrazione ? registrazione : candela.istante(), uscita, tipoOrdine);
            }
            if (diRegistrazione) {
                continue;
            }
            if (candela.massimo() >= operazione.obiettivo()) {
                return chiusura(StatoOperazione.CHIUSA_ALL_OBIETTIVO, operazione, candela.istante(),
                        operazione.obiettivo(), tipoOrdine);
            }
            if (++giorniPieni >= giorniTimeStop) {
                return chiusura(StatoOperazione.CHIUSA_PER_TEMPO, operazione, candela.istante(),
                        candela.chiusura(), tipoOrdine);
            }
        }
        return null;
    }

    private static Chiusura chiusura(StatoOperazione stato, OperazioneRegistrata operazione,
                                     Instant quando, double prezzoUscita, TipoOrdine tipoOrdine) {
        return new Chiusura(stato, quando, prezzoUscita,
                MatematicaOperazione.risultatoInR(operazione.entrata(), operazione.stop(),
                        prezzoUscita, tipoOrdine));
    }
}
