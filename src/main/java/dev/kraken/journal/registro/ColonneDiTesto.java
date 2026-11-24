package dev.kraken.journal.registro;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;

/**
 * Le colonne degli enum del registro sono testo semplice.
 *
 * Hibernate 6 le creava come ENUM di H2, con i valori dell'enum di allora: uno
 * stato aggiunto dopo (CHIUSA_A_MANO, NON_ESEGUITA) veniva rifiutato dal
 * database, e ddl-auto=update non tocca una colonna che esiste gia'. Con
 * {@link #ENUM} come definizione le colonne nuove nascono testo, senza nemmeno
 * il vincolo CHECK che Hibernate aggiungerebbe; qui quelle gia' create
 * diventano testo, una volta sola: rifarlo non cambia niente.
 *
 * Dipende da EntityManagerFactory solo per arrivare dopo Hibernate, che crea
 * la tabella e le colonne nuove.
 */
@Component
class ColonneDiTesto {

    static final String ENUM = "varchar(32)";
    private static final List<String> COLONNE = List.of("trigger", "stato", "tipo_ordine");

    private final JdbcTemplate jdbc;

    ColonneDiTesto(JdbcTemplate jdbc, EntityManagerFactory dopoHibernate) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void converti() {
        COLONNE.forEach(colonna -> jdbc.execute(
                "ALTER TABLE IF EXISTS operazione ALTER COLUMN IF EXISTS " + colonna + " SET DATA TYPE " + ENUM));
    }
}
