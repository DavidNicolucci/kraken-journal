package dev.kraken.journal.calcolatore.dto;

import dev.kraken.journal.scansione.Verso;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Quello che la pagina del calcolatore manda al server.
 *
 * Le commissioni arrivano in percentuale (0,40 e non 0,004) perche' e' il
 * valore che sta nei due menu a tendina della pagina: convertire qui, una
 * volta, evita di doversi ricordare l'unita' da entrambe le parti.
 *
 * @param obiettivo facoltativo: senza, il calcolatore dice comunque quantita',
 *        pareggio e costi, e tace sul rapporto invece di inventarlo.
 * @param commissioneUscita quella all'obiettivo e al pareggio: lo stop esce
 *        sempre a mercato. Al massimo 10%: oltre non e' una tariffa di Kraken,
 *        e al 100% il pareggio diventerebbe una divisione per zero.
 */
public record RichiestaValutazione(
        @Positive double capitale,
        @Positive double rischioPercentuale,
        @NotNull Verso verso,
        @Positive double entrata,
        @Positive double stop,
        @PositiveOrZero double obiettivo,
        @PositiveOrZero @DecimalMax("10") double commissioneEntrata,
        @PositiveOrZero @DecimalMax("10") double commissioneUscita) {

    public double commissioneEntrataFrazione() {
        return commissioneEntrata / 100;
    }

    public double commissioneUscitaFrazione() {
        return commissioneUscita / 100;
    }

    /** Comprando lo stop sta sotto; vendendo allo scoperto sta sopra. */
    public boolean stopCoerente() {
        return verso == Verso.ACQUISTO ? stop < entrata : stop > entrata;
    }

    public boolean obiettivoCoerente() {
        return obiettivo > 0 && verso.variazioneFavorevole(entrata, obiettivo) > 0;
    }
}
