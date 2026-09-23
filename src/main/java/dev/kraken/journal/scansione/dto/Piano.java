package dev.kraken.journal.scansione.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * I numeri di un'ipotetica operazione, gia' al netto delle commissioni.
 *
 * "Ipotetica" e' la parola importante: questo record dice quanto costerebbe e
 * quanto renderebbe il setup, non se convenga farlo. Non esiste un campo che
 * dica di comprare, e non va aggiunto: la decisione non e' un dato.
 *
 * @param pareggio prezzo a cui si esce in pari dopo aver pagato le commissioni
 *        su entrambi i lati. E' sempre sopra l'entrata: e' il vero punto zero.
 * @param rapporto guadagno per unita' diviso perdita per unita', commissioni
 *        incluse da entrambe le parti.
 */
public record Piano(
        double entrata,
        double stop,
        double obiettivo,
        double ampiezzaStop,
        double pareggio,
        double quantita,
        double controvalore,
        double perditaAlloStop,
        double guadagnoAllObiettivo,
        double rapporto) {

    /** Quante volte su cento serve azzeccarla per andare in pari. */
    @JsonProperty("percentualeMinimaDiSuccesso")
    public double percentualeMinimaDiSuccesso() {
        return rapporto <= 0 ? 100 : 100 / (1 + rapporto);
    }
}
