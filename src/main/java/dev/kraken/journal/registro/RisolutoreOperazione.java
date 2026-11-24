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
 * registrazione. Il giorno di registrazione si legge sulle candele orarie,
 * quando ci sono tutte fino a fine giornata (Kraken ne tiene trenta giorni):
 * cosi' un minimo toccato alle cinque non chiude un'operazione registrata
 * alle sette, e un obiettivo toccato nel pomeriggio conta. Dentro l'ora di
 * registrazione vale la stessa prudenza che vale altrimenti per il giorno:
 * - lo stop si': non si sa se il minimo e' venuto prima o dopo l'entrata, e
 *   si assume il peggio;
 * - l'obiettivo no: il massimo potrebbe essere stato fatto prima
 *   dell'entrata, e contarlo sarebbe la lettura ottimistica.
 * Senza le orarie, la stessa regola si applica alla candela del giorno.
 * Il time stop conta solo i giorni pieni successivi, ed esce alla chiusura
 * dell'ultimo: la data di chiusura e' la fine di quella candela.
 *
 * Se una candela apre gia' sotto lo stop, l'uscita e' l'apertura e non lo
 * stop: nel salto non c'e' nessun prezzo a cui lo stop si sarebbe riempito.
 * Sull'obiettivo il salto non si premia: si esce all'obiettivo.
 *
 * Stop e time stop escono a mercato; l'obiettivo, che e' un ordine in attesa,
 * alla tariffa scelta per l'operazione.
 */
public final class RisolutoreOperazione {

    /** Il registro ragiona su candele giornaliere, come il time stop. */
    private static final Duration DURATA_CANDELA = Duration.ofDays(1);
    private static final Duration DURATA_ORA = Duration.ofHours(1);

    private RisolutoreOperazione() {
    }

    /** Esito di un'operazione chiusa. */
    public record Chiusura(StatoOperazione stato, Instant quando, double prezzoUscita, double risultatoInR) {
    }

    /**
     * @param giornaliere candele chiuse, dalla piu' vecchia.
     * @param orarie candele orarie chiuse, dalla piu' vecchia: servono solo per
     *        il giorno di registrazione, e possono mancare.
     * @return la chiusura, oppure null se l'operazione e' ancora aperta.
     */
    public static Chiusura risolvi(OperazioneRegistrata operazione, List<Candela> giornaliere, List<Candela> orarie,
                                   int giorniTimeStop, TipoOrdine tipoOrdine) {
        Instant registrazione = operazione.dataRegistrazione();
        int giorniPieni = 0;

        for (Candela candela : giornaliere) {
            Instant fine = candela.istante().plus(DURATA_CANDELA);
            if (!fine.isAfter(registrazione)) {
                continue;
            }
            if (!candela.istante().isAfter(registrazione)) {
                List<Candela> ore = orarie.stream()
                        .filter(o -> o.istante().plus(DURATA_ORA).isAfter(registrazione) && o.istante().isBefore(fine))
                        .toList();
                boolean complete = !ore.isEmpty() && !ore.get(ore.size() - 1).istante().plus(DURATA_ORA).isBefore(fine);
                Chiusura chiusura = complete
                        ? candeleDopo(operazione, ore, registrazione, tipoOrdine)
                        : candeleDopo(operazione, List.of(candela), registrazione, tipoOrdine);
                if (chiusura != null) {
                    return chiusura;
                }
                continue;
            }
            Chiusura chiusura = candeleDopo(operazione, List.of(candela), registrazione, tipoOrdine);
            if (chiusura != null) {
                return chiusura;
            }
            if (++giorniPieni >= giorniTimeStop) {
                return chiusura(StatoOperazione.CHIUSA_PER_TEMPO, operazione, fine, candela.chiusura(), tipoOrdine);
            }
        }
        return null;
    }

    /**
     * Stop e obiettivo su una serie di candele. Quella che contiene la
     * registrazione conta solo per lo stop, e all'apertura non c'e' salto:
     * l'apertura precede l'entrata.
     */
    private static Chiusura candeleDopo(OperazioneRegistrata operazione, List<Candela> candele,
                                        Instant registrazione, TipoOrdine tipoOrdine) {
        for (Candela candela : candele) {
            boolean diRegistrazione = !candela.istante().isAfter(registrazione);
            if (candela.minimo() <= operazione.stop()) {
                double uscita = diRegistrazione
                        ? operazione.stop()
                        : Math.min(candela.apertura(), operazione.stop());
                return chiusura(StatoOperazione.CHIUSA_ALLO_STOP, operazione,
                        diRegistrazione ? registrazione : candela.istante(), uscita, tipoOrdine);
            }
            if (!diRegistrazione && candela.massimo() >= operazione.obiettivo()) {
                return chiusura(StatoOperazione.CHIUSA_ALL_OBIETTIVO, operazione, candela.istante(),
                        operazione.obiettivo(), tipoOrdine);
            }
        }
        return null;
    }

    private static Chiusura chiusura(StatoOperazione stato, OperazioneRegistrata operazione,
                                     Instant quando, double prezzoUscita, TipoOrdine tipoOrdine) {
        TipoOrdine uscita = stato == StatoOperazione.CHIUSA_ALL_OBIETTIVO
                ? tipoOrdine
                : MatematicaOperazione.USCITA_FORZATA;
        return new Chiusura(stato, quando, prezzoUscita,
                MatematicaOperazione.risultatoInR(operazione.entrata(), operazione.stop(),
                        prezzoUscita, tipoOrdine, uscita));
    }
}
