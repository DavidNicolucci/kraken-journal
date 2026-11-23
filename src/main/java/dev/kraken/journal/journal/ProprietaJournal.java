package dev.kraken.journal.journal;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Le soglie con cui il journal legge lo storico. Come per la scansione stanno
 * in configurazione: cambiarle cambia il modo di leggere i dati.
 *
 * @param residuoTrascurabile sotto questa percentuale della quantita' massima
 *        tenuta, quello che resta dopo una vendita e' polvere e non una
 *        posizione ancora aperta. Percentuale e non importo: un importo fisso
 *        vorrebbe dire una cosa in EUR e un'altra in USD.
 */
@ConfigurationProperties(prefix = "journal")
public record ProprietaJournal(BigDecimal residuoTrascurabile) {

    public ProprietaJournal {
        if (residuoTrascurabile == null || residuoTrascurabile.signum() < 0) {
            throw new IllegalArgumentException("journal.residuo-trascurabile deve essere una percentuale, zero o piu'");
        }
    }
}
