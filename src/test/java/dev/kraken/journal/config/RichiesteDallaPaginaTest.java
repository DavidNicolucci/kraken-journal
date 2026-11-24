package dev.kraken.journal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RichiesteDallaPaginaTest {

    private final RichiesteDallaPagina filtro = new RichiesteDallaPagina();

    private MockFilterChain passa(String metodo, String percorso, boolean conHeader) throws Exception {
        var richiesta = new MockHttpServletRequest(metodo, percorso);
        if (conHeader) {
            richiesta.addHeader(RichiesteDallaPagina.HEADER, "1");
        }
        var catena = new MockFilterChain();
        var risposta = new MockHttpServletResponse();
        filtro.doFilter(richiesta, risposta, catena);
        if (catena.getRequest() == null) {
            assertEquals(403, risposta.getStatus());
        }
        return catena;
    }

    /** Un POST senza header, come quello che puo' mandare un altro sito, non arriva al controller. */
    @Test
    void unPostSenzaHeaderVieneFermato() throws Exception {
        assertNull(passa("POST", "/api/giornaliero", false).getRequest());
        assertNull(passa("POST", "/api/giornaliero/risoluzione", false).getRequest());
    }

    @Test
    void lePagineDellAppPassano() throws Exception {
        assertNotNull(passa("POST", "/api/giornaliero", true).getRequest());
        assertNotNull(passa("GET", "/api/journal", false).getRequest(), "le letture non cambiano niente");
        assertNotNull(passa("GET", "/index.html", false).getRequest());
    }
}
