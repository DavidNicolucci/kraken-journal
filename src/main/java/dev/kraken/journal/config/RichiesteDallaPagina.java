package dev.kraken.journal.config;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Le chiamate che cambiano qualcosa arrivano solo dalle pagine dell'app.
 *
 * Una pagina di un altro sito aperta nello stesso browser puo' mandare un POST
 * senza corpo a localhost:8080 senza chiedere niente a nessuno: bastava per
 * far partire il lavoro giornaliero o la risoluzione. Con un header in piu' il
 * browser deve prima chiedere il permesso al server, e il server non lo da':
 * le pagine dell'app l'header lo mettono, le altre non possono.
 */
@Component
public class RichiesteDallaPagina extends OncePerRequestFilter {

    public static final String HEADER = "X-Journal";
    private static final Set<String> METODI_SICURI = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest richiesta, HttpServletResponse risposta, FilterChain catena)
            throws ServletException, IOException {
        if (richiesta.getRequestURI().startsWith("/api/") && !METODI_SICURI.contains(richiesta.getMethod())
                && richiesta.getHeader(HEADER) == null) {
            risposta.sendError(HttpStatus.FORBIDDEN.value(), "Manca l'header " + HEADER + ": la richiesta non viene da una pagina dell'app");
            return;
        }
        catena.doFilter(richiesta, risposta);
    }
}
