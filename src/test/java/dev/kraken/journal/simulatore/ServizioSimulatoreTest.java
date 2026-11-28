package dev.kraken.journal.simulatore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kraken.journal.kraken.KrakenApiException;
import dev.kraken.journal.mercato.Intervallo;
import dev.kraken.journal.mercato.MercatoClient;
import dev.kraken.journal.mercato.dto.Candela;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.scansione.TipoOrdine;
import dev.kraken.journal.simulatore.dto.Previsione;
import dev.kraken.journal.simulatore.dto.RispostaSimulatore;

class ServizioSimulatoreTest {

    private static final Instant INIZIO = Instant.parse("2024-12-01T00:00:00Z");

    /** Una coppia che Kraken rifiuta o con poca storia viene dichiarata, non persa in silenzio. */
    @Test
    void leCoppieNonAnalizzabiliFinisconoFraLeEscluseConIlMotivo() {
        MercatoClient mercato = mock(MercatoClient.class);
        when(mercato.candeleChiuse("XBTEUR", Intervallo.GIORNALIERO)).thenReturn(candele(719));
        when(mercato.candeleChiuse("ETHEUR", Intervallo.GIORNALIERO)).thenReturn(candele(719));
        when(mercato.candeleChiuse("HYPEEUR", Intervallo.GIORNALIERO)).thenReturn(candele(80));
        when(mercato.candeleChiuse("TOLTAEUR", Intervallo.GIORNALIERO))
                .thenThrow(new KrakenApiException("/0/public/OHLC", List.of("EQuery:Unknown asset pair")));

        RispostaSimulatore risposta = servizio(mercato, List.of("XBTEUR", "ETHEUR", "HYPEEUR", "TOLTAEUR")).simula();

        assertEquals(List.of("XBTEUR", "ETHEUR"), risposta.previsioni().stream().map(Previsione::coppia).toList());
        assertEquals(2, risposta.escluse().size());
        assertTrue(risposta.escluse().get(0).startsWith("HYPEEUR: 80 candele, ne servono almeno 97"), risposta.escluse().get(0));
        assertTrue(risposta.escluse().get(1).startsWith("TOLTAEUR"));
        assertEquals(7, risposta.orizzonte());
        assertEquals(candele(719).get(718).istante().minus(365, ChronoUnit.DAYS), risposta.verifica().dal());
        assertTrue(risposta.verifica().casi() > 0);
    }

    @Test
    void laPrevisioneEAllUltimaCandelaChiusa() {
        MercatoClient mercato = mock(MercatoClient.class);
        List<Candela> candele = candele(300);
        when(mercato.candeleChiuse("XBTEUR", Intervallo.GIORNALIERO)).thenReturn(candele);

        Previsione previsione = servizio(mercato, List.of("XBTEUR")).simula().previsioni().get(0);

        assertEquals(candele.get(299).istante(), previsione.istanteCandela());
        assertEquals(candele.get(299).chiusura(), previsione.chiusura(), 1e-12);
        assertTrue(previsione.prezzoBasso() <= previsione.prezzoMediano());
        assertTrue(previsione.prezzoMediano() <= previsione.prezzoAlto());
    }

    private static ServizioSimulatore servizio(MercatoClient mercato, List<String> watchlist) {
        // Il download in parallelo e' quello vero: chiama le candele delle singole coppie stubbate sopra.
        when(mercato.candeleChiusePerCoppia(anyList(), any())).thenCallRealMethod();
        var scansione = new ProprietaScansione(watchlist, 1_000_000, 12, 5, 10, 2.0,
                203, 1, 5, 0.5, 15, 15, 2, TipoOrdine.LIMITE, 20, 1.5, null);
        return new ServizioSimulatore(mercato, scansione, new ProprietaSimulatore(7, 365, 30));
    }

    /** Un'onda con una leggera salita: abbastanza varia da attraversare tutti i regimi. */
    private static List<Candela> candele(int quante) {
        List<Candela> candele = new ArrayList<>();
        for (int g = 0; g < quante; g++) {
            double c = 100 * (1 + 0.0005 * g) * (1 + 0.15 * Math.sin(g / 15.0)) * (1 + 0.02 * Math.sin(g * 1.7));
            candele.add(new Candela(INIZIO.plus(g, ChronoUnit.DAYS), c, c * 1.02, c * 0.98, c, c, 1000));
        }
        return candele;
    }
}
