# Kraken Trading Journal

Strumenti personali per operare su Kraken con un metodo scritto e misurabile:

- **Journal**: statistiche sulle operazioni reali, lette da Kraken e commentate da Claude.
- **Calcolatore**: quantita', pareggio e rapporto rischio/rendimento prima di entrare.
- **Scansione**: controlla ogni giorno una watchlist fissa contro la checklist della guida.
- **Registro**: tiene traccia delle operazioni (su carta o reali) e ne misura l'esito in R.
- **Lavoro giornaliero**: alle 7:00 chiude le operazioni risolte e registra le nuove idonee.

Tutti i numeri sono calcolati da codice deterministico. Il modello riceve solo
i numeri gia' pronti del journal e li interpreta.

## Principio di design

```
Kraken TradesHistory  ->  TradeStatisticsCalculator  ->  Claude  ->  JournalReport
   (dati grezzi)          (numeri, verificabili)        (lettura)
```

Il modello **non calcola nulla**. Se sbaglia, sbaglia nell'interpretazione, e le
metriche restano valide e controllabili a mano. Il system prompt gli vieta
esplicitamente di fare previsioni di prezzo o dare indicazioni operative.

## Setup

1. Su Kraken: Settings -> API -> Add key, con **solo** questi permessi:
   - Query Funds
   - Query Closed Orders & Trades

   Lascia disabilitati *Create & Modify Orders* e **soprattutto** *Withdraw Funds*.
   Una chiave con permesso di prelievo che finisce in un log e' un conto svuotato.

2. Variabili d'ambiente:

```bash
export KRAKEN_API_KEY="..."
export KRAKEN_API_SECRET="..."     # la stringa Base64 fornita da Kraken
export ANTHROPIC_API_KEY="sk-ant-..."
```

3. Avvio e chiamata:

```bash
mvn spring-boot:run
curl "http://localhost:8080/api/journal?days=30"
```

Le pagine web sono servite dalla stessa applicazione:

| Pagina | Indirizzo |
|---|---|
| Journal | http://localhost:8080/ |
| Calcolatore | http://localhost:8080/calcolatore.html |
| Scansione | http://localhost:8080/scansione.html |

Il registro vive in un database H2 su file, in `./dati/`. La cartella e' esclusa
dal repository: contiene dati personali e sopravvive ai riavvii.

## Funzioni

### Journal — `GET /api/journal?days=30`

Scarica `TradesHistory` da Kraken (da 1 a 365 giorni), calcola le metriche con
`TradeStatisticsCalculator` e chiede a Claude una lettura dei numeri. Vedi le
sezioni piu' sotto per i limiti del P&L.

### Calcolatore — `POST /api/valutazione`

Il calcolatore "Prima di entrare". Dati capitale, rischio percentuale, verso
(`ACQUISTO` o scoperto), entrata, stop, obiettivo facoltativo e commissioni in
percentuale (`0.40`, non `0.004`), restituisce:

- quantita' e controvalore per rischiare esattamente la quota scelta;
- commissioni in entrata e in uscita, e prezzo di pareggio;
- guadagno, rapporto rischio/rendimento e percentuale di successo minima,
  solo se l'obiettivo e' indicato: senza obiettivo il rapporto non viene inventato;
- tre avvisi: controvalore oltre il capitale, pareggio piu' lontano dello stop,
  commissioni oltre il 25% del rischio.

Uno stop dalla parte sbagliata dell'entrata restituisce `400` con il motivo.

### Scansione — `GET /api/scansione?soloIdonee=false`

Esamina la watchlist in `application.yml`, congelata al 23/09/2026 per non
scegliere a posteriori le coppie sopravvissute. Per ogni coppia calcola gli
indicatori sulle candele giornaliere chiuse e applica i controlli, in ordine:

| Controllo | Cosa verifica |
|---|---|
| `LIQUIDITA` | controvalore 24h sopra `volume-minimo` |
| `TREND` | media mobile a 20 in salita e prezzo sopra la media |
| `MOMENTO` | RSI a 14 fra `rsi-minimo` e `rsi-massimo` |
| `DISTANZA_DALLA_MEDIA` | prezzo non oltre `distanza-massima-dalla-media` % dalla media |
| `AMPIEZZA_STOP` | stop fra `ampiezza-stop-minima` e `ampiezza-stop-massima` % |
| `RESISTENZA_INTERMEDIA` | il massimo a 30 giorni non sta fra entrata e obiettivo |

Per ogni coppia propone anche un piano:

- **entrata**: l'ultimo prezzo, non la chiusura di ieri;
- **stop**: il minimo degli ultimi `giorni-minimo-stop` giorni, meno `margine-stop` %;
- **obiettivo**: a `multiplo-obiettivo` R, con le commissioni comprese;
- **quantita'**: quella che rischia `rischio-per-operazione` % del `capitale`.

Per impostazione predefinita restituisce anche le coppie bocciate, con il
controllo che le ha fermate: sapere perche' una coppia non passa e' piu' utile
di una lista corta.

### Registro — `/api/registro`

| Metodo | Percorso | Cosa fa |
|---|---|---|
| `POST` | `/api/registro` | registra un'operazione |
| `GET` | `/api/registro` | elenca tutte le operazioni |
| `GET` | `/api/registro/statistiche` | esiti, risultato medio in R, registrazioni mancanti |
| `POST` | `/api/registro/risoluzione` | chiude le operazioni aperte leggendo le candele |

Coppia, trigger (`BREAKOUT_20_GIORNI`, `PULLBACK_MEDIA`, `MANUALE`), entrata,
stop, obiettivo e motivo sono obbligatori: si scrivono **prima** di aprire.
`reale` e' `false` per le operazioni su carta.

La risoluzione legge solo le candele chiuse successive alla registrazione e
chiude allo stop, all'obiettivo o per tempo, dopo `giorni-time-stop` giorni.
Se nella stessa candela vengono toccati sia lo stop sia l'obiettivo, vince
**sempre lo stop**: la candela giornaliera non dice quale sia arrivato prima,
e la lettura pessimistica impedisce al registro di autoassolversi.

Il risultato e' espresso in R, dove 1 R e' la perdita reale allo stop,
commissioni comprese. Sotto `registrazioni-per-giudizio` operazioni chiuse il
risultato medio e' rumore, e le statistiche lo dichiarano. La soglia da battere
non e' zero ma il costo delle commissioni, circa 0,12 R per operazione.

### Lavoro giornaliero — ogni giorno alle 7:00 (Europe/Rome)

1. Risolve le operazioni aperte, cosi' il registro e' aggiornato.
2. Esegue la scansione e prende le coppie che superano tutti i controlli.
3. Le registra tutte **su carta**, saltando le coppie con un'operazione gia' aperta.
4. Indica la prima coppia per rotazione, quella registrata meno volte, come
   candidata per un'operazione reale.

Le posizioni reali restano al massimo `posizioni-massime` (regola 2.3); le
analisi su carta non hanno tetto, perche' non costano commissioni. Alle 7:00 le
candele giornaliere di Kraken, che chiudono a mezzanotte UTC, sono gia' chiuse
in qualunque stagione.

Se il computer era spento alle 7:00, il lavoro si avvia a mano:

```bash
curl -X POST http://localhost:8080/api/giornaliero
```

Le coppie gia' aperte non vengono registrate due volte.

## Configurazione del metodo

Tutte le soglie stanno nella sezione `scansione:` di `application.yml`:
watchlist, volume, RSI, ampiezza dello stop, multiplo dell'obiettivo, capitale,
rischio per operazione, time stop, numero massimo di posizioni reali e tipo di
ordine (`LIMITE` 0,40% o `MERCATO` 0,80% per lato).

Cambiare una soglia significa cambiare metodo. Va fatto li', tutto insieme, e
non dentro il codice. Le formule di rischio e commissioni stanno solo in
`MatematicaOperazione`, usata sia dal piano sia dalla chiusura: cosi' il
registro misura la stessa cosa che il piano aveva promesso.

## Verifica della firma

`KrakenSignerTest` confronta l'output con il vettore di riferimento della
documentazione Kraken. Fallo girare **prima** di dare la colpa alle credenziali:

```bash
mvn test -Dtest=KrakenSignerTest
```

## Limiti da conoscere

**Nonce.** Deve essere strettamente crescente per chiave API. `AtomicLong`
garantisce la monotonicita' dentro il processo: se lanci piu' istanze, usa una
chiave diversa per ciascuna, altrimenti otterrai `EAPI:Invalid nonce`.

**P&L.** Calcolato a costo medio ponderato. **Non e' il criterio fiscale
italiano**, che per le cripto indica il LIFO. Questi numeri servono a capire come
hai operato, non a compilare la dichiarazione: per quella serve un
commercialista o un software fiscale.

**Posizioni preesistenti.** Se detenevi un asset prima dell'inizio del periodo,
il costo di carico e' ignoto e il P&L delle relative vendite risulta
sovrastimato. Allarga la finestra (`days`) per ridurre l'effetto.

**Rate limit.** Kraken usa un contatore a decadimento sugli endpoint privati.
`TradesHistory` e' fra i piu' costosi: non chiamare l'endpoint in loop stretto.
Per uso schedulato, una volta al giorno e' piu' che sufficiente.

**Kraken risponde HTTP 200 anche sugli errori applicativi**, che finiscono
nell'array `error`. `KrakenClient` lo controlla sempre: non fidarti del solo
status code se estendi il client.

## Cosa questo progetto non fa, deliberatamente

Non piazza ordini: la chiave Kraken ha solo permessi di lettura. Non prevede
prezzi. La scansione applica regole scritte e registra i setup su carta; decidere
se aprire davvero resta una scelta manuale.

I setup della scansione vengono da codice deterministico e testabile, mai dal
modello. Claude si limita a commentare le statistiche del journal.

Un LLM non ha capacita' predittiva sui mercati: produce testo plausibile, non
stime probabilistiche calibrate, e non e' backtestabile. Se in futuro vuoi
automatizzare l'esecuzione, la chiave con permesso di trading va introdotta solo dopo aver
usato a lungo `validate=true` per il dry-run.

## Correzioni applicate

**P&L non calcolabile invece di P&L inventato.** Vendere senza un acquisto
corrispondente nel periodo lascia il costo di carico ignoto. Prima il codice
sottraeva solo le commissioni, producendo un numero che sembrava reale: portava
a leggere "ho perso esattamente le fee", che non era mai vero. Ora `realizedPnl`
e' `null` con `pnlComplete: false`, e gli aggregati escludono quelle coppie
invece di sommarle a zero.

**Conversioni valutarie separate dai trade.** EUR/USD non e' un trade.
Includerlo gonfiava conteggio operazioni e frequenza giornaliera. Ora
`tradingTrades` le esclude, `fxConversions` le conta a parte, e i giorni attivi
considerano solo operativita' vera.

**Prezzi con scala dinamica.** Un token da frazioni di centesimo arrotondato a
due decimali diventava `0.00`, che si legge come prezzo zero. Sotto l'unita' si
usano cifre significative.

**Commissioni sul volume.** `feesToVolumePercent` sostituisce il rapporto
fee/P&L come metrica principale sui costi: e' sempre calcolabile e non dipende
dal costo di carico.

**Valute miste.** Quando le coppie hanno quote diverse (USD ed EUR), gli
aggregati sommano importi in valute differenti. `mixedQuoteCurrencies` lo
segnala: leggili come ordine di grandezza.
