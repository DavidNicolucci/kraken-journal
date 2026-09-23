package dev.kraken.journal.registro;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un'operazione scritta nel registro PRIMA di essere aperta.
 *
 * Tre campi sono obbligatori e non annullabili apposta: stop, obiettivo e
 * motivo. Sono le regole R1, R6 e R7 della guida rese impossibili da saltare:
 * un'operazione senza stop qui non si puo' nemmeno salvare. Non e' un
 * promemoria, e' un vincolo, ed e' l'unica parte di questo programma che
 * affronta davvero il problema documentato, cioe' abbandonare la procedura nel
 * momento in cui si agisce.
 *
 * Il campo {@code reale} distingue la carta dall'operazione vera. Il valore
 * predefinito e' false: finche' un vantaggio non e' dimostrato le registrazioni
 * servono a misurare un'ipotesi, non a giustificare un acquisto.
 *
 * Classe e non record: JPA ha bisogno di un costruttore vuoto e di campi
 * modificabili per scrivere l'esito a posteriori.
 */
@Entity
@Table(name = "operazione")
public class OperazioneRegistrata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String coppia;

    @Column(nullable = false)
    private Instant dataRegistrazione;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Trigger trigger;

    @Column(nullable = false)
    private double entrata;

    @Column(nullable = false)
    private double stop;

    @Column(nullable = false)
    private double obiettivo;

    private double quantita;

    private double rapporto;

    /** Perche' questa operazione. Se non si riesce a scriverlo, non si e' capito. */
    @Column(nullable = false, length = 1000)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatoOperazione stato = StatoOperazione.APERTA;

    @Column(nullable = false)
    private boolean reale;

    private Instant dataChiusura;

    private Double prezzoUscita;

    private Double risultatoInR;

    protected OperazioneRegistrata() {
    }

    public OperazioneRegistrata(String coppia, Trigger trigger, double entrata, double stop,
                                double obiettivo, double quantita, double rapporto,
                                String motivo, boolean reale, Instant dataRegistrazione) {
        this.coppia = coppia;
        this.trigger = trigger;
        this.entrata = entrata;
        this.stop = stop;
        this.obiettivo = obiettivo;
        this.quantita = quantita;
        this.rapporto = rapporto;
        this.motivo = motivo;
        this.reale = reale;
        this.dataRegistrazione = dataRegistrazione;
    }

    /** Scrive l'esito. Una volta chiusa, un'operazione non si riapre. */
    public void chiudi(StatoOperazione esito, Instant quando, double prezzoUscita, double risultatoInR) {
        if (this.stato.chiusa()) {
            throw new IllegalStateException("Operazione " + id + " gia' chiusa come " + this.stato);
        }
        this.stato = esito;
        this.dataChiusura = quando;
        this.prezzoUscita = prezzoUscita;
        this.risultatoInR = risultatoInR;
    }

    public Long id() { return id; }
    public String coppia() { return coppia; }
    public Instant dataRegistrazione() { return dataRegistrazione; }
    public Trigger trigger() { return trigger; }
    public double entrata() { return entrata; }
    public double stop() { return stop; }
    public double obiettivo() { return obiettivo; }
    public double quantita() { return quantita; }
    public double rapporto() { return rapporto; }
    public String motivo() { return motivo; }
    public StatoOperazione stato() { return stato; }
    public boolean reale() { return reale; }
    public Instant dataChiusura() { return dataChiusura; }
    public Double prezzoUscita() { return prezzoUscita; }
    public Double risultatoInR() { return risultatoInR; }
}
