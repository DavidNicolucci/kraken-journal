# Regole del commento del journal

**Stato: bozza 2, implementata il 23/11/2026** in `journal/GeneratoreCommento`,
con un test per ogni regola (`GeneratoreCommentoTest`). I punti del §10 sono
applicati con le proposte come valori di partenza: stanno in `application.yml`
e si cambiano lì. Da rivedere quando il journal gira sui dati veri.

La bozza 1 (23/11) è in `regole-commento-journal.bozza-1.md`. Si può cancellare
dopo l'approvazione di questa.

La versione con l'AI resta recuperabile dal tag `versione-con-ai` (commit `4cebb3b`).

---

## 0. Perché una bozza 2

La bozza 1 leggeva un calcolo che sbagliava proprio sui tuoi dati: su un anno,
13 vendite e 3 acquisti, e il risultato era calcolabile su una vendita sola.
Gli acquisti fatti dall'app Kraken non passano da `TradesHistory`, e il costo di
carico si ricostruiva solo dentro il periodo. Le regole commentavano quindi lo
smobilizzo di monete comprate altrove come se fosse il tuo modo di operare.

Il calcolo è stato corretto prima delle regole (§3). Questa bozza riscrive le
regole sui dati nuovi e aggiunge quelle che prima non erano possibili.

## 1. Scopo

Sostituire il commento scritto da Claude (`AnthropicClient`) con un commento
prodotto da regole fisse in Java.

Il report mantiene la stessa forma di oggi (`JournalReport`: `summary`,
`observedPatterns`, `questionsToAskYourself`, `dataCaveat`). In `index.html`
cambia solo il piè di pagina, che oggi dice che la lettura è "generata da un
modello".

## 2. Principi

1. **Solo numeri già calcolati.** Le regole usano le metriche del calcolo e le
   soglie della configurazione. Al massimo fanno somme, rapporti e confronti:
   nessuna stima nuova.
2. **Niente previsioni, niente consigli.** Nessuna frase su cosa farà il prezzo
   o su cosa comprare, vendere o tenere. Le regole parlano di quello che hai
   fatto e di quello su cui hai controllo: costi, metodo, dimensione, tempi.
3. **Prima i limiti, poi le conclusioni.** Se il campione non basta, il
   commento lo dice e non trae conclusioni sul risultato.
4. **Ogni importo porta la sua valuta.** EUR e USD non si sommano mai: due
   valute danno due importi.
5. **L'unità è la decisione, non l'esecuzione.** "Ordine" è una decisione di
   comprare o vendere, anche se Kraken l'ha eseguita in più pezzi. "Operazione"
   è un giro completo, dalla prima entrata su una moneta a quando ne resta solo
   polvere: la stessa unità del registro.
6. **Niente nomi tecnici nel testo.** Mai `pnlComplete`, `volumeTaker` e simili.
7. **Soglie in `application.yml`**, mai dentro un if, come per la scansione.
8. **Stessi dati, stesso testo.** Il commento è ripetibile e testabile.

## 3. Cosa fa già il calcolo (non serve una regola)

Corretto in `StoriaOperazioni` e `TradeStatisticsCalculator`, con un test per
ciascun punto:

| Prima | Adesso |
|---|---|
| Solo gli ordini di Kraken Pro | Anche le operazioni dell'app, lette dal ledger (righe `spend`/`receive`) |
| Costo di carico ricostruito solo nel periodo | Storico completo; il periodo decide solo cosa riportare |
| Una vendita senza acquisto annullava il risultato di tutta la coppia | La parte abbinata tiene il suo risultato, la parte scoperta è dichiarata |
| Posizioni per coppia: SOL/USD comprato e SOL/EUR venduto restavano due cose | Posizioni per moneta; la vendita in un'altra valuta chiude la posizione, col costo convertito al cambio del giorno della vendita |
| Esecuzioni contate come operazioni | Ordini e operazioni chiuse |
| Totali che sommavano EUR e USD | Totali per valuta, esatti |
| Giorni in UTC | Giorni nel fuso del lavoro giornaliero (`giornaliero.fuso`) |
| Tipo d'ordine dedotto dalla commissione media | Campo `maker` di ogni trade |
| Residui dopo una vendita contati come posizioni aperte | Sotto `journal.residuo-trascurabile` (1%) della quantità massima sono polvere (era la regola E5) |
| "Venduto senza acquisto: comprato prima, depositi, airdrop o ricompense" | La provenienza letta dal ledger, coppia per coppia |

Limiti che restano: una moneta arrivata da un altro wallet non ha un costo di
carico; un prelievo verso un altro wallet non toglie la moneta dalle posizioni;
lo spread dell'app non è visibile da nessuna parte.

## 4. Dati a disposizione

| Dato | Cosa dice |
|---|---|
| `ordini` | ogni ordine del periodo: coppia, acquisto o vendita, app o Kraken Pro, taker sì/no, controvalore, commissione, istante |
| `totaliPerValuta` | per ogni valuta: ordini, volume, commissioni e %, volume maker, volume taker e sue commissioni, volume e commissioni dell'app, risultato realizzato, coppie incomplete |
| `operazioniChiuse` | giri completi chiusi nel periodo: coppia, valuta, apertura, chiusura, giorni, investito, risultato |
| `posizioniAperte` | posizioni in carico adesso: coppia, quantità, costo di carico, aperta dal |
| `byPair` | per coppia: ordini, operazioni dall'app, volume, commissioni, risultato (anche parziale), quantità scoperta e sua provenienza, quantità venduta in un'altra valuta (convertita o senza cambio) |
| `ledgerLetto` | se la chiave ha potuto leggere il ledger |
| `storicoCompleto` | se Kraken ha restituito tutto lo storico o si è fermato al tetto di pagine |
| `tradingDays`, `fxConversions` | giorni con ordini e conversioni valutarie (il loro costo è nelle coppie di cambio di `byPair`) |
| **dalla configurazione esistente** | `scansione.watchlist`, `registrazioni-per-giudizio` (15), `giorni-time-stop` (15), `capitale` (203), `rischio-per-operazione` (1%), `ampiezza-stop-minima` (5%), `posizioni-massime` (2), `tipo-ordine`, `giornaliero.fuso` |
| **dal codice esistente** | `TipoOrdine` (0,40% / 0,80%), `MatematicaOperazione` (pareggio, rischio per unità), `PairResolver` (moneta di una coppia) |
| **dal registro** (sola lettura) | le operazioni reali registrate: coppia e data |

## 5. Come si compone il report

| Sezione nella pagina | Campo | Contenuto | Limite |
|---|---|---|---|
| **Lettura** | `summary` | C1 + C2 + C3, oppure A1/A2 | max 4 frasi: C1 ne usa al massimo 2 |
| **Cosa dicono i dati** | `observedPatterns` | una voce per ogni regola D/E che scatta, in ordine di priorità | max 5 voci |
| **Domande da porti** | `questionsToAskYourself` | una domanda per ogni regola che ne prevede una, stesso ordine | max 3 domande |
| **Limiti di questi dati** | `dataCaveat` | le frasi delle regole B che scattano, in ordine; B6 sempre in fondo | — |

**Ordine di priorità delle voci:** E8, D2, D1, E9, E10, E7, E1, E3, E2, D4,
E4, E6, D3. Prima le regole sul metodo, poi i costi, poi il resto. Se scattano
più di 5 regole, restano fuori le ultime.

## 6. Formato dei testi

- Numeri all'italiana: virgola decimale, punto per le migliaia.
- Importi con 2 decimali e il codice della valuta: `1,95 EUR`.
- Con due valute, due importi: `1,25 EUR e 0,68 USD`. Mai una somma.
- Percentuali di commissione con 2 decimali (`0,79%`); quote di volume o di
  conteggi senza decimali (`il 73% del volume`).
- Articolo e preposizione giusti davanti ai numeri: lo 0,79%, l'1,59%, l'8%,
  l'11%, il 73%; allo 0,79%, all'1,59%, al 73%.
- Segno esplicito sul risultato: `+1,95`, `−0,80`.
- Singolare e plurale corretti: 1 ordine / 2 ordini, 1 operazione / 2
  operazioni, 1 coppia / 2 coppie, 1 conversione / 2 conversioni.
- Elenchi: al massimo 6 voci, poi "e altre N".
- Date come "23 novembre 2026", nel fuso `giornaliero.fuso`.

---

## 7. Scenari e regole

### A. Casi che fermano il commento

#### A1 — Nessun ordine nel periodo
- **Quando:** nessun ordine e nessuna conversione nel periodo.
- **Lettura:** "Nessuna operazione negli ultimi {giorni} giorni."
- **Limiti:** "Nessun dato da analizzare nel periodo."
- **Altre sezioni:** si applicano solo E4 ed E7, perché una posizione aperta
  prima del periodo è aperta anche adesso.
- **Nota:** il controllo esiste già in `TradingJournalService.buildJournal`.

#### A2 — Solo conversioni valutarie
- **Quando:** zero ordini di trading, almeno una conversione.
- **Lettura:** "Negli ultimi {giorni} giorni ci sono solo {n} conversioni
  valutarie e nessuna operazione di trading."
- **Limiti:** "Le conversioni valutarie non sono operazioni di trading: non c'è
  un comportamento operativo da leggere."
- **Altre sezioni:** si applicano solo D4, E4 ed E7.

### B. Limiti dei dati → "Limiti di questi dati"

#### B0 — Ledger non letto (nuova)
- **Quando:** `ledgerLetto` è falso.
- **Testo:** "La chiave API non può leggere il ledger: mancano le operazioni
  fatte dall'app, e le vendite di monete comprate lì risultano senza acquisto.
  Aggiungi il permesso Query Ledger Entries."
- **Effetto:** sempre per prima. D2 non scatta.

#### B1 — Poche operazioni chiuse
- **Quando:** le operazioni chiuse nel periodo con un risultato calcolabile
  sono meno di `scansione.registrazioni-per-giudizio` (15).
- **Testo:** "Le operazioni chiuse con un risultato calcolabile sono {c}: sotto
  {soglia} il risultato dice poco sul metodo." Con una sola: "C'è una sola
  operazione chiusa con un risultato calcolabile: …".
- **Cambia rispetto alla bozza 1:** contava le righe di vendita. Una posizione
  chiusa in tre vendite valeva tre campioni.

#### B2 — Monete vendute senza un acquisto
- **Quando:** almeno una coppia ha una quantità venduta senza acquisto in
  tutto lo storico.
- **Testo:** "Hai venduto monete che non risultano comprate su {coppie}
  ({provenienza}) e su {coppie} ({provenienza}): per quella parte il risultato
  non è calcolabile e non è sommato a zero." Le coppie con la stessa
  provenienza stanno insieme: la provenienza si scrive una volta sola.
- **Provenienza:** quella del ledger ("deposito da un altro wallet", "ricompense,
  staking o accrediti", "airdrop o trasferimento", "conversione da un'altra
  moneta"). Se il ledger non dice niente: "provenienza non registrata".
- **Cambia:** non elenca più ipotesi, dice quella vera.

#### B3 — Vendite in un'altra valuta (nuova)
- **Quando:** almeno una coppia ha venduto una moneta comprata in un'altra
  valuta.
- **Testo, con il cambio:** "Su {coppie} hai venduto monete comprate in
  un'altra valuta: il costo è convertito al cambio medio del giorno della
  vendita, preso da Kraken."
- **Testo, senza il cambio** (vendita di oltre 720 giorni fa, o due valute che
  Kraken non quota fra loro): "Su {coppie} hai venduto monete comprate in
  un'altra valuta, in giorni senza un cambio: escono dalle posizioni, ma quella
  parte non ha un risultato."
- **Cambio:** il prezzo medio della candela giornaliera (UTC) del cambio su
  Kraken, lo stesso giorno per ricavo e costo. Il risultato della coppia è
  nella valuta della vendita, quello dell'operazione chiusa nella valuta del
  primo acquisto.
- **Nota:** sostituisce la vecchia B3 ("risultato non rappresentativo"), che non
  serve più: la parte abbinata ha sempre il suo risultato.

#### B4 — Valute diverse
- **Quando:** le operazioni di trading sono in più di una valuta.
- **Testo:** "Le operazioni sono in {EUR e USD}: ogni importo è dato nella sua
  valuta, e le due non si sommano."
- **Cambia:** prima diceva di leggere i totali "come ordine di grandezza".
  Adesso i totali sono esatti, per valuta.

#### B5 — Spread dell'app (nuova)
- **Quando:** c'è volume passato dall'app.
- **Testo:** "Sulle operazioni fatte dall'app il costo vero è più alto di
  quello mostrato: oltre alla commissione c'è lo spread, che sta dentro il
  prezzo e Kraken non riporta."

#### B7 — Storico troncato (dopo la revisione del 24/11)
- **Quando:** Kraken si è fermato al tetto di 40 pagine (2.000 voci) su trade o
  ledger (`stats.storicoCompleto = false`). Viene subito dopo B0.
- **Testo:** "Lo storico scaricato da Kraken si ferma alle voci più recenti: le
  vendite di monete comprate prima risultano senza costo di carico, anche se un
  acquisto c'è stato."
- **Perché:** prima il troncamento finiva solo nel log, e le vendite vecchie
  comparivano in B2 con una provenienza sbagliata.

#### B6 — Metodo di calcolo (sempre, in fondo)
- **Quando:** sempre, se c'è almeno un ordine di trading.
- **Testo:** "Il risultato è calcolato a costo medio ponderato su tutto lo
  storico del conto: serve a leggere come hai operato e non vale come calcolo
  fiscale."

### C. Lettura → "Lettura"

#### C1 — Attività del periodo (sempre)
- **Testo:** "Dal {inizio} al {fine} hai fatto {o} ordini di trading ({a}
  dall'app) su {k} coppie, in {g} giorni diversi." La parentesi c'è solo se
  ci sono ordini dall'app.
- **Se ci sono conversioni** aggiunge: "A parte, {f} conversioni valutarie."

#### C2 — Costo del periodo (sempre)
- **Testo, una valuta:** "Le commissioni di trading sono state {c} {valuta},
  pari allo {p}% del volume."
- **Due valute:** "Le commissioni di trading sono state {c1} EUR (lo {p1}% del
  volume) e {c2} USD (lo {p2}%)."

#### C3 — Risultato
Un solo caso, il primo che si verifica, in quest'ordine:

| Caso | Testo |
|---|---|
| nessuna vendita nel periodo | "Nel periodo hai solo comprato: non c'è ancora un risultato realizzato." |
| nessuna vendita con un costo di carico | "Il risultato non è calcolabile: nessuna vendita del periodo ha un acquisto corrispondente." |
| scatta B1 | "Il risultato realizzato è {±r EUR} [e {±r USD}], su {c} operazioni chiuse: non basta per dire come è andato il periodo." |
| altrimenti | "Il risultato realizzato è {±r EUR} [e {±r USD}]: {v} operazioni chiuse su {c} in guadagno." |

Il risultato realizzato comprende anche le vendite parziali di posizioni ancora
aperte. Niente aggettivi come "buono" o "deludente": il numero e il campione
bastano.

### D. Costi → "Cosa dicono i dati"

#### D1 — Ordini eseguiti a tariffa taker
- **Quando:** in una valuta, il volume di Kraken Pro eseguito a tariffa taker è
  almeno `commento.quota-taker-massima` (50%) del volume con il dato
  maker/taker.
- **Testo:** "Su Kraken Pro il {q}% del volume in {valuta} è stato eseguito
  subito, alla tariffa taker: {a} acquisti e {v} vendite. Rispetto alla tariffa
  maker ({maker}%) hai pagato {d} {valuta} in più, il {x}% delle commissioni."
  Una seconda valuta non ripete la spiegazione: "In USD l'87%, con 1 acquisto e
  4 vendite: 0,30 USD in più, il 45% delle commissioni."
- **Calcolo di {d}:** commissioni taker − volume taker × `TipoOrdine.LIMITE`.
- **Domanda, solo se ci sono acquisti taker:** "Nei {a} acquisti eseguiti
  subito, serviva entrare a quel prezzo o un ordine limite sarebbe bastato?"
- **Cambia:** prima si deduceva il tipo d'ordine dalla commissione media per
  coppia, e la soglia lasciava fuori le coppie con ordini misti. La domanda
  riguarda solo gli acquisti: un'uscita da stop è a mercato per natura.

#### D2 — Operazioni dall'app (nuova)
- **Quando:** c'è volume passato dall'app.
- **Testo:** "{n} ordini sono passati dall'app, per {v} {valuta}: commissione
  media dello {p}% più lo spread, contro lo {maker}% di un ordine limite su
  Kraken Pro."
- **Domanda:** "Le operazioni fatte dall'app si potevano fare da Kraken Pro con
  un ordine limite?"
- **Perché in alto nella priorità:** è il costo più grande che hai controllato
  e quello che il journal prima non vedeva.

#### D3 — Pareggio
- **Quando:** sempre, se c'è almeno un ordine di trading.
- **Testo:** "Con commissioni medie dello {p}% per lato, il prezzo deve salire
  almeno dell'{x}% solo per chiudere in pari."
- **Calcolo:** `MatematicaOperazione.pareggio`, con la commissione media della
  valuta della watchlist (EUR), oppure della valuta con più volume se in EUR
  non ci sono ordini.
- **Cambia:** prima era 2 × p. Il pareggio vero è un po' più alto, ed è la
  formula che usano già piano e registro.

#### D4 — Conversioni valutarie
- **Quando:** almeno una conversione nel periodo.
- **Testo:** "Hai fatto {n} conversioni valutarie, costate {c} {valuta} (lo {p}%
  del convertito): un costo che dipende dalla valuta delle coppie scelte, non
  dal mercato."
- **Domanda:** "Quelle conversioni erano necessarie, o la stessa operazione si
  poteva fare su una coppia in {valuta della watchlist}?"
- **Nota:** se la watchlist ha più valute, la domanda finisce dopo "necessarie?".

### E. Metodo e comportamento → "Cosa dicono i dati"

Le regole che confrontano con il metodo (E1, E3, E7, E8, E9, E10) valgono solo
da `commento.metodo-dal` in poi: per gli acquisti conta la data dell'ordine, per
le operazioni la data di apertura. Prima di quella data il metodo non c'era, e
giudicarci sopra direbbe solo che non c'era.

#### E8 — Acquisti senza registrazione (nuova, prima per priorità)
- **Quando:** un acquisto non ha nel registro un'operazione reale sulla stessa
  coppia (moneta e valuta), registrata fra `commento.giorni-registrazione` (3)
  giorni prima e il momento dell'acquisto, che non abbia già esaurito la sua
  quantità. Un'operazione eseguita in due ordini è coperta per intero; un
  secondo acquisto dopo averla riempita no (sotto il 2% della quantità
  registrata è riempita). Un acquisto più grande del registrato resta coperto:
  la dimensione la guarda E9. La coppia del registro può essere scritta
  XBTEUR, XBT/EUR o BTC/EUR.
- **Testo:** "{n} acquisti su {t} non hanno un'operazione reale nel registro:
  {elenco con data}."
- **Domanda:** "Dove erano scritti stop, obiettivo e motivo di quegli acquisti?"
- **Perché:** è il settimo controllo della guida, "l'ho scritto sul registro",
  che `Controllo` dichiara mancante. Ora i dati per verificarlo ci sono.

#### E9 — Dimensione oltre il metodo (nuova)
- **Quando:** una posizione nella valuta del capitale, aperta dal metodo in
  poi, supera il controvalore massimo che il metodo consente: con il rischio
  dell'1% di 203 EUR e lo stop più stretto ammesso (5%), circa 33 EUR. Lo stop
  esce a mercato (vedi `MatematicaOperazione.USCITA_FORZATA`): con la tariffa
  limite erano 35. Conta la posizione, non l'ordine: due acquisti da 25 EUR
  sono una posizione da 50. Per un giro chiuso è il costo più alto tenuto
  insieme (`costoMassimo`), per una posizione aperta il costo di oggi;
  commissioni comprese.
- **Calcolo:** `capitale × rischio-per-operazione / 100`, diviso
  `MatematicaOperazione.rischioPerUnita(1, 1 − ampiezza-stop-minima / 100,
  tipo-ordine)`. Se cambiano capitale o soglie, cambia da solo.
- **Testo:** "{n} posizioni superano i {max} EUR, la dimensione più alta che il
  metodo consente con il rischio dell'1% e lo stop più stretto ammesso (5%):
  {coppia} per {costo}, …"
- **Domanda:** "Con quale stop avevi calcolato la quantità di quelle posizioni?"
- **Nota:** sotto quel limite non si può dire niente: senza lo stop di ogni
  acquisto non si sa se il rischio era l'1%.

#### E10 — Più posizioni del previsto (nuova)
- **Quando:** nel periodo ci sono stati momenti con più di
  `scansione.posizioni-massime` (2) operazioni aperte insieme.
- **Calcolo:** sovrapposizione di apertura e chiusura delle operazioni chiuse e
  delle posizioni aperte, dal metodo in poi. Conta anche una posizione aperta
  prima del metodo e rimasta aperta dopo: occupa un posto lo stesso. Una moneta
  conta una volta, anche se è in carico in due valute.
- **Testo:** "Nel periodo hai tenuto fino a {n} posizioni aperte insieme: la
  guida ne prevede al massimo {max}."

#### E7 — Oltre il time stop (nuova)
- **Quando:** un'operazione chiusa nel periodo è durata più di
  `scansione.giorni-time-stop` (15) giorni, oppure una posizione aperta lo è da
  più di quei giorni. I giorni sono quelli toccati: 15 giorni e un'ora sono il
  sedicesimo, e contano come 16 anche nel testo. Troncando erano 15, e il
  controllo non scattava.
- **Testo:** "{n} operazioni sono durate più dei {15} giorni del time stop:
  {coppia} ({giorni} giorni), …" e per le aperte: "{coppia} è aperta da {giorni}
  giorni."
- **Domanda:** "Cosa ti ha tenuto dentro oltre il time stop?"

#### E1 — Acquisti fuori watchlist
- **Quando:** almeno un acquisto (non una vendita) su una moneta che non è
  nella watchlist. Il confronto è sulla moneta, non sulla valuta: SOL/USD conta
  come SOL, che è in watchlist come SOLEUR.
- **Testo:** "{n} acquisti su {t} sono su monete fuori dalla watchlist: {elenco}."
  Se lo sono tutti: "L'unico acquisto è su una moneta fuori dalla watchlist: …"
  oppure "Tutti e {t} gli acquisti sono su monete fuori dalla watchlist: …".
- **Domanda:** "Gli acquisti fuori watchlist sono passati dalla checklist della
  scansione, o sono nati fuori dal metodo?"
- **Cambia:** contava anche le vendite, cioè l'uscita da posizioni comprate
  prima. Sui tuoi dati 9 delle 10 operazioni "fuori watchlist" erano vendite.

#### E3 — Troppi ordini in un giorno
- **Quando:** in almeno un giorno ci sono più di
  `commento.ordini-al-giorno-massimi` (2) ordini.
- **Testo:** "In {k} giorni su {g} hai fatto più di {soglia} ordini: la guida
  prevede una revisione al giorno, a orario fisso."
- **Domanda:** "Quanti di quegli ordini erano previsti dalla revisione del
  mattino?"
- **Cambia:** la media per giorno attivo nascondeva il giorno con cinque
  ordini.

#### E2 — Concentrazione su una coppia
- **Quando:** in una valuta con almeno 2 coppie, una sola supera
  `commento.concentrazione-massima` (50%) del volume di quella valuta.
- **Testo:** "{coppia} da sola fa il {q}% del volume in {valuta}."
- **Domanda:** "Concentrare il volume su {coppia} è stata una scelta, o è
  successo?"

#### E4 — Posizioni ancora aperte
- **Quando:** c'è almeno una posizione aperta (la polvere è già esclusa dal
  calcolo).
- **Testo:** "Resta aperta una posizione su {coppia}, dal {data}, con un costo
  di carico di {costo} {valuta}: il suo risultato non è ancora realizzato."
  Con più posizioni, una frase sola con l'elenco.
- **Cambia:** prima il valore usava il prezzo medio di tutti gli acquisti,
  non il costo della parte rimasta.

#### E6 — Risultato per operazione chiusa
- **Quando:** almeno 2 operazioni chiuse nel periodo.
- **Testo:** "Operazioni chiuse: {coppia} {±r} {valuta} in {g} giorni, …",
  dalla più recente. Un'operazione con risultato non calcolabile è indicata
  come tale.
- **Cambia:** prima era il risultato per coppia, che mescolava giri diversi
  sulla stessa coppia.

---

## 8. Scenari valutati e scartati

| Scenario | Perché è fuori |
|---|---|
| Rischio effettivo di ogni acquisto | Serve lo stop di ogni operazione reale, che non c'è nei dati di Kraken. E9 dà solo un limite superiore; il resto lo misura il registro. |
| Giudicare se un'operazione è stata "buona" | Serve il piano di partenza, che sta nel registro e non nei trade. |
| Previsioni o suggerimenti su cosa fare | Vietati per principio (§2). |
| Orari degli ordini rispetto alla revisione delle 7 | Ora possibile (ogni ordine ha il suo istante), ma E3 copre già il caso più utile. Da valutare dopo. |
| Margine d'errore del risultato invece della soglia di 15 | Ha senso sui risultati in R del registro, non sugli importi del journal. Da proporre per le statistiche del registro. |

## 9. Nuove soglie in `application.yml`

```yaml
# Soglie del commento del journal. Come per la scansione: cambiarle e'
# cambiare il modo di leggere i dati, quindi qui e non dentro un if.
commento:
  metodo-dal: "2026-11-19"        # da qui acquisti e operazioni si confrontano con il metodo
  quota-taker-massima: 50         # % del volume Pro eseguito a tariffa taker
  concentrazione-massima: 50      # % del volume di una valuta su una sola coppia
  ordini-al-giorno-massimi: 2     # sopra, il giorno conta come "troppi ordini"
  giorni-registrazione: 3         # quanto prima dell'acquisto puo' stare la registrazione
```

Già in configurazione e riusati: `journal.residuo-trascurabile`,
`giornaliero.fuso` e, dalla sezione `scansione`, watchlist,
registrazioni-per-giudizio, giorni-time-stop, capitale,
rischio-per-operazione, ampiezza-stop-minima, posizioni-massime, tipo-ordine.
La sezione `anthropic` viene tolta.

Tolte rispetto alla bozza 1: `quota-coppie-incomplete-massima` (la vecchia B3
non serve più), `operazioni-al-giorno-massime` (sostituita da
`ordini-al-giorno-massimi`, che si applica al singolo giorno e non alla media) e
`residuo-trascurabile`, ora nel calcolo e in percentuale.

## 10. Decisioni applicate

Applicate il 23/11 con le proposte qui sotto, come valori di partenza. Si
cambiano in `application.yml` senza toccare il codice, tranne i punti 3 e 5.

1. **Data del metodo** (`metodo-dal`). *Proposta:* 19/11/2026, la data della
   watchlist in EUR. Con il 23/09/2026, data della prima watchlist, le regole
   del metodo giudicherebbero anche gli acquisti dei mesi scorsi.
2. **Ordini al giorno.** *Proposta:* più di 2. Una revisione al giorno può dare
   un'uscita e un'entrata; il terzo ordine non viene dalla revisione. La bozza 1
   usava 3, ma sulla media.
3. **Tariffe maker/taker.** Avevi scelto "in configurazione, aggiornate a mano".
   Adesso il journal legge la commissione davvero pagata e il campo `maker` di
   ogni trade, quindi non dipende più dalle tariffe. Servono solo come termine
   di confronto in D1, D2 e D3. *Proposta:* restano in `TipoOrdine`, dove le usa
   già la scansione. Spostarle in `application.yml` resta possibile, ma tocca
   anche il codice della scansione.
4. **Finestra della registrazione** (E8). *Proposta:* 3 giorni prima
   dell'acquisto.
5. **Numero di voci.** Con 13 regole possibili, 5 voci lasciano fuori spesso
   D4, E4, E6 e D3. *Proposta:* restano 5, la pagina mostra comunque tutti i
   numeri.
6. **Stablecoin.** Già deciso: si tratta a parte, fuori da questo lavoro.

## 11. Cosa è cambiato nel codice

- **Nuove:** `journal/GeneratoreCommento` (le regole), `journal/TestoItaliano`
  (numeri, articoli, plurali, date), `journal/ProprietaCommento` (sezione
  `commento`).
- **Modificata:** `TradingJournalService` chiama il generatore e legge le
  operazioni reali dal registro (`ServizioRegistro.reali()`, sola lettura).
- **Eliminate:** `ai/AnthropicClient`, `ai/dto/*`, `config/AnthropicProperties`,
  `journal/dto/AiView`, il `RestClient` di Anthropic in `HttpClientConfig`, la
  sezione `anthropic` di `application.yml`, `ANTHROPIC_API_KEY` nel README.
- **Pagina:** il piè di pagina di `index.html`.
- **Cambi (dopo la prova sui dati veri):** `journal/Cambi` tiene i cambi
  giornalieri; `TradingJournalService` scarica le candele del cambio con
  `MercatoClient` quando le operazioni sono in più valute;
  `TradeStatisticsCalculator` converte il costo di carico al cambio del giorno
  della vendita.
- **Test:** `GeneratoreCommentoTest` (un test per regola, con il nome della
  regola) e `TestoItalianoTest`.
- **Revisione del 24/11:** il costo della polvere passa al giro successivo;
  investito non calcolabile come il risultato quando manca un cambio; tolti
  da `JournalStats` i totali che sommavano EUR e USD; piano a zero con il
  prezzo sotto lo stop; quotazioni abbinate con `PairResolver`; RSI 50 su
  serie piatta e periodi della scansione controllati all'avvio; chiamate
  private a Kraken una alla volta; quantile della t dalle tavole fino a 30.
- **Revisione del 24/11, seconda parte:** registro con coppia normalizzata,
  quantità obbligatoria e tetto di posizioni sulle reali, rapporto e tariffa
  scritti dal registro; operazioni reali chiuse dagli ordini su Kraken
  (`ChiusuraReale`) o a mano; E8 per quantità e coppia; E9 per posizione; E10
  con le posizioni aperte prima del metodo; E7 sui giorni toccati; B7 per lo
  storico troncato; candele orarie per il giorno di registrazione della carta;
  controllo `PREZZO_ATTUALE` nella scansione; calcolatore con lo stop a mercato.
- **Niente commit:** il codice viene consegnato, il commit lo fai tu.

## 12. Esempio sui tuoi dati

Risposta di `/api/journal?days=365` del 24 novembre 2026, con il ledger letto
(chiave con *Query Ledger Entries*) e il cambio EUR/USD del giorno. Il report è
quello che esce, con accanto la regola che ha prodotto ogni parte.

**Lettura** (C1, C2, C3)

> Dal 24 novembre 2025 al 24 novembre 2026 hai fatto 48 ordini di trading (32
> dall'app) su 23 coppie, in 21 giorni diversi. A parte, 8 conversioni
> valutarie. Le commissioni di trading sono state 8,01 EUR (lo 0,95% del
> volume) e 0,68 USD (lo 0,72%). Il risultato realizzato è −20,60 EUR e −4,28
> USD: 6 operazioni chiuse su 19 in guadagno.

**Cosa dicono i dati** (5 voci, il massimo)

| Regola | Voce |
|---|---|
| E8 | L'acquisto di NEXO/EUR del 22 novembre 2026 non ha un'operazione reale nel registro. |
| D2 | 32 ordini sono passati dall'app: 701,52 EUR con una commissione media dell'1,00%, più lo spread che sta nel prezzo, contro lo 0,40% di un ordine limite su Kraken Pro. |
| D1 | Su Kraken Pro il 78% del volume in EUR è stato eseguito subito, alla tariffa taker: 2 acquisti e 4 vendite. Rispetto alla tariffa maker (0,40%) hai pagato 0,44 EUR in più, il 5% delle commissioni. In USD l'81%, con 1 acquisto e 3 vendite: 0,30 USD in più, il 45% delle commissioni. |
| E1 | L'unico acquisto è su una moneta fuori dalla watchlist: NEXO/EUR. |
| D4 | Hai fatto 8 conversioni valutarie, costate 1,65 EUR e 0,54 USD: un costo che dipende dalla valuta delle coppie scelte, non dal mercato. |

Restano fuori per il limite di 5 voci E4 (la posizione aperta su NEXO/EUR),
E6 e D3. E1 conta solo gli acquisti dal 19 novembre 2026 (`metodo-dal`): per
questo "l'unico".

**Domande da porti** (E8, D2, D1)

- Dove erano scritti stop, obiettivo e motivo di quell'acquisto?
- Le operazioni fatte dall'app si potevano fare da Kraken Pro con un ordine limite?
- Nei 3 acquisti eseguiti subito, serviva entrare a quel prezzo o un ordine limite sarebbe bastato?

**Limiti di questi dati** (B2, B3, B4, B5, B6)

> Hai venduto monete che non risultano comprate su XBT/EUR, PEPE/EUR, NEAR/EUR,
> TRX/EUR, XDG/EUR e USDG/EUR (ricompense, staking o accrediti): per quella
> parte il risultato non è calcolabile e non è sommato a zero. Su LIGHTER/USD,
> SOL/EUR, ONE/USD e AMI/USD hai venduto monete comprate in un'altra valuta: il
> costo è convertito al cambio medio del giorno della vendita, preso da Kraken.
> Le operazioni sono in EUR e USD: ogni importo è dato nella sua valuta, e le
> valute non si sommano fra loro. Sulle operazioni fatte dall'app il costo vero
> è più alto di quello mostrato: oltre alla commissione c'è lo spread, che sta
> dentro il prezzo e Kraken non riporta. Il risultato è calcolato a costo medio
> ponderato su tutto lo storico del conto: serve a leggere come hai operato e
> non vale come calcolo fiscale.

**Cosa è cambiato dal 23/11**

Col cambio del giorno le vendite di LIGHTER, ONE e AMI (comprate in EUR
dall'app, vendute in USD su Kraken Pro) e di SOL (al contrario) hanno un
risultato. Le operazioni chiuse calcolabili passano da 14 a 19, le coppie con
risultato parziale da 10 a 6, e B1 ("sotto 15 il risultato dice poco") non
scatta più. Il risultato in EUR passa da −19,92 a −20,60 per la vendita di SOL;
quello in USD, −4,28, prima non c'era.

**Cosa resta fuori dal calcolo**

- **Monete arrivate da ricompense e staking** (B2): vendute senza un costo di
  carico, restano fuori dal risultato.
- **Conversione dei saldi piccoli.** Un'operazione dell'app (riferimento
  TS6V2XV-SA33Z-KBQJE4) ha venduto insieme 0,25 USDG e due residui di SOL ed
  ETH per 0,22 EUR. Non è una coppia spesa/incasso, quindi è ignorata: il log
  la riporta con le sue righe. Il valore è sotto i 25 centesimi e nessuna
  posizione resta aperta per colpa sua.
