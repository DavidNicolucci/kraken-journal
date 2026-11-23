# Regole del commento del journal

**Stato: bozza da approvare.** Nessuna riga di codice è stata ancora scritta.
Prima si approvano scenari e regole, poi si passa al codice.

La versione con l'AI resta recuperabile dal tag `versione-con-ai` (commit `4cebb3b`).

---

## 1. Scopo

Sostituire il commento scritto da Claude (`AnthropicClient`) con un commento
prodotto da regole fisse in Java.

Il report mantiene la stessa forma di oggi (`JournalReport`: `summary`,
`observedPatterns`, `questionsToAskYourself`, `dataCaveat`), quindi
`index.html` non cambia.

## 2. Principi

1. **Solo numeri già calcolati.** Le regole usano le metriche di
   `TradeStatisticsCalculator`. Al massimo fanno somme, rapporti e confronti
   con una soglia: nessun numero nuovo.
2. **Niente previsioni, niente consigli.** Nessuna frase su cosa farà il prezzo
   o su cosa comprare, vendere o tenere. Le regole parlano di quello che hai
   fatto e di quello su cui hai controllo: costi, frequenza, scelta delle coppie.
3. **Prima i limiti, poi le conclusioni.** Se il campione non basta, il
   commento lo dice e non trae conclusioni sul risultato.
4. **Niente nomi tecnici nel testo.** Mai `pnlComplete`, `unmatchedSellVolume`
   e simili.
5. **Soglie in `application.yml`**, mai dentro un if, come per la scansione.
6. **Stessi dati, stesso testo.** Il commento è ripetibile e testabile.

## 3. Dati a disposizione

| Dato | Cosa dice |
|---|---|
| operazioni di trading, conversioni valutarie | quante operazioni, con le conversioni contate a parte |
| giorni con operazioni, operazioni per giorno attivo | frequenza |
| volume, commissioni di trading, commissioni sulle conversioni | costi |
| commissioni in % sul volume | costo medio del trading |
| risultato realizzato complessivo | somma delle sole coppie con risultato calcolabile; vuoto se nessuna lo è |
| coppie con risultato non calcolabile | quante hanno vendite senza acquisto corrispondente |
| valute miste, elenco valute | se i totali sommano EUR e USD |
| **per coppia:** nome, valuta, operazioni, acquisti, vendite, volume, commissioni, commissioni in %, risultato, quantità venduta senza acquisto, prezzo medio di acquisto e di vendita, posizione rimasta | il dettaglio |
| **dalla configurazione esistente:** `scansione.watchlist`, `scansione.registrazioni-per-giudizio` (15) | riusati, non duplicati |

Nei trade di Kraken ci sono anche il **tipo d'ordine** e il **margine**, ma oggi
non vengono contati. Vedi i punti aperti (§9).

## 4. Come si compone il report

| Sezione nella pagina | Campo | Contenuto | Limite |
|---|---|---|---|
| **Lettura** | `summary` | C1 + C2 + C3, oppure il testo di A1/A2 | max 4 frasi |
| **Cosa dicono i dati** | `observedPatterns` | una voce per ogni regola D/E che scatta, in ordine di priorità | max 5 voci |
| **Domande da porti** | `questionsToAskYourself` | una domanda per ogni regola che ne prevede una, stesso ordine | max 3 domande |
| **Limiti di questi dati** | `dataCaveat` | le frasi delle regole B che scattano, unite; B5 sempre in fondo | — |

**Ordine di priorità delle voci:** D1, E1, D3, E3, E2, E4, E6, D2.
Se scattano più di 5 regole, restano fuori le ultime.

## 5. Formato dei testi

- Numeri all'italiana: virgola decimale, punto per le migliaia.
- Importi con 2 decimali.
- Commissioni in % con 2 decimali, come le calcola già il codice (`0,72%`).
  Quote di volume o di commissioni senza decimali (`il 63% del volume`).
- Articolo e preposizione giusti davanti ai numeri: lo 0,72%, l'1,44%, l'8%,
  l'11%, il 63%; allo 0,72%, all'1,44%, al 63%.
- **Valuta:** se tutte le operazioni sono nella stessa valuta, i totali portano
  il codice (`1,94 EUR`). Se le valute sono miste, i totali sono senza codice e
  scatta B4. Gli importi di una singola coppia portano sempre la valuta della
  coppia.
- Segno esplicito sul risultato: `+1,94`, `−0,50`.
- Singolare e plurale corretti: 1 coppia / 2 coppie, 1 vendita / 2 vendite,
  1 conversione / 2 conversioni.
- Elenchi di coppie: al massimo 6 nomi, poi "e altre N".
- Date come oggi: "23 novembre 2025" (il formattatore `DAY` esiste già in
  `TradingJournalService`).

---

## 6. Scenari e regole

### A. Casi che fermano il commento

#### A1 — Nessuna operazione
- **Quando:** nel periodo non c'è nessun trade.
- **Lettura:** "Nessuna operazione negli ultimi {giorni} giorni."
- **Limiti:** "Nessun dato da analizzare nel periodo."
- **Altre sezioni:** vuote.
- **Nota:** esiste già in `TradingJournalService.buildJournal`. Si riusa così com'è.

#### A2 — Solo conversioni valutarie
- **Quando:** zero operazioni di trading, almeno una conversione.
- **Lettura:** "Negli ultimi {giorni} giorni ci sono solo {n} conversioni
  valutarie e nessuna operazione di trading."
- **Limiti:** "Le conversioni valutarie non sono operazioni di trading: non c'è
  un comportamento operativo da leggere."
- **Altre sezioni:** si applica solo D3.

### B. Limiti dei dati → "Limiti di questi dati"

#### B1 — Campione troppo piccolo
- **Quando:** le vendite con un risultato calcolabile sono meno di
  `scansione.registrazioni-per-giudizio` (15).
- **Testo:** "Le vendite con un risultato calcolabile sono {v}: sotto {soglia}
  il risultato è rumore e non dice niente sul metodo." Con una sola vendita:
  "C'è una sola vendita con un risultato calcolabile: sotto {soglia} il
  risultato è rumore e non dice niente sul metodo."
- **Effetto:** C3 non trae conclusioni (vedi C3).

#### B2 — Vendite senza acquisto corrispondente
- **Quando:** almeno una coppia ha venduto più di quanto aveva comprato nel periodo.
- **Testo:** "Su {m} coppie ({elenco}) hai venduto più di quanto avevi comprato
  nel periodo: l'acquisto è avvenuto prima, oppure le monete sono arrivate da
  depositi, airdrop o ricompense. Per queste il risultato non è calcolabile e
  non è sommato a zero."

#### B3 — Risultato non rappresentativo
- **Quando:** le coppie con risultato non calcolabile superano
  `commento.quota-coppie-incomplete-massima` (50%) delle coppie di trading.
- **Testo:** nessuno a parte, il caso è già descritto da B2.
- **Effetto:** C3 non trae conclusioni (vedi C3).

#### B4 — Valute miste
- **Quando:** le operazioni sono quotate in più di una valuta.
- **Testo:** "Le operazioni sono quotate in valute diverse ({EUR e USD}):
  volumi, commissioni e risultato sommano importi non omogenei e vanno letti
  come ordine di grandezza."

#### B5 — Metodo di calcolo (sempre, in fondo)
- **Quando:** sempre, se c'è almeno un'operazione di trading.
- **Testo:** "Il risultato è calcolato a costo medio ponderato: serve a leggere
  come hai operato e non vale come calcolo fiscale."

### C. Lettura → "Lettura"

#### C1 — Attività del periodo (sempre)
- **Testo:** "Dal {inizio} al {fine} hai fatto {n} operazioni di trading su {k}
  coppie, in {g} giorni diversi."
- **Se ci sono conversioni** aggiunge: "A parte, {f} conversioni valutarie."

#### C2 — Costo del periodo (sempre)
- **Testo:** "Le commissioni di trading sono state {c} {valuta}, pari al {p}%
  del volume scambiato."

#### C3 — Risultato
Un solo caso, il primo che si verifica, in quest'ordine:

| Caso | Testo |
|---|---|
| nessuna vendita nel periodo | "Nel periodo hai solo comprato: non c'è ancora un risultato realizzato." |
| nessuna coppia con risultato calcolabile | "Il risultato non è calcolabile su nessuna coppia: per tutte mancano gli acquisti corrispondenti alle vendite." |
| scatta B1 oppure B3 | "Il risultato realizzato è {±r} {valuta}, ma è calcolato solo su {m} coppie su {k} e su {v} vendite: non basta per dire come è andato il periodo." |
| risultato = 0 | "Il risultato realizzato è in pareggio, su {m} coppie su {k}." |
| altrimenti | "Il risultato realizzato è {±r} {valuta}, su {m} coppie su {k}." |

Niente aggettivi come "buono" o "deludente": il numero e il campione bastano.

### D. Costi → "Cosa dicono i dati"

#### D1 — Commissioni da taker
- **Quando:** almeno una coppia ha commissioni in % più vicine alla tariffa
  taker che a quella maker, cioè ≥ (`commissione-maker` + `commissione-taker`) / 2.
  Con le tariffe di oggi: ≥ 0,60%.
- **Testo:** "Su {m} coppie su {k} ({elenco}) hai pagato commissioni vicine
  alla tariffa taker ({taker}%), tra lo {min}% e lo {max}%: è la tariffa degli
  ordini eseguiti subito. Rispetto alla tariffa maker ({maker}%) hai pagato
  {d} {valuta} in più, il {q}% delle commissioni di trading del periodo."
- **Calcolo di {d}:** somma, sulle coppie con commissioni sopra la tariffa
  maker, di volume × (commissioni % − maker %) / 100.
- **Domanda:** "In quelle operazioni c'era davvero bisogno di entrare o uscire
  subito, o un ordine limite sarebbe bastato?"
- **Nota:** un ordine limite non garantisce la tariffa maker, e un'uscita da
  stop è per natura a mercato. Per questo la frase descrive quanto hai pagato
  e non dice "dovevi usare ordini limite".

#### D2 — Costo di andata e ritorno
- **Quando:** sempre, se c'è almeno un'operazione di trading.
- **Testo:** "Con commissioni medie del {p}%, tra entrata e uscita il prezzo
  deve muoversi almeno del {2p}% solo per chiudere in pari."
- **Domanda:** nessuna.

#### D3 — Conversioni valutarie
- **Quando:** almeno una conversione nel periodo.
- **Testo:** "Hai fatto {n} conversioni valutarie, costate {c} (il {p}% del
  convertito): un costo che dipende dalla valuta delle coppie scelte, non dal
  mercato."
- **Domanda:** "Quelle conversioni erano necessarie, o la stessa operazione si
  poteva fare su una coppia in {valuta della watchlist}?"
- **Nota:** la valuta della watchlist si ricava dalla watchlist stessa con
  `PairResolver`. Se la watchlist ha più valute, la domanda finisce dopo
  "necessarie?".

### E. Comportamento → "Cosa dicono i dati"

#### E1 — Operazioni fuori watchlist
- **Quando:** almeno un'operazione su una coppia il cui asset non è nella
  watchlist attuale. Il confronto è sull'asset, non sulla valuta: SOL/USD conta
  come SOL, che è in watchlist come SOLEUR.
- **Testo:** "{t} operazioni su {n} (il {q}% del volume) sono su coppie fuori
  dalla watchlist attuale: {elenco}."
- **Domanda:** "Le operazioni fuori watchlist sono passate dalla checklist
  della scansione, o sono nate fuori dal metodo?"
- **Nota:** il confronto è con la watchlist di oggi, anche per le operazioni
  fatte prima che esistesse. Per questo il testo dice "attuale".

#### E2 — Concentrazione su una coppia
- **Quando:** almeno 2 coppie nel periodo, e una sola supera
  `commento.concentrazione-massima` (50%) del volume.
- **Testo:** "{coppia} da sola fa il {q}% del volume del periodo."
- **Domanda:** "Concentrare il volume su {coppia} è stata una scelta, o è
  successo?"

#### E3 — Frequenza alta
- **Quando:** le operazioni per giorno attivo superano
  `commento.operazioni-al-giorno-massime` (3).
- **Testo:** "Nei giorni in cui hai operato hai fatto in media {x} operazioni:
  la guida prevede una revisione al giorno, a orario fisso."
- **Domanda:** "Quante di quelle operazioni erano previste dalla revisione del
  mattino?"

#### E4 — Posizioni ancora aperte
- **Quando:** una coppia ha una posizione rimasta il cui valore, quantità ×
  prezzo medio di acquisto, è almeno `commento.residuo-trascurabile` (1).
- **Testo:** "Resta aperta una posizione su {coppia} (circa {valore} {valuta}):
  il suo risultato non è ancora realizzato e non entra nel totale." Con più
  posizioni, una frase sola con l'elenco.
- **Domanda:** nessuna.

#### E5 — Residui trascurabili
- **Quando:** posizione rimasta con valore sotto `commento.residuo-trascurabile`.
- **Testo:** nessuno. Il commento **non** la chiama posizione aperta. Sono le
  briciole che restano dopo una vendita (nei tuoi dati: 0,15 EUR di HYPE e
  0,16 EUR di PEPE).

#### E6 — Risultato per coppia
- **Quando:** almeno 2 coppie con un risultato calcolato (risultato calcolabile
  e almeno una vendita).
- **Testo:** "Risultato per coppia, dove è calcolabile: {coppia} {±r}, {coppia}
  {±r}, …", ordinate dal più alto al più basso.
- **Domanda:** nessuna.

---

## 7. Scenari valutati e scartati

| Scenario | Perché è fuori |
|---|---|
| Dimensione delle operazioni rispetto al capitale | Per dire se hai rischiato l'1% serve lo stop di ogni operazione reale, e nei trade di Kraken non c'è. |
| Giudicare se un'operazione è stata "buona" | Serve il piano di partenza (entrata, stop, obiettivo), che sta nel registro su carta e non nei trade. |
| Previsioni o suggerimenti su cosa fare | Vietati per principio (§2). |
| Orari delle operazioni rispetto alla revisione delle 7 | Il dato c'è (l'ora di ogni trade) ma oggi non viene aggregato. Possibile in futuro, non adesso. |

## 8. Nuove soglie in `application.yml`

```yaml
# Soglie del commento del journal. Come per la scansione: cambiarle e'
# cambiare il modo di leggere i dati, quindi qui e non dentro un if.
commento:
  commissione-maker: 0.40               # % del tuo livello Kraken, da aggiornare se cambia
  commissione-taker: 0.80               # %
  quota-coppie-incomplete-massima: 50   # % oltre cui il risultato non e' rappresentativo
  concentrazione-massima: 50            # % del volume su una sola coppia
  operazioni-al-giorno-massime: 3       # media nei giorni con operazioni
  residuo-trascurabile: 1               # sotto questo valore una posizione rimasta e' polvere
```

Riusati senza duplicarli: `scansione.watchlist` e
`scansione.registrazioni-per-giudizio`. La sezione `anthropic` viene tolta.

## 9. Punti aperti (con la mia proposta)

1. **Tariffe maker/taker.** 0,40% e 0,80% sono quelle che mostra oggi la pagina
   di Kraken. Kraken le abbassa con il volume a 30 giorni, e i trade passati
   possono avere tariffe diverse. *Proposta:* tenerle in configurazione e
   aggiornarle a mano quando cambiano.
2. **Stablecoin.** Oggi un'operazione su USDC/EUR o EURC/EUR conta come
   trading, perché il riconoscimento delle conversioni guarda solo le valute
   fiat. *Proposta:* trattarle come conversioni. Però tocca `PairResolver`,
   quindi è una modifica al calcolo e non al commento: da decidere a parte.
3. **Tipo d'ordine e margine.** Sono già nei trade ma non vengono contati.
   D1 usa le commissioni come indicatore, e per lo scopo basta.
   *Proposta:* non aggiungerli ora.
4. **Soglia del campione.** Riuso `registrazioni-per-giudizio` (15) invece di
   crearne una nuova. Con il ritmo attuale (16 operazioni in un anno, 1 vendita
   con risultato calcolabile) il commento dirà quasi sempre che il campione non
   basta. È corretto, ma è bene saperlo prima.
5. **Watchlist attuale per E1.** Vedi la nota di E1. *Proposta:* va bene così.

## 10. Cosa cambierà nel codice (dopo l'approvazione)

- **Nuove:** `journal/GeneratoreCommento` (applica le regole e restituisce un
  `JournalReport`) e `journal/ProprietaCommento` (le soglie della sezione
  `commento`).
- **Modificata:** `TradingJournalService` chiama il generatore. Escono
  `SYSTEM_PROMPT`, `toAiView`, `parse` e `degradato`.
- **Eliminate:** `ai/AnthropicClient`, `ai/dto/*`, `config/AnthropicProperties`,
  `journal/dto/AiView`, il `RestClient` di Anthropic in `HttpClientConfig`, la
  sezione `anthropic` di `application.yml`, `ANTHROPIC_API_KEY` nel README.
- **Test:** uno per ogni regola, più uno su un caso costruito come i tuoi dati
  reali (§11).
- **Niente commit:** il codice viene consegnato, il commit lo fai tu.

---

## 11. Esempio sui tuoi dati reali

Le regole applicate a `dati/journal-esempio.json` (23/11/2025 – 23/11/2026).
Valute miste, quindi niente codice valuta sugli importi.

**Lettura**
> Dal 23 novembre 2025 al 23 novembre 2026 hai fatto 16 operazioni di trading
> su 8 coppie, in 11 giorni diversi. A parte, 7 conversioni valutarie. Le
> commissioni di trading sono state 1,67, pari allo 0,72% del volume scambiato.
> Il risultato realizzato è +1,94, ma è calcolato solo su 1 coppia su 8 e su
> 1 vendita: non basta per dire come è andato il periodo.

**Cosa dicono i dati**
> - Su 6 coppie su 8 (PEPE/EUR, HYPE/EUR, LIGHTER/USD, UNI/EUR, SOL/USD,
>   ONE/USD) hai pagato commissioni vicine alla tariffa taker (0,80%), tra lo
>   0,65% e lo 0,80%: è la tariffa degli ordini eseguiti subito. Rispetto alla
>   tariffa maker (0,40%) hai pagato 0,74 in più, il 44% delle commissioni di
>   trading del periodo.
> - 10 operazioni su 16 (il 63% del volume) sono su coppie fuori dalla
>   watchlist attuale: PEPE/EUR, LIGHTER/USD, UNI/EUR, ONE/USD, AMI/USD.
> - Hai fatto 7 conversioni valutarie, costate 0,26 (lo 0,20% del convertito):
>   un costo che dipende dalla valuta delle coppie scelte, non dal mercato.
> - Resta aperta una posizione su SOL/USD (circa 28,46 USD): il suo risultato
>   non è ancora realizzato e non entra nel totale.
> - Con commissioni medie dello 0,72%, tra entrata e uscita il prezzo deve
>   muoversi almeno dell'1,44% solo per chiudere in pari.

**Domande da porti**
> - In quelle operazioni c'era davvero bisogno di entrare o uscire subito, o
>   un ordine limite sarebbe bastato?
> - Le operazioni fuori watchlist sono passate dalla checklist della scansione,
>   o sono nate fuori dal metodo?
> - Quelle conversioni erano necessarie, o la stessa operazione si poteva fare
>   su una coppia in EUR?

**Limiti di questi dati**
> Su 6 coppie (PEPE/EUR, LIGHTER/USD, UNI/EUR, ONE/USD, ETH/EUR, AMI/USD) hai
> venduto più di quanto avevi comprato nel periodo: l'acquisto è avvenuto
> prima, oppure le monete sono arrivate da depositi, airdrop o ricompense. Per
> queste il risultato non è calcolabile e non è sommato a zero. C'è una sola
> vendita con un risultato calcolabile: sotto 15 il risultato è rumore e non
> dice niente sul metodo. Le operazioni sono quotate in valute diverse (EUR e USD):
> volumi, commissioni e risultato sommano importi non omogenei e vanno letti
> come ordine di grandezza. Il risultato è calcolato a costo medio ponderato:
> serve a leggere come hai operato e non vale come calcolo fiscale.

Non scattano: A1, A2, E2 (la coppia più grande, PEPE/EUR, fa il 22,5% del
volume), E3 (1,45 operazioni per giorno attivo), E6 (una sola coppia con
risultato calcolato). E5 scatta in silenzio su HYPE/EUR e PEPE/EUR.
