package dev.kraken.journal.registro.dto;

/**
 * Come sta andando il registro.
 *
 * @param risultatoMedioInR il numero che conta davvero. La soglia da battere
 *        non e' zero ma il costo delle commissioni, misurato intorno a 0,12 R
 *        per operazione: sotto quel valore la regola sta perdendo anche quando
 *        i verdi sono piu' dei rossi.
 * @param registrazioniMancanti quante ne servono ancora prima di poter dire
 *        qualcosa. Finche' e' maggiore di zero, il risultato medio va letto
 *        come rumore.
 * @param posizioniRealiAperte contate a parte dalle analisi su carta: il tetto
 *        della regola 2.3 vale solo su queste. La carta non ha limite perche'
 *        non costa commissioni e non corre rischio.
 */
public record StatisticheRegistro(
        long aperte,
        long chiuse,
        long allObiettivo,
        long alloStop,
        long perTempo,
        double percentualeAllObiettivo,
        double risultatoMedioInR,
        double risultatoTotaleInR,
        long registrazioniMancanti,
        long posizioniRealiAperte,
        int posizioniMassime) {
}
