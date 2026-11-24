# Kraken Trading Journal

Strumenti personali per operare su Kraken con un metodo scritto e misurabile:

- **Journal**: statistiche sulle operazioni reali (Kraken Pro e app), lette da Kraken e commentate da regole fisse.
- **Calcolatore**: quantita', pareggio e rapporto rischio/rendimento prima di entrare.
- **Scansione**: controlla ogni giorno una watchlist fissa contro la checklist della guida.
- **Registro**: tiene traccia delle operazioni (su carta o reali) e ne misura l'esito in R.
- **Lavoro giornaliero**: alle 7:00 chiude le operazioni risolte e registra le nuove idonee.

Tutto e' calcolato e scritto da codice deterministico, commento compreso: dal
computer non esce nessun dato, a parte le chiamate a Kraken.

## Principio di design

```
Kraken TradesHistory + Ledgers  ->  StoriaOperazioni  ->  TradeStatisticsCalculator  ->  GeneratoreCommento  ->  JournalReport
   (dati grezzi)                    (una storia sola)     (numeri, verificabili)        (regole scritte)
```

Il commento applica le regole di `docs/regole-commento-journal.md`, ciascuna
con il suo test: stessi dati, stesso testo. Non fa previsioni di prezzo e non
da' indicazioni operative. Fino al 23/11/2026 lo scriveva Claude; quella
versione e' nella storia di git.

## Setup

1. Su Kraken: Settings -> API -> Add key, con **solo** questi permessi:
   - Query Closed Orders & Trades
   - Query Ledger Entries: serve a leggere gli acquisti fatti dall'app Kraken,
     che non compaiono fra i trade. Senza, il journal funziona lo stesso ma
     dichiara che quelle operazioni mancano.

   Lascia disabilitati *Create & Modify Orders* e **soprattutto** *Withdraw Funds*.
   Una chiave con permesso di prelievo che finisce in un log e' un conto svuotato.

2. Variabili d'ambiente:

```bash
export KRAKEN_API_KEY="..."
export KRAKEN_API_SECRET="..."     # la stringa Base64 fornita da Kraken
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

Scarica da Kraken **tutto lo storico** del conto: `TradesHistory` per gli
ordini di Kraken Pro e `Ledgers` per le operazioni fatte dall'app, che compaiono
solo li' come righe `spend` e `receive`. `StoriaOperazioni` le mette in una
storia sola, `TradeStatisticsCalculator` calcola le metriche e
`GeneratoreCommento` le commenta con le regole scritte.

Il periodo (`days`, da 1 a 365) decide cosa riportare, non da dove ricostruire
il costo di carico: una vendita di oggi trova il suo acquisto anche se e' di
mesi fa. Oltre alle metriche per coppia, la risposta contiene:

- `ordini`: un ordine eseguito in piu' pezzi conta una volta;
- `totaliPerValuta`: volume, commissioni, quota taker e risultato, esatti per
  ogni valuta invece di sommare EUR e USD;
- `operazioniChiuse`: i giri completi chiusi nel periodo, dalla prima entrata a
  quando della moneta resta solo polvere;
- `posizioniAperte`: quello che e' in carico adesso, con il suo costo;
- `ledgerLetto`: `false` se la chiave non ha il permesso sul ledger;
- `storicoCompleto`: `false` se Kraken si e' fermato al tetto di 2.000 voci
  (40 pagine): le vendite di monete comprate prima risultano senza costo di
  carico, e il commento lo dice.

Vedi le sezioni piu' sotto per i limiti del P&L.

### Calcolatore — `POST /api/valutazione`

Il calcolatore "Prima di entrare". Dati capitale, rischio percentuale, verso
(`ACQUISTO` o scoperto), entrata, stop, obiettivo facoltativo e commissioni in
percentuale (`0.40`, non `0.004`), restituisce:

- quantita' e controvalore per rischiare esattamente la quota scelta. Lo stop
  esce sempre a mercato, come nella scansione: la commissione d'uscita scelta
  vale per l'obiettivo e il pareggio, e stessa operazione vuol dire stessa
  quantita' nelle due pagine;
- commissioni in entrata e allo stop, e prezzo di pareggio;
- guadagno, rapporto rischio/rendimento e percentuale di successo minima,
  solo se l'obiettivo e' indicato: senza obiettivo il rapporto non viene inventato;
- tre avvisi: controvalore piu' commissione d'entrata oltre il capitale,
  pareggio piu' lontano dello stop, commissioni oltre il 25% del rischio.

Uno stop dalla parte sbagliata dell'entrata restituisce `400` con il motivo.

### Scansione — `GET /api/scansione?soloIdonee=false`

Esamina la watchlist in `application.yml`, congelata al 19/11/2026 per non
scegliere a posteriori le coppie sopravvissute. Per ogni coppia calcola gli
indicatori sulle candele giornaliere chiuse e applica i controlli, in ordine:

| Controllo | Cosa verifica |
|---|---|
| `LIQUIDITA` | controvalore 24h sopra `volume-minimo` |
| `TREND` | media mobile a 20 in salita e prezzo sopra la media |
| `BREAKOUT` | il trigger: chiusura sopra il massimo dei `giorni-breakout` giorni prima, con controvalore almeno `multiplo-volume-breakout` volte la loro media |
| `DISTANZA_DALLA_MEDIA` | prezzo non oltre `distanza-massima-dalla-media` % dalla media |
| `PREZZO_ATTUALE` | il prezzo di stamattina conferma la chiusura di ieri: sopra media e rottura, entro la stessa distanza dalla media |
| `AMPIEZZA_STOP` | stop fra `ampiezza-stop-minima` e `ampiezza-stop-massima` % |

Per ogni coppia propone anche un piano:

- **entrata**: l'ultimo prezzo, non la chiusura di ieri;
- **stop**: il minimo degli ultimi `giorni-minimo-stop` giorni, meno `margine-stop` %;
- **obiettivo**: a `multiplo-obiettivo` R, con le commissioni comprese;
- **quantita'**: quella che rischia `rischio-per-operazione` % del `capitale`.

Per impostazione predefinita restituisce anche le coppie bocciate, con il
controllo che le ha fermate: sapere perche' una coppia non passa e' piu' utile
di una lista corta. Le soglie si confrontano con il valore mostrato, arrotondato
come lo si legge: uno stop al 4,96% si legge 5,0% e passa "tra 5% e 10%".

### Registro — `/api/registro`

| Metodo | Percorso | Cosa fa |
|---|---|---|
| `POST` | `/api/registro` | registra un'operazione |
| `GET` | `/api/registro` | elenca tutte le operazioni |
| `GET` | `/api/registro/statistiche` | esiti e, per ogni regola su carta e reale, risultato medio in R con margine d'errore |
| `POST` | `/api/registro/{id}/chiusura` | chiude a mano un'operazione reale: `{"prezzoUscita": 70.5}` |
| `POST` | `/api/giornaliero/risoluzione` | chiude le operazioni risolte (era `/api/registro/risoluzione`) |

Coppia, trigger (`BREAKOUT_20_GIORNI`, `PULLBACK_MEDIA`, `MANUALE`), entrata,
stop, obiettivo e motivo sono obbligatori: si scrivono **prima** di aprire.
`reale` e' `false` per le operazioni su carta; un'operazione reale ha anche la
quantita', e oltre `posizioni-massime` posizioni reali aperte viene rifiutata.
La coppia si puo' scrivere come si vuole (XBTEUR, XBT/EUR, BTC/EUR): nel
registro finisce col nome della watchlist. Il rapporto lo calcola il registro,
e con esso la tariffa dell'operazione, che resta quella anche se poi cambi
`tipo-ordine`.

**Le analisi su carta** si chiudono sulle candele chiuse successive alla
registrazione: allo stop, all'obiettivo o per tempo, dopo `giorni-time-stop`
giorni pieni, alla chiusura dell'ultimo. Il giorno di registrazione si legge
sulle candele orarie (Kraken ne tiene trenta giorni): un minimo fatto prima
dell'entrata non e' uno stop, un obiettivo toccato nel pomeriggio conta. Se in
una stessa candela vengono toccati sia lo stop sia l'obiettivo, vince **sempre
lo stop**: la candela non dice quale sia arrivato prima, e la lettura
pessimistica impedisce al registro di autoassolversi.

**Le operazioni reali** si chiudono dagli ordini veri letti da Kraken, non da
una simulazione: l'operazione e' il giro che contiene il primo acquisto sulla
moneta nei tre giorni dopo la registrazione, e l'R e' il suo risultato diviso
il rischio pianificato. Il prezzo medio di uscita dice lo stato: obiettivo,
stop o "a mano" se in mezzo. Senza nessun acquisto in quei giorni
l'operazione e' "mai eseguita" e libera il suo posto. Quando Kraken non basta
(venduta in un'altra valuta, ledger non leggibile) si chiude a mano, anche
dalla pagina della scansione.

Il risultato e' espresso in R, dove 1 R e' la perdita reale allo stop,
commissioni comprese e con lo stop eseguito a mercato. Essendo gia' al netto
delle commissioni, la soglia da battere e' zero. Le statistiche sono separate
per regola e per carta/reale, e danno il margine d'errore al 95% sulla media:
il vantaggio e' misurato solo quando media meno margine resta sopra zero. Con
15 operazioni chiuse, un terzo all'obiettivo a 2 R, il margine e' di circa
0,8 R: `registrazioni-per-giudizio` e' il minimo per cominciare a guardare,
non per concludere.

### Lavoro giornaliero — ogni giorno alle 7:00 (Europe/Rome)

1. Risolve le operazioni aperte, la carta sulle candele e le reali dagli
   ordini su Kraken, cosi' il registro e' aggiornato.
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
curl -X POST -H "X-Journal: 1" http://localhost:8080/api/giornaliero
```

Ogni `POST` vuole l'header `X-Journal`: le pagine dell'app lo mettono, una
pagina di un altro sito aperta nello stesso browser non puo' farlo senza un
permesso che il server non da'. Senza, il server risponde `403`.

Le coppie gia' aperte non vengono registrate due volte.

## Configurazione del metodo

Tutte le soglie stanno nella sezione `scansione:` di `application.yml`:
watchlist, volume, trigger di rottura, RSI, ampiezza dello stop, multiplo dell'obiettivo, capitale,
rischio per operazione, time stop, numero massimo di posizioni reali e tipo di
ordine (`LIMITE` o `MERCATO`, con le tariffe scritte solo in `TipoOrdine`).

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

**Monete senza un acquisto.** Lo storico e' completo, quindi un acquisto prima
del periodo viene trovato. Restano senza costo di carico le monete arrivate da
un altro wallet, da ricompense o da airdrop: per quelle il risultato e' "non
calcolabile" e la coppia riporta la provenienza letta dal ledger. Un prelievo
verso un altro wallet invece non toglie la moneta dal carico, che resta come
posizione aperta.

**Vendite in un'altra valuta.** SOL comprato in USD e venduto in EUR esce dal
carico, e il suo costo si converte al cambio medio del giorno della vendita,
preso dalle candele giornaliere pubbliche di Kraken (EURUSD). Le candele
arrivano a 720 giorni fa: una vendita piu' vecchia, o fra due valute che Kraken
non quota, resta senza risultato.

**Rate limit.** Kraken usa un contatore a decadimento sugli endpoint privati.
`TradesHistory` e `Ledgers` sono fra i piu' costosi, e il journal li chiede
entrambi a ogni caricamento: non chiamare l'endpoint in loop stretto.
Per uso schedulato, una volta al giorno e' piu' che sufficiente.

**Kraken risponde HTTP 200 anche sugli errori applicativi**, che finiscono
nell'array `error`. `KrakenClient` lo controlla sempre: non fidarti del solo
status code se estendi il client.

## Cosa questo progetto non fa, deliberatamente

Non piazza ordini: la chiave Kraken ha solo permessi di lettura. Non prevede
prezzi. La scansione applica regole scritte e registra i setup su carta; decidere
se aprire davvero resta una scelta manuale.

I setup della scansione e il commento del journal vengono da codice
deterministico e testabile. Se in futuro vuoi
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

**Commissioni sul volume.** `commissioniPercentuale`, per valuta in
`totaliPerValuta` e per coppia in `feesToVolumePercent`, sostituisce il
rapporto fee/P&L come metrica principale sui costi: e' sempre calcolabile e non
dipende dal costo di carico.

**Valute miste.** Volumi, commissioni e risultato stanno solo in
`totaliPerValuta`, una riga per valuta. I vecchi totali di `stats` che
sommavano EUR e USD (`tradingVolumeQuote`, `totalFees`, `tradingFees`,
`fxFees`, `feesToVolumePercent`, `realizedPnlComplete`) sono stati tolti.
`mixedQuoteCurrencies` dice se le valute sono piu' d'una.

**Storico completo e acquisti dall'app.** Il costo di carico si ricostruiva
sui soli trade del periodo, quindi ogni posizione aperta prima della finestra
risultava venduta "senza acquisto". In piu' gli acquisti fatti dall'app non
passano da `TradesHistory`: su un anno di dati il risultato era calcolabile
su una sola vendita su 13. Ora lo storico si legge per intero, ledger compreso; le
posizioni si tengono per moneta e la parte abbinata di una coppia conserva il
suo risultato anche se un'altra parte non ce l'ha.

**Ordini, giorni e polvere.** Gli ordini si contano una volta anche se eseguiti
in piu' pezzi; i giorni attivi seguono il fuso del lavoro giornaliero
(`giornaliero.fuso`) invece di UTC; un residuo sotto
`journal.residuo-trascurabile` per cento non e' piu' una posizione aperta.
La polvere resta nel carico, perche' le monete sono ancora sul conto: il suo
costo esce dall'investito del giro chiuso e passa al giro successivo.

**Chiamate private una alla volta.** Due journal chiesti insieme (30 g e subito
1 anno) potevano arrivare a Kraken con i nonce in ordine inverso, e il secondo
veniva rifiutato con `EAPI:Invalid nonce`. Ora `KrakenClient` fa una chiamata
privata alla volta, e la pagina annulla la richiesta precedente quando cambi
periodo.

**Margine al 95%.** Il quantile della t di Student viene dalle tavole fino a 30
gradi di liberta': l'approssimazione usata prima, con 2 o 3 operazioni chiuse,
dava un margine fino al 44% piu' stretto del vero.

**Stop a mercato.** Il rischio di un'operazione pagava lo stop alla tariffa
dell'entrata, cioe' come ordine limite. Uno stop che scatta prende il prezzo
che c'e': ora stop e time stop escono a tariffa taker (`MatematicaOperazione.USCITA_FORZATA`).
La quantita' calcolata scende fra il 3 e il 6%, a seconda dell'ampiezza dello
stop, e una R vale la perdita vera. Le
operazioni chiuse prima del 23/11/2026 conservano il risultato in R calcolato
allora.

**Trigger verificato.** Il lavoro delle sette registrava come
`BREAKOUT_20_GIORNI` ogni coppia che superava i filtri, senza controllare la
rottura. Ora il controllo `BREAKOUT` fa parte della checklist, e il massimo a
30 giorni della resistenza esclude la candela di rottura.

**Tariffe da una fonte sola.** `GET /api/tariffe` espone `TipoOrdine`; il
calcolatore e le tacche del journal le leggono da li'.

**Commento senza AI.** `GeneratoreCommento` applica le regole scritte al posto
del modello: gratis, offline, ripetibile e testabile. Le soglie sono nella
sezione `commento:` di `application.yml`.
