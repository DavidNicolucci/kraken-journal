package dev.kraken.journal.calcolatore;

import org.springframework.stereotype.Service;

import dev.kraken.journal.calcolatore.dto.EsitoValutazione;
import dev.kraken.journal.calcolatore.dto.RichiestaValutazione;
import dev.kraken.journal.scansione.MatematicaOperazione;
import dev.kraken.journal.scansione.Verso;

/**
 * Il calcolatore "Prima di entrare", spostato dal browser al server.
 *
 * Perche' questa classe esiste: le stesse formule stavano in JavaScript dentro
 * calcolatore.html e in Java dentro ValutatoreSetup. Due implementazioni della
 * stessa aritmetica divergono al primo ritocco, e quando divergono il registro
 * misura qualcosa di diverso da quello che il piano aveva promesso. Adesso la
 * sorgente e' una sola: MatematicaOperazione.
 *
 * Differenza dalla scansione: qui l'obiettivo lo sceglie chi usa la pagina e
 * puo' mancare, ed e' ammessa la vendita allo scoperto. La scansione invece
 * costruisce l'obiettivo a 2 R e tratta solo acquisti.
 */
@Service
public class ServizioCalcolatore {

    /** Sopra questa quota del rischio, le commissioni meritano un avviso. */
    private static final double QUOTA_COMMISSIONI_PESANTI = 0.25;

    public EsitoValutazione valuta(RichiestaValutazione richiesta) {
        if (!richiesta.stopCoerente()) {
            throw new IllegalArgumentException(richiesta.verso() == Verso.ACQUISTO
                    ? "Comprando, lo stop deve stare sotto il prezzo di entrata"
                    : "Vendendo allo scoperto, lo stop deve stare sopra il prezzo di entrata");
        }
        double entrata = richiesta.entrata();
        double stop = richiesta.stop();
        double cIn = richiesta.commissioneEntrataFrazione();
        double cOut = richiesta.commissioneUscitaFrazione();

        double rischioAmmesso = richiesta.capitale() * richiesta.rischioPercentuale() / 100;
        double perditaPerUnita = MatematicaOperazione.rischioPerUnita(entrata, stop, cIn, cOut);
        double quantita = perditaPerUnita > 0 ? rischioAmmesso / perditaPerUnita : 0;
        double controvalore = quantita * entrata;

        double commissioneInEntrata = controvalore * cIn;
        double commissioneInUscita = quantita * stop * cOut;
        double pareggio = MatematicaOperazione.pareggio(entrata, richiesta.verso(), cIn, cOut);

        double distanza = Math.abs(entrata - stop);
        double distanzaPercentuale = distanza / entrata * 100;
        double pareggioPercentuale = Math.abs(pareggio - entrata) / entrata * 100;

        Double guadagno = null;
        Double rapporto = null;
        Double percentualeMinima = null;
        boolean obiettivoNonCopreICosti = false;
        if (richiesta.obiettivoCoerente()) {
            double guadagnoPerUnita = MatematicaOperazione.risultatoPerUnita(
                    entrata, richiesta.obiettivo(), richiesta.verso(), cIn, cOut);
            guadagno = quantita * guadagnoPerUnita;
            rapporto = guadagnoPerUnita / perditaPerUnita;
            // Obiettivo fra entrata e pareggio: anche vincendo sempre si perde,
            // quindi non esiste una percentuale di successo che basti.
            obiettivoNonCopreICosti = guadagnoPerUnita <= 0;
            if (!obiettivoNonCopreICosti) {
                percentualeMinima = 100 / (1 + rapporto);
            }
        }

        double commissioniTotali = commissioneInEntrata + commissioneInUscita;
        return new EsitoValutazione(
                rischioAmmesso, distanza, distanzaPercentuale, perditaPerUnita, quantita, controvalore,
                commissioneInEntrata, commissioneInUscita, commissioniTotali,
                pareggio, pareggioPercentuale, guadagno, rapporto, percentualeMinima,
                controvalore > richiesta.capitale(),
                pareggioPercentuale >= distanzaPercentuale,
                rischioAmmesso > 0 && commissioniTotali > rischioAmmesso * QUOTA_COMMISSIONI_PESANTI,
                obiettivoNonCopreICosti);
    }
}
