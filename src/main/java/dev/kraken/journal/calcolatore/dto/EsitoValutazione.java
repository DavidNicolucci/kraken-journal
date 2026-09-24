package dev.kraken.journal.calcolatore.dto;

/**
 * Tutti i numeri che la pagina mostra. La pagina li formatta e basta: dopo
 * questo record, in JavaScript non resta nessun calcolo di rischio.
 *
 * @param guadagnoAllObiettivo null quando l'obiettivo manca o e' dalla parte
 *        sbagliata. Null e non zero: "non calcolabile" e "vale zero" sono due
 *        cose diverse, e confonderle e' un errore che il journal ha gia' fatto
 *        una volta.
 * @param percentualeMinimaDiSuccesso null anche quando l'obiettivo non copre
 *        i costi: nessuna percentuale di successo porta in pari.
 * @param costiOltreLoStop le commissioni chiedono al prezzo un movimento piu'
 *        grande della distanza dello stop: l'operazione perde comunque.
 * @param obiettivoNonCopreICosti l'obiettivo sta fra entrata e pareggio: e'
 *        dalla parte giusta del prezzo, ma raggiungerlo fa perdere lo stesso.
 */
public record EsitoValutazione(
        double rischioAmmesso,
        double distanza,
        double distanzaPercentuale,
        double perditaPerUnita,
        double quantita,
        double controvalore,
        double commissioneInEntrata,
        double commissioneInUscitaAlloStop,
        double commissioniTotali,
        double pareggio,
        double pareggioPercentuale,
        Double guadagnoAllObiettivo,
        Double rapporto,
        Double percentualeMinimaDiSuccesso,
        boolean capitaleSuperato,
        boolean costiOltreLoStop,
        boolean commissioniPesanti,
        boolean obiettivoNonCopreICosti) {
}
