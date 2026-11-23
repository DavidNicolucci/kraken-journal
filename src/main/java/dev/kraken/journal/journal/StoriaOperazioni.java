package dev.kraken.journal.journal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.kraken.journal.kraken.PairResolver;
import dev.kraken.journal.kraken.dto.KrakenLedgerEntry;
import dev.kraken.journal.kraken.dto.KrakenTrade;

/**
 * Mette insieme le due fonti di Kraken in una storia sola.
 *
 * TradesHistory contiene solo gli ordini di Kraken Pro. Un acquisto fatto
 * dall'app non c'e': sta nel ledger, come una riga "spend" (quello che esce) e
 * una "receive" (quello che entra) con lo stesso riferimento. Leggere solo i
 * trade voleva dire trovare vendite senza il loro acquisto e dichiararne il
 * risultato non calcolabile, quando l'acquisto esisteva ed era a due chiamate
 * di distanza.
 *
 * Limite noto: prelievi e depositi non spostano le quantita' in carico. Un
 * deposito viene solo ricordato come provenienza; una moneta prelevata verso
 * un altro wallet resta in carico come posizione aperta.
 */
@Component
public class StoriaOperazioni {

    private static final Logger log = LoggerFactory.getLogger(StoriaOperazioni.class);
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final String SPESA = "spend";
    private static final String INCASSO = "receive";

    private final PairResolver pairResolver;

    public StoriaOperazioni(PairResolver pairResolver) {
        this.pairResolver = pairResolver;
    }

    /**
     * @param ledger null se non e' stato possibile leggerlo: la storia si
     *        compone lo stesso con i soli trade, e lo dichiara.
     */
    public Storia componi(List<KrakenTrade> trade, List<KrakenLedgerEntry> ledger) {
        List<Movimento> movimenti = new ArrayList<>();
        trade.forEach(t -> movimenti.add(new Movimento(t.timestamp(), t.orderTxId(),
                pairResolver.resolve(t.pair()), t.isBuy(), Origine.PRO, t.maker(), t.vol(), t.cost(), t.fee())));

        Map<String, Set<Provenienza>> arrivi = new TreeMap<>();
        if (ledger != null) {
            ledger.stream()
                    .filter(riga -> riga.refid() != null && (SPESA.equals(riga.type()) || INCASSO.equals(riga.type())))
                    .collect(Collectors.groupingBy(KrakenLedgerEntry::refid, LinkedHashMap::new, Collectors.toList()))
                    .values().forEach(righe -> daApp(righe, movimenti, arrivi));
            ledger.forEach(riga -> provenienza(riga).ifPresent(p -> annota(arrivi, riga.asset(), p)));
        }
        movimenti.sort(Comparator.comparing(Movimento::istante));
        return new Storia(List.copyOf(movimenti), arrivi, ledger != null);
    }

    /**
     * Un'operazione dell'app: e' un acquisto quando esce valuta ed entra una
     * moneta, in tutti gli altri casi e' la vendita di quello che esce.
     *
     * La quantita' e' al netto della commissione pagata nella moneta: e' quello
     * che entra o esce davvero dal saldo, e cosi' una vendita di tutto il saldo
     * azzera la posizione. Quella commissione si converte al prezzo
     * dell'operazione e si somma a quella in valuta: se Kraken la trattiene
     * nella moneta, l'app non deve sembrare gratis. Controvalore e commissione
     * sono costruiti in modo che la loro somma (acquisto) o differenza
     * (vendita) sia esattamente la valuta uscita o entrata dal conto.
     */
    private void daApp(List<KrakenLedgerEntry> righe, List<Movimento> movimenti,
                       Map<String, Set<Provenienza>> arrivi) {
        KrakenLedgerEntry spesa = unica(righe, SPESA);
        KrakenLedgerEntry incasso = unica(righe, INCASSO);
        if (spesa == null || incasso == null || spesa.amount().signum() == 0 || incasso.amount().signum() == 0) {
            log.warn("Operazione dell'app {} senza una spesa e un incasso: ignorata ({})", righe.get(0).refid(),
                    righe.stream().map(r -> r.type() + " " + r.amount() + " " + r.asset() + " fee " + r.fee()).collect(Collectors.joining(", ")));
            return;
        }
        boolean acquisto = pairResolver.fiat(spesa.asset()) && !pairResolver.fiat(incasso.asset());
        KrakenLedgerEntry moneta = acquisto ? incasso : spesa;
        KrakenLedgerEntry valuta = acquisto ? spesa : incasso;

        BigDecimal prezzo = valuta.amount().abs().divide(moneta.amount().abs(), MC);
        BigDecimal quantita = moneta.amount().subtract(moneta.fee()).abs();
        movimenti.add(new Movimento(spesa.timestamp(), spesa.refid(),
                pairResolver.coppia(moneta.asset(), valuta.asset()), acquisto, Origine.APP, null,
                quantita, prezzo.multiply(quantita, MC), valuta.fee().add(moneta.fee().multiply(prezzo, MC))));

        // Moneta scambiata con un'altra moneta: quella che entra non ha un
        // costo in valuta, e una sua vendita futura lo deve poter dire.
        if (!pairResolver.fiat(spesa.asset()) && !pairResolver.fiat(incasso.asset())) {
            annota(arrivi, incasso.asset(), Provenienza.CONVERSIONE);
        }
    }

    private static KrakenLedgerEntry unica(List<KrakenLedgerEntry> righe, String tipo) {
        List<KrakenLedgerEntry> diQuelTipo = righe.stream().filter(r -> tipo.equals(r.type())).toList();
        return diQuelTipo.size() == 1 ? diQuelTipo.get(0) : null;
    }

    /**
     * Solo le entrate di moneta che non sono un acquisto. I trasferimenti con
     * un sottotipo sono spostamenti interni (verso lo staking e ritorno): la
     * moneta era gia' sul conto, quindi non contano come arrivo.
     */
    private Optional<Provenienza> provenienza(KrakenLedgerEntry riga) {
        if (riga.amount().signum() <= 0 || riga.asset() == null || riga.type() == null
                || pairResolver.fiat(riga.asset())) {
            return Optional.empty();
        }
        boolean conSottotipo = riga.subtype() != null && !riga.subtype().isBlank();
        return Optional.ofNullable(switch (riga.type()) {
            case "deposit" -> Provenienza.DEPOSITO;
            case "reward", "staking", "dividend", "credit" -> Provenienza.RICOMPENSA;
            case "earn" -> "reward".equals(riga.subtype()) ? Provenienza.RICOMPENSA : null;
            case "transfer" -> conSottotipo ? null : Provenienza.TRASFERIMENTO;
            case "conversion", "adjustment" -> Provenienza.CONVERSIONE;
            default -> null;
        });
    }

    private void annota(Map<String, Set<Provenienza>> arrivi, String codice, Provenienza provenienza) {
        arrivi.computeIfAbsent(pairResolver.asset(codice), k -> new TreeSet<>()).add(provenienza);
    }
}
