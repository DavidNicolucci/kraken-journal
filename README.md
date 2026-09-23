# Kraken Trading Journal

Journal automatico delle operazioni Kraken. Le metriche sono calcolate da codice
deterministico; il modello riceve i numeri gia' pronti e li interpreta.

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

Non piazza ordini. Non genera segnali di ingresso o uscita. Non prevede prezzi.

Un LLM non ha capacita' predittiva sui mercati: produce testo plausibile, non
stime probabilistiche calibrate, e non e' backtestabile. Se in futuro vuoi
automatizzare l'esecuzione, la logica di segnale e di risk management va scritta
in codice deterministico e testabile, con l'AI al massimo come layer di
spiegazione. E la chiave con permesso di trading va introdotta solo dopo aver
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
