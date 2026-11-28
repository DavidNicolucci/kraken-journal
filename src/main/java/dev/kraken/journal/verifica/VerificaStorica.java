package dev.kraken.journal.verifica;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.registro.OperazioneRegistrata;
import dev.kraken.journal.registro.RisolutoreOperazione;
import dev.kraken.journal.registro.RisolutoreOperazione.Chiusura;
import dev.kraken.journal.registro.Statistica;
import dev.kraken.journal.registro.StatoOperazione;
import dev.kraken.journal.registro.Trigger;
import dev.kraken.journal.scansione.CalcolatoreIndicatori;
import dev.kraken.journal.scansione.Checklist;
import dev.kraken.journal.scansione.Controllo;
import dev.kraken.journal.scansione.Esito;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.scansione.ValutatoreSetup;
import dev.kraken.journal.scansione.dto.EsitoControllo;
import dev.kraken.journal.scansione.dto.Indicatori;
import dev.kraken.journal.scansione.dto.Piano;
import dev.kraken.journal.verifica.dto.OperazioneStorica;
import dev.kraken.journal.verifica.dto.Riepilogo;

/**
 * Il metodo di oggi fatto girare sul passato, un giorno alla volta.
 *
 * Niente qui e' una regola nuova: gli indicatori sono quelli della scansione,
 * i controlli sono la stessa Checklist, il piano lo fa lo stesso
 * ValutatoreSetup e l'esito lo decide lo stesso RisolutoreOperazione del
 * registro. Se la verifica avesse le sue copie, misurerebbe un altro metodo.
 *
 * Per ogni candela chiusa si guardano solo le candele fino a quella, come la
 * mattina dopo la vedeva il lavoro giornaliero. Le differenze dal vivo, tutte
 * dichiarate:
 * - il controvalore delle 24 ore e' quello della candela, non del ticker;
 * - il prezzo del mattino e' l'apertura della candela dopo: il lavoro gira
 *   qualche ora piu' tardi, e le candele orarie di Kraken non arrivano cosi'
 *   indietro;
 * - per lo stesso motivo il giorno di entrata si legge sulla candela
 *   giornaliera, con la regola pessimistica del risolutore: lo stop conta,
 *   l'obiettivo no.
 *
 * Come nel registro, una coppia con un'operazione aperta non ne apre un'altra.
 * Le coppie invece non si limitano fra loro: le analisi su carta non hanno tetto.
 */
final class VerificaStorica {

    private VerificaStorica() {
    }

    /** Quello che e' successo su una coppia. */
    record EsitoCoppia(List<OperazioneStorica> operazioni, int giorni, Map<Controllo, Integer> bocciature, Instant dal) {
    }

    static EsitoCoppia simula(String coppia, List<Candela> candele, ProprietaScansione proprieta, ValutatoreSetup valutatore) {
        List<OperazioneStorica> operazioni = new ArrayList<>();
        Map<Controllo, Integer> bocciature = new EnumMap<>(Controllo.class);
        Instant occupataFino = Instant.MIN;
        int giorni = 0;

        for (int t = CalcolatoreIndicatori.CANDELE_MINIME - 1; t + 1 < candele.size(); t++) {
            Candela mattino = candele.get(t + 1);
            Indicatori indicatori = CalcolatoreIndicatori.calcola(candele.subList(0, t + 1),
                    proprieta.giorniMinimoStop(), proprieta.giorniBreakout());
            Piano piano = valutatore.valuta(mattino.apertura(), indicatori);
            List<EsitoControllo> controlli = Checklist.controlla(candele.get(t).controvalore(), mattino.apertura(),
                    indicatori, piano, proprieta);
            giorni++;
            controlli.stream().filter(c -> c.esito() != Esito.SUPERATO)
                    .forEach(c -> bocciature.merge(c.controllo(), 1, Integer::sum));

            if (controlli.stream().allMatch(c -> c.esito() == Esito.SUPERATO) && mattino.istante().isAfter(occupataFino)) {
                OperazioneStorica operazione = apri(coppia, indicatori, piano, mattino, candele.subList(t + 1, candele.size()), proprieta);
                operazioni.add(operazione);
                occupataFino = operazione.chiusura() == null ? Instant.MAX : operazione.chiusura();
            }
        }
        Instant dal = candele.size() > CalcolatoreIndicatori.CANDELE_MINIME
                ? candele.get(CalcolatoreIndicatori.CANDELE_MINIME - 1).istante() : null;
        return new EsitoCoppia(operazioni, giorni, bocciature, dal);
    }

    private static OperazioneStorica apri(String coppia, Indicatori indicatori, Piano piano, Candela mattino,
                                          List<Candela> dopo, ProprietaScansione proprieta) {
        OperazioneRegistrata operazione = new OperazioneRegistrata(coppia, Trigger.BREAKOUT_20_GIORNI,
                piano.entrata(), piano.stop(), piano.obiettivo(), piano.quantita(), piano.rapporto(),
                "verifica storica", false, mattino.istante(), proprieta.tipoOrdine());
        Chiusura chiusura = RisolutoreOperazione.risolvi(operazione, dopo, List.of(),
                proprieta.giorniTimeStop(), proprieta.tipoOrdine());
        return new OperazioneStorica(coppia, indicatori.istanteUltimaCandela(), piano.entrata(), piano.stop(),
                piano.obiettivo(), piano.rapporto(),
                chiusura == null ? StatoOperazione.APERTA : chiusura.stato(),
                chiusura == null ? null : chiusura.quando(),
                chiusura == null ? null : chiusura.prezzoUscita(),
                chiusura == null ? null : chiusura.risultatoInR());
    }

    /** Il criterio del registro: la media conta solo tolto il margine, e sotto la soglia di casi non conta. */
    static Riepilogo riepilogo(String coppia, List<OperazioneStorica> operazioni, int casiPerGiudizio) {
        double[] risultati = operazioni.stream().filter(o -> o.risultatoInR() != null)
                .mapToDouble(OperazioneStorica::risultatoInR).toArray();
        Double media = risultati.length == 0 ? null : Statistica.media(risultati);
        Double margine = Statistica.margine95(risultati);
        return new Riepilogo(coppia, operazioni.size(), risultati.length,
                conta(operazioni, StatoOperazione.CHIUSA_ALL_OBIETTIVO),
                conta(operazioni, StatoOperazione.CHIUSA_ALLO_STOP),
                conta(operazioni, StatoOperazione.CHIUSA_PER_TEMPO),
                media, margine,
                Arrays.stream(risultati).sum(),
                risultati.length == 0 ? 0 : (double) Arrays.stream(risultati).filter(r -> r > 0).count() / risultati.length,
                giudizio(risultati.length, media, margine, casiPerGiudizio));
    }

    private static int conta(List<OperazioneStorica> operazioni, StatoOperazione stato) {
        return (int) operazioni.stream().filter(o -> o.stato() == stato).count();
    }

    private static Giudizio giudizio(int chiuse, Double media, Double margine, int casiPerGiudizio) {
        if (chiuse < casiPerGiudizio || margine == null) {
            return Giudizio.POCHI_CASI;
        }
        return media - margine > 0 ? Giudizio.POSITIVO : media + margine < 0 ? Giudizio.NEGATIVO : Giudizio.INCERTO;
    }
}
