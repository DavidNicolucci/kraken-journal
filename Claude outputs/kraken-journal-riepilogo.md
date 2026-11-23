# Kraken Journal — riepilogo della sessione

Periodo: 19 – 23 novembre 2026
Progetto: `C:\Users\DavidNicolucci\Desktop\kraken-journal`
Repository: https://github.com/DavidNicolucci/kraken-journal (pubblico)

---

## 1. Situazione attuale in breve

| Cosa | Stato |
|---|---|
| Claude Code sul nuovo PC | installato (v2.1.328) e funzionante nel terminale dell'IDE |
| Variabili `KRAKEN_API_KEY` / `KRAKEN_API_SECRET` | impostate come variabili utente di Windows e verificate |
| Watchlist della scansione | passata da 12 coppie in USD a 10 coppie in EUR, pushata (commit `4cebb3b`) |
| Repository condivisibile con un collega | sì: pubblico, senza chiavi né password, nemmeno nella cronologia |
| Commento AI del journal | deciso di sostituirlo con regole fisse (opzione A) |
| Regole del nuovo commento | scritte in `docs/regole-commento-journal.md`, **da approvare** |
| Backup della versione con l'AI | **da fare**: tag `versione-con-ai` non ancora creato |
| Codice dell'opzione A | **non ancora scritto**, si parte dopo l'approvazione delle regole |

---

## 2. Cosa ci siamo detti e cosa abbiamo fatto

### 2.1 Installazione di Claude Code sul nuovo PC (19/11)

- **Problema:** nel terminale dell'IDE `claude` non veniva riconosciuto.
- **Installazione** da una PowerShell esterna:
  ```powershell
  irm https://claude.ai/install.ps1 | iex
  ```
  Installato in `C:\Users\DavidNicolucci\.local\bin\claude.exe`.
- **La cartella non era nel PATH.** Aggiunta come variabile utente:
  ```powershell
  [Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User") + ";C:\Users\DavidNicolucci\.local\bin", "User")
  ```
- **Errore "Cannot paste clipboard media"** nel terminale dell'IDE: negli appunti
  c'era un'immagine (lo screenshot). Soluzione: copiare il testo con il
  pulsante di copia e usare una PowerShell esterna.
- **Da ricordare:** dopo aver cambiato il PATH o le variabili d'ambiente, l'IDE
  va chiuso del tutto (File → Exit, e anche Toolbox se lo usi). Legge le
  variabili solo all'avvio.

### 2.2 Variabili d'ambiente di Kraken (19/11)

- **Errore all'avvio:** Base64 non valido. La variabile `KRAKEN_API_SECRET` non
  esisteva, quindi Spring passava la stringa letterale `${KRAKEN_API_SECRET}`
  e il `$` non è un carattere Base64.
- **Dove stavano sul vecchio PC:** non in Windows, ma nella Run Configuration
  dell'IDE (campo *Environment variables*). Con "Store as project file" spento,
  quella configurazione resta nei file locali dell'IDE e non finisce nel
  repository. Per i secret è giusto così.
- **Come le abbiamo impostate sul nuovo PC** (il valore si incolla quando
  richiesto e non finisce nella cronologia di PowerShell):
  ```powershell
  [Environment]::SetEnvironmentVariable('KRAKEN_API_KEY', (Read-Host 'API Key'), 'User')
  [Environment]::SetEnvironmentVariable('KRAKEN_API_SECRET', (Read-Host 'Private Key'), 'User')
  ```
- **Problema trovato:** il secret era di 90 caratteri invece di 88 e finiva con
  `\=\=`. Nel campo Environment variables l'IDE mette un `\` davanti a ogni `=`,
  e copiando dal campo i backslash vengono via insieme al valore. Li abbiamo
  tolti:
  ```powershell
  $env:KRAKEN_API_SECRET = $env:KRAKEN_API_SECRET.Replace('\',''); [Environment]::SetEnvironmentVariable('KRAKEN_API_SECRET', $env:KRAKEN_API_SECRET, 'User')
  ```
- **Verifica** (non stampa i valori). Risultato atteso: `88`, `True`, `False`.
  ```powershell
  $env:KRAKEN_API_SECRET.Length; $env:KRAKEN_API_SECRET -match '^[A-Za-z0-9+/]+={0,2}$'; $env:KRAKEN_API_KEY.Contains('\')
  ```

### 2.3 Watchlist in EUR (19/11, pushata il 23/11)

- In `application.yml` le 12 coppie in USD sono state sostituite da 10 coppie in
  EUR: XBTEUR, ETHEUR, SOLEUR, XRPEUR, ZECEUR, ADAEUR, NEAREUR, AVAXEUR,
  LINKEUR, HYPEEUR.
- **Escluse** USDC/EUR ed EURC/EUR: sono stablecoin, non hanno trend e non
  potrebbero mai avere uno stop tra il 5% e il 10%.
- Aggiornati il commento della watchlist (congelata al 19/11/2026) e la data nel
  README. Il codice non è cambiato: la scansione legge la lista dalla
  configurazione.
- **Avvertenze emerse:**
  - la lista nasce dalla classifica per volume di un giorno, cioè il criterio
    contro cui mette in guardia il commento della watchlist. Va bene cambiarla
    una volta e poi lasciarla ferma. I risultati sulle coppie in USD e quelli
    sulle coppie in EUR non vanno sommati;
  - HYPE (1,2 milioni di euro), LINK (1,43) e AVAX (1,49) stanno appena sopra la
    soglia di liquidità di 1 milione;
  - per al massimo 15 giorni (il time stop) un'analisi su carta aperta su
    XBTUSD non impedisce di aprirne una su XBTEUR.
- Commit e push fatti da te: `4cebb3b` ("added UER").

### 2.4 Errore all'avvio "Could not find class KrakenProperties" (23/11)

- **Causa:** un'interferenza tra due compilazioni, non un problema del codice.
  L'app è partita alle 08:01:50 e i `.class` in `target/classes` sono stati
  riscritti tra le 08:01:52 e le 08:01:55. Mentre l'app partiva, il language
  server Java (jdtls) stava ricompilando la stessa cartella che usa l'IDE.
- **Soluzione:** rilanciare l'app, cosa che ha funzionato. Se si ripete dopo
  l'apertura dell'IDE, aspettare che finisca la compilazione in background,
  oppure disattivare il plugin del language server Java se l'IDE compila già
  da solo.

### 2.5 Condivisione del repository con un collega (23/11)

- **Può scaricarlo:** il repository è pubblico.
  ```
  git clone https://github.com/DavidNicolucci/kraken-journal.git
  ```
- **Controllo dei dati sensibili** su tutti i 9 commit della cronologia:
  - nessuna chiave, secret o password;
  - le stringhe lunghe nei test (`KrakenSignerTest`, `KrakenClientTest`) sono la
    chiave di esempio pubblica della documentazione di Kraken e la firma
    calcolata con quella;
  - `dati/`, `.idea/` e `.env` sono esclusi da `.gitignore` e non sono mai
    entrati nella cronologia;
  - sono visibili solo due dati personali minori: il capitale in
    `application.yml` e la tua email come autore dei commit.
- **Al collega servono le sue chiavi Kraken**, con soli permessi di lettura.
  Le tue non vanno date.
- `.openidea/` (impostazioni dell'IDE) non va caricata: conviene aggiungerla al
  `.gitignore`.
- Il registro `dati/registro.mv.db` esiste solo sul tuo PC: va salvato a parte
  se vuoi conservarne lo storico.

### 2.6 A cosa serve la chiave Anthropic (23/11)

- Serve **solo** per il commento in italiano della pagina del journal.
- Flusso: storico dei trade da Kraken → metriche calcolate in Java → metriche
  aggregate mandate a Claude → commento (riassunto, schemi, domande, limiti).
- Senza chiave l'app parte lo stesso: le metriche si vedono, e al posto del
  commento compare "Analisi non disponibile".
- Si paga a consumo (chiave della Console Anthropic, separata dall'abbonamento),
  con una chiamata a ogni caricamento della pagina. Ad Anthropic arrivano i dati
  aggregati delle operazioni.

### 2.7 Togliere l'AI dal journal (23/11)

- **Opzione A, scelta:** commento generato da regole fisse in Java. È gratis,
  funziona offline, è ripetibile e testabile, non inventa numeri e dal PC non
  esce niente.
- **Opzione B, scartata:** AI locale con Ollama sul Raspberry (Qwen 3.5 2B).
  Il modello è troppo piccolo per questo lavoro: rischia di inventare numeri,
  regge male il vincolo del JSON ed è lento.
- **Metodo concordato:**
  1. backup;
  2. analisi degli scenari possibili;
  3. una regola per ogni scenario, approvata da te;
  4. solo dopo, il codice.
- **Backup:** tag Git sul commit `4cebb3b`. Va lanciato da te:
  ```
  git tag versione-con-ai 4cebb3b
  git push origin versione-con-ai
  ```
- **Regole:** scritte in `docs/regole-commento-journal.md`. Sono 19 regole in 5
  gruppi (A casi che fermano il commento, B limiti dei dati, C lettura, D costi,
  E comportamento). Il documento contiene anche le soglie da aggiungere, i punti
  aperti e un esempio di commento sui tuoi dati reali.
- **Dati reali usati:** `dati/journal-esempio.json`, l'ultimo anno, escluso da
  Git. Cosa ne è uscito:
  - il 44% delle commissioni di trading (0,74 su 1,67) è stato pagato in più
    rispetto alla tariffa maker;
  - il 63% del volume è su coppie fuori dalla watchlist attuale;
  - il risultato si può calcolare su una sola vendita, quindi per molto tempo il
    journal dirà che il campione non basta.
- **Correzione fatta dopo:** le tariffe maker/taker (0,40% / 0,80%) esistono già
  nell'enum `TipoOrdine`. Il commento le riusa da lì invece di duplicarle in
  configurazione.
- **Le tue decisioni sui punti aperti:**
  - stablecoin come conversioni: si decide a parte, fuori da questo lavoro;
  - tariffe: hai scelto "in configurazione, aggiornate a mano". Visto che
    esistono già in `TipoOrdine`, resta da confermare se lasciarle nell'enum o
    spostarle da lì in `application.yml`.

### 2.8 Altro

- **RSI sul grafico di Kraken:** Indicatori → cerca "RSI" → Relative Strength
  Index, lunghezza 14, sorgente chiusura. Sono gli stessi parametri di Kraken
  Journal (RSI di Wilder). Il controllo MOMENTO vuole un valore tra 50 e 70.

---

## 3. Come è strutturato il codice

### 3.1 Stack

- **Java 21**, **Spring Boot 3.3.4** (web, validation, data-jpa), **Maven**.
- **H2** su file (`./dati/registro`) per il registro delle operazioni.
- Nessuna dipendenza da librerie di trading: le chiamate a Kraken sono fatte a
  mano con `RestClient`.
- Ascolta solo su `127.0.0.1:8080`, perché non c'è autenticazione.
- Package radice: `dev.kraken.journal`.

### 3.2 Mappa dei package

| Package | Responsabilità | Classi principali |
|---|---|---|
| *(radice)* | avvio | `JournalApplication` (`@SpringBootApplication`, `@EnableScheduling`) |
| `config` | configurazione e client HTTP | `HttpClientConfig` (bean `RestClient` per Kraken e per Anthropic; registra le proprietà), `KrakenProperties`, `AnthropicProperties` |
| `kraken` | API **private** di Kraken, sola lettura | `KrakenClient` (storico dei trade con paginazione), `KrakenSigner` (firma HMAC-SHA512, nonce crescente), `PairResolver` (nomi Kraken X/Z, base/quotazione, riconoscimento delle conversioni valutarie), `Ritentativi` (ritenta gli errori temporanei con attesa crescente), `KrakenApiException`, dto `KrakenEnvelope`, `KrakenTrade`, `TradesHistoryResult` |
| `mercato` | API **pubbliche** di Kraken | `MercatoClient` (candele e quotazioni a 24 ore, cache giornaliera, limite di chiamate contemporanee), enum `Intervallo`, dto `Candela`, `Quotazione` |
| `journal` | storico reale delle operazioni | `JournalController`, `TradingJournalService` (trade → metriche → commento), `TradeStatisticsCalculator` (metriche deterministiche), dto `JournalStats`, `PairStats`, `JournalReport`, `JournalResponse`, `AiView` |
| `ai` | commento con Claude (**da eliminare con l'opzione A**) | `AnthropicClient`, dto `AnthropicRequest`, `AnthropicResponse` |
| `scansione` | checklist sulla watchlist | `ScansioneController`, `ServizioScansione`, `CalcolatoreIndicatori` (RSI di Wilder, ATR, media mobile…), `ValutatoreSetup` (entrata, stop, obiettivo, quantità), `MatematicaOperazione` (rischio e commissioni, fonte unica), `ProprietaScansione` (soglie), enum `Controllo`, `Esito`, `TipoOrdine`, `Verso`, dto `Candidato`, `EsitoControllo`, `Indicatori`, `Piano`, `RispostaScansione` |
| `calcolatore` | calcolatore "Prima di entrare" | `CalcolatoreController`, `ServizioCalcolatore` (usa `MatematicaOperazione`), dto `RichiestaValutazione`, `EsitoValutazione` |
| `registro` | analisi su carta e operazioni reali | `RegistroController`, `ServizioRegistro`, `RisolutoreOperazione` (decide come è finita un'operazione: funzione pura, in caso di dubbio assume lo stop), `OperazioneRegistrata` (`@Entity`: stop, obiettivo e motivo obbligatori, flag `reale`), `RegistroRepository` (JPA), enum `StatoOperazione`, `Trigger`, dto |
| `giornaliero` | lavoro automatico delle 7 | `LavoroGiornaliero` (`@Scheduled`), `GiornalieroController` (avvio a mano) |

### 3.3 Endpoint e pagine

| Endpoint | Cosa fa | Pagina |
|---|---|---|
| `GET /api/journal?days=30` | metriche dello storico reale e commento | `index.html` |
| `GET /api/scansione?soloIdonee=false` | checklist su tutta la watchlist | `scansione.html` |
| `POST /api/valutazione` | calcolatore "Prima di entrare" | `calcolatore.html` |
| `POST /api/registro` | registra un'operazione (su carta o reale) | — |
| `GET /api/registro` | elenco delle operazioni registrate | — |
| `GET /api/registro/statistiche` | statistiche del registro | — |
| `POST /api/registro/risoluzione` | chiude le operazioni arrivate a stop, obiettivo o time stop | — |
| `POST /api/giornaliero` | lancia a mano il lavoro delle 7 | — |

Le pagine stanno in `src/main/resources/static/` e condividono `stile.css`.

### 3.4 I flussi principali

**Journal**
```
index.html → GET /api/journal
  → TradingJournalService.buildJournal
      → KrakenClient.fetchTrades         (API privata, firmata, paginata)
      → TradeStatisticsCalculator.compute (metriche per coppia e totali)
      → AnthropicClient.complete          (commento; con l'opzione A → GeneratoreCommento)
  → JournalResponse { stats, report }
```
Scelte del calcolo da conoscere:
- risultato a costo medio ponderato;
- una vendita senza acquisto corrispondente rende il risultato "non
  calcolabile" (null), mai zero;
- le conversioni valutarie (EUR/USD) sono contate a parte.

**Scansione**
```
scansione.html → GET /api/scansione
  → ServizioScansione.scansiona
      → MercatoClient.quotazioni      (tutte insieme; se Kraken rifiuta, una per una)
      → per ogni coppia, in parallelo su thread virtuali:
          MercatoClient.candeleChiuse → CalcolatoreIndicatori → ValutatoreSetup
          → 6 controlli: LIQUIDITA, TREND, MOMENTO, DISTANZA_DALLA_MEDIA,
                         AMPIEZZA_STOP, RESISTENZA_INTERMEDIA
  → RispostaScansione (esaminate / in watchlist / candidati ordinati)
```

**Lavoro delle 7** (cron `0 0 7 * * *`, Europe/Rome)
```
LavoroGiornaliero.esegui
  → ServizioRegistro.risolviAperte      (chiude le analisi su carta finite)
  → ServizioScansione.scansiona         (solo le coppie che superano tutti i controlli)
  → ordina per rotazione                (prima le coppie registrate meno volte)
  → ServizioRegistro.registraSuCarta    (salta le coppie con un'operazione già aperta)
```
Al massimo 2 posizioni reali aperte insieme. Le analisi su carta non hanno tetto.

**Calcolatore**
```
calcolatore.html → POST /api/valutazione → ServizioCalcolatore → MatematicaOperazione
```

### 3.5 Configurazione (`application.yml`)

| Sezione | Contenuto |
|---|---|
| `server` | `address: 127.0.0.1` |
| `spring.datasource` | H2 su file `./dati/registro` |
| `kraken` | `base-url`, `api-key: ${KRAKEN_API_KEY}`, `api-secret: ${KRAKEN_API_SECRET}` |
| `giornaliero` | `cron` (7:00) e `fuso` (Europe/Rome) |
| `scansione` | watchlist (10 coppie EUR) e soglie del metodo: volume minimo, RSI 50–70, distanza dalla media, stop 5–10%, multiplo dell'obiettivo 2R, capitale, rischio 1%, time stop 15 giorni, 15 registrazioni per giudicare, 2 posizioni massime, tipo d'ordine |
| `anthropic` | chiave, modello e token (**da togliere con l'opzione A**) |
| `logging` | livello INFO per `dev.kraken.journal` |

### 3.6 Variabili d'ambiente

| Variabile | Obbligatoria | Uso |
|---|---|---|
| `KRAKEN_API_KEY` | sì | firma delle chiamate private (chiave con soli permessi di lettura) |
| `KRAKEN_API_SECRET` | sì | firma delle chiamate private (88 caratteri Base64) |
| `ANTHROPIC_API_KEY` | no | solo il commento del journal; sparisce con l'opzione A |

### 3.7 Cosa resta sul PC e non va su Git

- `dati/`: il database H2 del registro e `journal-esempio.json`.
- `.idea/`: la Run Configuration, che può contenere chiavi.
- `.openidea/`: impostazioni dell'IDE (da aggiungere al `.gitignore`).
- `target/`: i compilati.

### 3.8 Test (`src/test/java/dev/kraken/journal`)

| Package | Test |
|---|---|
| `calcolatore` | `ServizioCalcolatoreTest` |
| `giornaliero` | `PianificazioneTest` |
| `journal` | `TradeStatisticsCalculatorTest`, `TradingJournalServiceTest` |
| `kraken` | `KrakenClientTest`, `KrakenSignerTest`, `PairResolverTest`, `RitentativiTest` |
| `mercato` | `MercatoClientTest` |
| `registro` | `RisolutoreOperazioneTest` |
| `scansione` | `CalcolatoreIndicatoriTest`, `MatematicaOperazioneTest`, `ServizioScansioneTest`, `ValutatoreSetupTest` |

### 3.9 Principi che il codice segue

- **Le soglie del metodo stanno in configurazione**, non dentro un if.
- **La matematica di rischio e commissioni ha una fonte sola**
  (`MatematicaOperazione`): il piano e la misura parlano la stessa lingua.
- **Le parti di calcolo sono funzioni pure** (`CalcolatoreIndicatori`,
  `RisolutoreOperazione`): si testano senza Spring.
- **Lettura pessimistica:** se nella stessa candela il prezzo tocca stop e
  obiettivo, vince lo stop.
- **Un dato mancante non diventa mai zero:** "non calcolabile" resta tale.
- **Kraken in sola lettura:** nessun metodo crea o annulla ordini, e la chiave
  ha solo permessi di lettura.
- **Enum invece di costanti sparse** per i valori ricorrenti (`TipoOrdine`,
  `Intervallo`, `Controllo`…).

---

## 4. Cosa cambierà con l'opzione A

Il dettaglio è in `docs/regole-commento-journal.md` (§10). In sintesi:

- **Nuove classi:** `journal/GeneratoreCommento` (applica le regole e restituisce
  un `JournalReport`) e `journal/ProprietaCommento` (le soglie della nuova
  sezione `commento`).
- **Modificata:** `TradingJournalService`, che chiama il generatore invece di
  Claude.
- **Eliminate:** tutto il package `ai`, `AnthropicProperties`, `AiView`, il bean
  `anthropicRestClient`, la sezione `anthropic` e `ANTHROPIC_API_KEY` dal README.
- **Riusati senza duplicarli:** la watchlist, `registrazioni-per-giudizio`, le
  tariffe di `TipoOrdine`, `PairResolver` e il formattatore delle date.
- **Invariati:** `index.html` (il report ha la stessa forma) e tutto il resto
  dell'app.
- **Niente commit:** il codice viene consegnato, il commit lo fai tu.

---

## 5. Cose da fare

- [ ] Creare e pushare il tag `versione-con-ai` (backup).
- [ ] Rileggere `docs/regole-commento-journal.md` e approvare le regole.
- [ ] Confermare dove stanno le tariffe maker/taker: in `TipoOrdine` o in `application.yml`.
- [x] Stablecoin: si decide a parte.
- [ ] Dopo l'approvazione: implementare l'opzione A, con un test per ogni regola.
- [ ] Fare commit di `docs/` quando vuoi.
- [ ] Aggiungere `.openidea/` al `.gitignore`.
- [ ] Controllare che la scansione mostri "10 coppie esaminate su 10 in watchlist".
- [ ] Facoltativo: disattivare il plugin del language server Java se l'errore all'avvio si ripete.
- [ ] Più avanti, a parte: trattare le stablecoin come conversioni (modifica a `PairResolver`).
