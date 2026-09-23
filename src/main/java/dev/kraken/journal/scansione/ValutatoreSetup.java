package dev.kraken.journal.scansione;

import org.springframework.stereotype.Component;

import dev.kraken.journal.scansione.dto.Indicatori;
import dev.kraken.journal.scansione.dto.Piano;

/**
 * Traduce indicatori e soglie nei numeri di un'operazione: stop, obiettivo,
 * quantita', pareggio, rapporto.
 *
 * Le formule di rischio e commissioni stanno in MatematicaOperazione, che e' lo
 * stesso posto da cui le prende il registro quando chiude un'operazione: il
 * piano e la sua misura devono parlare la stessa lingua.
 *
 * Le stesse formule vivono ancora anche dentro calcolatore.html. Finche'
 * restano in due posti divergeranno al primo ritocco: il passo successivo e'
 * far chiamare questo endpoint alla pagina.
 *
 * L'entrata e' l'ULTIMO PREZZO, non la chiusura di ieri. La regola R6 della
 * guida dice "al prezzo di adesso, non al prezzo che vorrei": calcolare il
 * rapporto su una chiusura vecchia di ore e' esattamente il modo di farlo
 * sembrare migliore di quello che e'.
 *
 * CORRETTO il 23/09. L'obiettivo era il massimo a 30 giorni. Sbagliato: dopo
 * una rottura del massimo a 20 giorni quel livello e' gia' stato superato, e
 * la sua distanza mediana dall'entrata risultava 0,08 R. Con quel codice il
 * controllo sul rapporto bocciava 90 setup su 91 e la scansione non avrebbe
 * mai prodotto una coppia idonea: nessun errore, nessun log, solo una lista
 * vuota ogni mattina. Adesso l'obiettivo si costruisce a 2 R dalla regola di
 * uscita, e il massimo a 30 giorni resta come livello da NON avere in mezzo.
 */
@Component
public class ValutatoreSetup {

    private final ProprietaScansione proprieta;

    public ValutatoreSetup(ProprietaScansione proprieta) {
        this.proprieta = proprieta;
    }

    public Piano valuta(double ultimoPrezzo, Indicatori indicatori) {
        TipoOrdine tipoOrdine = proprieta.tipoOrdine();
        double stop = indicatori.minimoPerStop() * (1 - proprieta.margineStop() / 100);

        double perditaPerUnita = MatematicaOperazione.rischioPerUnita(ultimoPrezzo, stop, tipoOrdine);
        double obiettivo = MatematicaOperazione.obiettivoPerMultiplo(ultimoPrezzo, perditaPerUnita,
                proprieta.multiploObiettivo(), tipoOrdine);
        double guadagnoPerUnita = MatematicaOperazione.risultatoPerUnita(ultimoPrezzo, obiettivo, tipoOrdine);
        double quantita = perditaPerUnita <= 0 ? 0 : proprieta.rischioAmmesso() / perditaPerUnita;

        return new Piano(
                ultimoPrezzo,
                stop,
                obiettivo,
                (ultimoPrezzo - stop) / ultimoPrezzo * 100,
                MatematicaOperazione.pareggio(ultimoPrezzo, tipoOrdine),
                quantita,
                quantita * ultimoPrezzo,
                quantita * perditaPerUnita,
                quantita * guadagnoPerUnita,
                perditaPerUnita <= 0 ? 0 : guadagnoPerUnita / perditaPerUnita);
    }
}
