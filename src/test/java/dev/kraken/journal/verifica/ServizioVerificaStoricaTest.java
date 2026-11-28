package dev.kraken.journal.verifica;

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
import dev.kraken.journal.scansione.ValutatoreSetup;
import dev.kraken.journal.verifica.dto.Riepilogo;
import dev.kraken.journal.verifica.dto.RispostaVerificaStorica;

class ServizioVerificaStoricaTest {

    private static final Instant INIZIO = Instant.parse("2025-01-01T00:00:00Z");

    /** Una coppia con poca storia o rifiutata da Kraken viene dichiarata, non persa in silenzio. */
    @Test
    void leCoppieNonVerificabiliFinisconoFraLeEscluse() {
        MercatoClient mercato = mock(MercatoClient.class);
        when(mercato.candeleChiusePerCoppia(anyList(), any())).thenCallRealMethod();
        when(mercato.candeleChiuse("XBTEUR", Intervallo.GIORNALIERO)).thenReturn(candele(300));
        when(mercato.candeleChiuse("HYPEEUR", Intervallo.GIORNALIERO)).thenReturn(candele(50));
        when(mercato.candeleChiuse("TOLTAEUR", Intervallo.GIORNALIERO))
                .thenThrow(new KrakenApiException("/0/public/OHLC", List.of("EQuery:Unknown asset pair")));
        var proprieta = new ProprietaScansione(List.of("XBTEUR", "HYPEEUR", "TOLTAEUR"), 1000, 12, 3, 10,
                2.0, 203, 1, 5, 0.5, 15, 3, 2, TipoOrdine.LIMITE, 20, 1.5, null);

        RispostaVerificaStorica risposta = new ServizioVerificaStorica(mercato, proprieta, new ValutatoreSetup(proprieta)).verifica();

        assertEquals(List.of("XBTEUR"), risposta.perCoppia().stream().map(Riepilogo::coppia).toList());
        assertEquals(List.of("HYPEEUR: 50 candele, ne servono piu' di 60", "TOLTAEUR: candele non disponibili"),
                risposta.escluse());
        assertEquals(300 - 60, risposta.giorniEsaminati());
        assertEquals(INIZIO.plus(59, ChronoUnit.DAYS), risposta.dal());
        assertEquals(INIZIO.plus(299, ChronoUnit.DAYS), risposta.al());
        assertEquals(2.03, risposta.rischioPerOperazione(), 1e-9);
        assertTrue(risposta.totale().operazioni() == risposta.operazioni().size());
    }

    private static List<Candela> candele(int quante) {
        List<Candela> candele = new ArrayList<>();
        for (int g = 0; g < quante; g++) {
            double c = 100 * (1 + 0.15 * Math.sin(g / 12.0)) * (1 + 0.001 * g);
            candele.add(new Candela(INIZIO.plus(g, ChronoUnit.DAYS), c, c * 1.02, c * 0.98, c, c, g % 9 == 0 ? 3000 : 1000));
        }
        return candele;
    }
}
