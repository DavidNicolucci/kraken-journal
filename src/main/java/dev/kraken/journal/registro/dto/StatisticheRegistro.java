package dev.kraken.journal.registro.dto;

import java.util.List;

import dev.kraken.journal.registro.Trigger;

/**
 * Come sta andando il registro.
 *
 * I risultati stanno solo nei gruppi, uno per regola e per tipo (carta o
 * reale). Una media unica mescolava le analisi su carta con le operazioni
 * vere e un trigger con l'altro: il numero non diceva quale ipotesi stesse
 * funzionando, che e' il motivo per cui il registro esiste.
 *
 * @param aperte carta e reali insieme: serve solo a sapere quante ne restano.
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
        long posizioniRealiAperte,
        int posizioniMassime,
        List<Gruppo> gruppi) {

    /**
     * I risultati di una regola, su carta o con soldi veri.
     *
     * @param risultatoMedioInR gia' al netto delle commissioni, perche' una R e'
     *        la perdita allo stop commissioni comprese. La soglia da battere e'
     *        quindi zero. Null se non ci sono operazioni chiuse.
     * @param margineInR mezza ampiezza dell'intervallo al 95% sulla media. Il
     *        vantaggio e' misurato solo se media meno margine resta sopra zero.
     *        Null sotto le due operazioni chiuse.
     * @param registrazioniMancanti quante operazioni chiuse servono ancora per
     *        arrivare a {@code registrazioni-per-giudizio}.
     */
    public record Gruppo(
            Trigger trigger,
            boolean reale,
            long aperte,
            long chiuse,
            double percentualeAllObiettivo,
            Double risultatoMedioInR,
            Double margineInR,
            long registrazioniMancanti) {
    }
}
