package dev.kraken.journal.scansione;

/**
 * La matematica di rischio e commissioni, in un posto solo.
 *
 * Esiste perche' gli stessi due conti servono in due momenti lontani: quando
 * si pianifica un'operazione e quando la si chiude per misurarne l'esito. Se
 * vivessero in due classi finirebbero per divergere, e il registro misurerebbe
 * qualcosa di diverso da quello che il piano aveva promesso.
 *
 * Convenzione: una R e' quello che si perde davvero allo stop, commissioni
 * comprese. Quindi "meno una R" e' esattamente la perdita pianificata, non la
 * sola distanza dal prezzo di entrata. E' l'unico modo perche' il risultato
 * medio in R del registro sia confrontabile con le soglie della guida.
 *
 * CORRETTO il 23/11. Lo stop si pagava alla tariffa dell'entrata, cioe' come
 * ordine limite. Ma uno stop che scatta prende il prezzo che c'e': e' sempre
 * un ordine a mercato. Con l'entrata a limite la perdita vera allo stop era
 * fra 1,03 e 1,07 volte quella pianificata, a seconda dell'ampiezza.
 */
public final class MatematicaOperazione {

    /**
     * Come si esce quando non si sceglie il prezzo: allo stop e al time stop.
     * L'obiettivo invece e' un ordine in attesa, e paga la tariffa scelta.
     */
    public static final TipoOrdine USCITA_FORZATA = TipoOrdine.MERCATO;

    private MatematicaOperazione() {
    }

    /** Quanto si perde per ogni unita' se lo stop viene colpito: si esce a mercato. */
    public static double rischioPerUnita(double entrata, double stop, TipoOrdine tipoOrdine) {
        return rischioPerUnita(entrata, stop, tipoOrdine.commissione(), USCITA_FORZATA.commissione());
    }

    /** Quanto si guadagna o si perde per ogni unita' uscendo a un dato prezzo. */
    public static double risultatoPerUnita(double entrata, double uscita, TipoOrdine tipoOrdine) {
        return risultatoPerUnita(entrata, uscita, Verso.ACQUISTO,
                tipoOrdine.commissione(), tipoOrdine.commissione());
    }

    /**
     * Versione generale, con commissioni diverse sui due lati.
     *
     * Serve al calcolatore, dove entrata e uscita possono essere una a limite e
     * l'altra a mercato. Il valore assoluto copre anche lo scoperto, dove lo
     * stop sta sopra l'entrata.
     */
    public static double rischioPerUnita(double entrata, double stop,
                                         double commissioneEntrata, double commissioneUscita) {
        return Math.abs(entrata - stop) + entrata * commissioneEntrata + stop * commissioneUscita;
    }

    /** Versione generale: direzione esplicita e commissioni diverse sui due lati. */
    public static double risultatoPerUnita(double entrata, double uscita, Verso verso,
                                           double commissioneEntrata, double commissioneUscita) {
        return verso.variazioneFavorevole(entrata, uscita)
                - entrata * commissioneEntrata - uscita * commissioneUscita;
    }

    /**
     * Versione generale del pareggio.
     * Acquisto:  P(1 - uscita) = entrata(1 + entrata)
     * Scoperto:  P(1 + uscita) = entrata(1 - entrata)
     */
    public static double pareggio(double entrata, Verso verso,
                                  double commissioneEntrata, double commissioneUscita) {
        return verso == Verso.ACQUISTO
                ? entrata * (1 + commissioneEntrata) / (1 - commissioneUscita)
                : entrata * (1 - commissioneEntrata) / (1 + commissioneUscita);
    }

    /**
     * Risultato espresso in R. Vale -1 quando si esce esattamente allo stop,
     * qualunque sia l'ampiezza: e' quello che rende confrontabili fra loro
     * operazioni su coppie con volatilita' diverse.
     *
     * @param tipoEntrata come si e' entrati, e come si esce all'obiettivo.
     * @param tipoUscita come si e' usciti questa volta: {@link #USCITA_FORZATA}
     *        allo stop e al time stop, la tariffa scelta all'obiettivo.
     */
    public static double risultatoInR(double entrata, double stop, double uscita,
                                      TipoOrdine tipoEntrata, TipoOrdine tipoUscita) {
        double rischio = rischioPerUnita(entrata, stop, tipoEntrata);
        return rischio <= 0 ? 0 : risultatoPerUnita(entrata, uscita, Verso.ACQUISTO,
                tipoEntrata.commissione(), tipoUscita.commissione()) / rischio;
    }

    /** Prezzo a cui si esce in pari dopo aver pagato le commissioni sui due lati. */
    public static double pareggio(double entrata, TipoOrdine tipoOrdine) {
        return entrata * (1 + tipoOrdine.commissione()) / (1 - tipoOrdine.commissione());
    }

    /**
     * Prezzo che, una volta pagate le commissioni, vale esattamente il multiplo
     * di R richiesto.
     *
     * Non basta sommare due volte il rischio all'entrata: su quel prezzo si
     * paga un'altra commissione, quindi il 2 R sarebbe in realta' qualcosa meno.
     * Qui l'equazione e' risolta al contrario, partendo dal risultato voluto.
     */
    public static double obiettivoPerMultiplo(double entrata, double rischioPerUnita,
                                              double multiplo, TipoOrdine tipoOrdine) {
        double commissione = tipoOrdine.commissione();
        return (multiplo * rischioPerUnita + entrata * (1 + commissione)) / (1 - commissione);
    }
}
