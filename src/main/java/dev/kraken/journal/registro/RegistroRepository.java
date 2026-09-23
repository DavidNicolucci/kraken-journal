package dev.kraken.journal.registro;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroRepository extends JpaRepository<OperazioneRegistrata, Long> {

    List<OperazioneRegistrata> findByStato(StatoOperazione stato);

    List<OperazioneRegistrata> findAllByOrderByDataRegistrazioneDesc();

    boolean existsByCoppiaAndStato(String coppia, StatoOperazione stato);

    long countByStatoAndReale(StatoOperazione stato, boolean reale);

    long countByCoppia(String coppia);
}
