package dev.kraken.journal.journal;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Come e' arrivata sul conto una moneta che non risulta comprata.
 *
 * Serve a una cosa sola: quando una vendita non trova il suo acquisto, dire il
 * motivo che il ledger riporta invece di elencare ipotesi.
 */
public enum Provenienza {

    DEPOSITO("deposito da un altro wallet"),
    RICOMPENSA("ricompense, staking o accrediti"),
    TRASFERIMENTO("airdrop o trasferimento"),
    CONVERSIONE("conversione da un'altra moneta");

    private final String descrizione;

    Provenienza(String descrizione) {
        this.descrizione = descrizione;
    }

    /** In uscita viaggia la descrizione: la pagina la mostra cosi' com'e'. */
    @JsonValue
    public String descrizione() {
        return descrizione;
    }
}
