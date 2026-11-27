# Specifiche JML e verifica formale (appunti per il Lab 5)

Note di lavoro da cui partire per scrivere `verification.md` (massimo 5.000 caratteri).
Le specifiche sono commenti `/*@ ... @*/` e `//@`. Le uniche due modifiche al codice Java sono workaround per bug di OpenJML, con comportamento identico (vedi Setup, punto 5). Build e test restano verdi.

**Stato della checklist della guida**
- [x] OpenJML installato e configurato (sezione *Setup run openjml*)
- [x] Componenti verificabili identificati (*Classi specificate*)
- [x] Precondizioni, postcondizioni e invarianti aggiunti
- [~] ESC: run 1 su 6 classi; run 2 fatto su `SimplePalette`, `Allocator` e `RationalNumber`; `ImageService` corretto ma non rieseguito (strada b)
- [ ] RAC
- [~] `reports/verification.md`: scritta la parte statica (circa 3.350 caratteri su 5.000), linkato dal `README.md`; manca il commit

## Classi specificate

| # | Classe | Modulo | Modalità prevista | Cosa contiene |
|---|---|---|---|---|
| 1 | `org.apache.commons.imaging.common.RationalNumber` | libreria | ESC + RAC | 3 invarianti, 3 costruttori, `factoryMethod`, `gcd` (ricorsivo, `measured_by`), `valueOf`, `negate` (3 casi), `doubleValue`/`intValue`/`longValue` `spec_pure` |
| 2 | `org.apache.commons.imaging.common.Allocator` | libreria | ESC + RAC | `check(int)`, `check(int,int)`, `check(long,int)`, `checkByteArray`, `byteArray(int)`, `byteArray(long)`, `intArray`: `normal_behavior` / `exceptional_behavior` |
| 3 | `org.apache.commons.imaging.palette.SimplePalette` | libreria | ESC + RAC | invariante, costruttore (copia difensiva + NPE), `getEntry`, `getPaletteIndex` con **loop invariant** e `decreases`, `length` |
| 4 | `org.apache.commons.imaging.internal.SafeOperations` | libreria | ESC (attesi limiti) + RAC | `add`: 3 casi (somma, IAE, ArithmeticException), con `\sum` e `\bigint` |
| 5 | `org.apache.commons.imaging.PixelDensity` | libreria | **solo RAC** | 2 invarianti (unità ammesse, densità ≥ 0), 4 factory, costruttore privato, getter e `is*` pure, 6 conversioni con `requires !isUnitless()` |
| 6 | `it.course.imaging.image.ImageAsset` | backend | ESC + RAC | 8 invarianti (rispecchiano i vincoli `@Column`), costruttore, `onCreate`/`onUpdate`, 12 getter `spec_pure`, 2 setter |
| 7 | `it.course.imaging.image.ImageService` | backend | opzione A: soprattutto RAC | invariante sui collaboratori, model method `isNotBlank`/`isTrimmed`, specifiche leggere su tutti i metodi pubblici + `getAsset` e `deleteQuietly` |

**Specifiche di supporto**, aggiunte dopo la prima esecuzione di ESC (vedi *ESC Risultati e Fix*):
- `palette.Palette`: il contratto di `getEntry` e `length`.
- `ImagingRuntimeException` e `common.AllocationRequestException`: costruttori `pure`.

Escluse, con motivazione:
- `ImageController` e `ApiExceptionHandler`: sono controller, come indica la guida.
- Parser e `BinaryFunctions`: dipendono dall'I/O.
- `ColorConversions`: fa troppi calcoli in double.
- `ByteConversions`: è lunga e ripetitiva.

## Scelte di specifica da ricordare nel report
- **`SafeOperations.add`:** la precondizione chiede che non vada in overflow *nessuna somma parziale*, non solo il totale. `Math::addExact` dentro `reduce` lancia eccezione già con `[MAX_VALUE, 1, -1]`, anche se il totale ci starebbe.
- **`Allocator.check(int,int)`:** il prodotto viene scritto come `(long) request * elementSize`, così la specifica descrive la dimensione matematica e non quella troncata a int.
- **`RationalNumber.factoryMethod`:** è specificato solo il caso con argomenti nel range int, cioè il percorso esatto. Il percorso con il troncamento (`>>= 1`) volutamente non è specificato.
- **`RationalNumber.valueOf`:** non ha precondizioni, perché la Javadoc lo promette per qualunque double. Se l'invariante si rompe sul risultato, è un bug e non un errore di specifica.
- **`PixelDensity`:** la precondizione `!isUnitless()` sulle conversioni esprime che una densità senza unità non si converte in cm, pollici o metri (il codice dividerebbe per `unitLength == 0`).
- **`ImageAsset`:** gli invarianti sono esattamente i vincoli dello schema DB (lunghezze delle colonne, `nullable = false`). `createdAt` e `updatedAt` sono `nullable` perché vengono valorizzati solo da `@PrePersist`.
- **`ImageService`:** si usa `public behavior` con `signals_only RuntimeException`, perché i 404/400/500 e gli errori del repository sono eccezioni previste. Le postcondizioni dipendono da `repository.save(...)`, che ESC non conosce, quindi sono pensate per RAC. `String.trim()` non è ammesso nelle specifiche, quindi "non vuoto" e "senza spazi ai bordi" sono definiti con due model method basati su `charAt()`.

## Ipotesi prima della verifica (da confermare o smentire con ESC/RAC)
- `RationalNumber.negate()` con `numerator == Integer.MIN_VALUE` e tipo signed: `-numerator` vale 2^31 e viola l'invariante signed (e la precondizione del costruttore privato).
- `RationalNumber.negate()` unsigned, con numeratore piccolo e divisore ≥ 2^31: il risultato è signed ma ha il divisore fuori dal range int.
- `RationalNumber.factoryMethod(Integer.MIN_VALUE, -1)`: dopo la divisione per il gcd il cast `(int) n` riporta a `MIN_VALUE`, quindi la postcondizione "stesso valore" fallisce.
- `RationalNumber.valueOf(NaN)` crea `new RationalNumber(1, 0)`, che viola la precondizione `divisor != 0`. Con valori positivi molto piccoli (< 1/MAX_VALUE), `approx + 1` va in overflow e produce un divisore negativo.
- `Allocator`: `check` accetta richieste negative, quindi `xxxArray(-1)` lancia `NegativeArraySizeException`. → **Confermata da ESC (run 1)**, vedi sotto.
- `SafeOperations.add`: ESC probabilmente non riesce a ragionare su `Arrays.stream(...).reduce(...)`. Lo si dimostra con RAC.
- `ImageAsset()`, il costruttore `protected` richiesto da JPA: lascia i campi `null`, quindi viola gli invarianti e il non_null di default. È un conflitto noto tra framework e Design by Contract.
- `ImageAsset` con titolo > 120 caratteri: il costruttore pubblico non lo controlla. Il vincolo è garantito solo dal controller (`@Size`).
- `ImageService.convertToPng`: `ImagingRuntimeException` (per esempio `AllocationRequestException`) non viene catturata e diventa un 500 generico.
- `ImageService.deleteQuietly`: `Files.deleteIfExists` può lanciare `SecurityException`, che non è catturata, quindi `normal_behavior` potrebbe non essere dimostrabile.

---

# Setup run openjml

Versione: **OpenJML 21.0.29** (`openjml-ubuntu-24.04-21.0.29.zip`), che include il proprio JDK e il solver Z3 5.1.0. Tutti i comandi partono dalla root del repo e vanno lanciati nello **stesso terminale** (le variabili `export` valgono solo lì).

- **Installazione e PATH.** Lo zip è estratto in `~/opt/openjml`, e `~/.bashrc` (riga 15) contiene già l'export del PATH. Contenuto utile della cartella:
  - `openjml`: il tool (modalità `-check`, `-esc`, `-rac`);
  - `openjml-java`: `java` con il runtime JML già incluso, da usare per eseguire le classi RAC;
  - `jmlruntime.jar`: la stessa libreria di runtime, per chi usa il `java` normale;
  - `specs/`: le specifiche JML delle classi del JDK (es. `specs/java/lang/Math.jml`).
  ```bash
  export OPENJML_HOME="$HOME/opt/openjml"   # usata nei comandi RAC
  export PATH="$OPENJML_HOME:$PATH"         # già presente in ~/.bashrc
  openjml --version                         # deve rispondere 21.0.29
  ```
- **Cartella dei log.** L'output di ogni esecuzione serve come evidenza per il report.
  ```bash
  mkdir -p reports/exports/jml-logs
  ```
  Si trova in `reports/` perché lì Apache RAT non richiede l'header di licenza.
- **Compilazione dei progetti** (serve a Maven per risolvere le dipendenze; OpenJML lavora sui sorgenti):
  ```bash
  mvn -o -q compile                     # libreria
  mvn -q -f backend/pom.xml compile     # backend
  ```
- **Classpath delle dipendenze.** Maven scrive in un file l'elenco dei jar da cui dipende ciascun progetto.
  ```bash
  mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
  mvn -q -f backend/pom.xml dependency:build-classpath -Dmdep.outputFile=target/cp.txt
  export LIB_DEPS="$(cat target/cp.txt)"           # commons-io, commons-lang3 (+ jar di test, innocui)
  export BE_DEPS="$(cat backend/target/cp.txt)"    # Spring, JPA, Jakarta Validation, commons-imaging
  ```
  - Va lanciato **senza `-o`**: il plugin `dependency` non era nella cache locale.
  - `mvn clean` cancella `target/cp.txt`, quindi dopo un clean il comando va ripetuto.
- **Regola del classpath.** In `-cp` vanno **solo i jar delle dipendenze**; le classi del progetto arrivano da `-sourcepath`, dove OpenJML legge anche il JML delle classi usate. Se in `-cp` c'è `target/classes`, javac carica il bytecode (più recente del sorgente), che non ha JML. Il risultato è un crash interno di OpenJML (`NullPointerException ... cspecs.javaDecl is null`), getter `pure` visti come non puri ed errori di tipo falsi. `--specs-path` non serve: il JML è scritto dentro i `.java`.
- **Type-check** (veloce: controlla solo sintassi e tipi delle specifiche, da lanciare prima di ESC):
  ```bash
  openjml -check -cp "$LIB_DEPS" -sourcepath src/main/java <file.java ...>
  openjml -check -cp "$BE_DEPS" -sourcepath backend/src/main/java <file.java ...>
  ```
- **ESC** (una classe alla volta). `-progress` mostra l'esito metodo per metodo; `tee` salva il log. I log dei run successivi ai fix hanno suffisso `-v2`, così si conserva lo stato prima/dopo.
  ```bash
  openjml -esc -progress -cp "$LIB_DEPS" -sourcepath src/main/java \
    src/main/java/org/apache/commons/imaging/palette/SimplePalette.java 2>&1 | tee reports/exports/jml-logs/esc-SimplePalette.log
  openjml -esc -progress -cp "$LIB_DEPS" -sourcepath src/main/java \
    src/main/java/org/apache/commons/imaging/common/Allocator.java 2>&1 | tee reports/exports/jml-logs/esc-Allocator.log
  # stesso schema per common/RationalNumber.java e internal/SafeOperations.java (PixelDensity: solo RAC)
  openjml -esc -progress -cp "$BE_DEPS" -sourcepath backend/src/main/java \
    backend/src/main/java/it/course/imaging/image/ImageAsset.java 2>&1 | tee reports/exports/jml-logs/esc-ImageAsset.log
  # stesso schema per ImageService.java
  ```
  ESC verifica anche le classi usate dal file che trova in `-sourcepath`: per esempio `Palette` e `ImagingException` compaiono nel riepilogo di `SimplePalette`.
- **RAC** (da fare). Si compilano le classi instrumentate in una cartella separata, poi si esegue un driver mettendo quella cartella **prima** di `target/classes`:
  ```bash
  openjml -rac -d target/jml-rac -cp "$LIB_DEPS" -sourcepath src/main/java <le 5 classi della libreria>
  openjml-java -ea -cp "target/jml-rac:target/classes:$LIB_DEPS" LibraryRacDriver 2>&1 | tee reports/exports/jml-logs/rac-library.log
  ```

**Problemi incontrati nel setup e come sono stati risolti** (tutti trovati con `openjml -check`):
1. **`target/classes` nel classpath**: il crash e gli errori falsi descritti sopra. Risolto rispettando la regola del classpath.
2. **`pure` → `spec_pure`** sugli override di `Number` (`doubleValue`, `intValue`, `longValue`). OpenJML li dichiara `spec_pure`, e chi li sovrascrive deve essere almeno altrettanto puro. Era un errore di specifica, corretto. I getter di `ImageAsset` usati nelle specifiche sono passati a `spec_pure` per lo stesso motivo.
3. **`String.trim()` non ammesso nelle clausole** ("non-pure method"): sostituito con i model method `isNotBlank`/`isTrimmed` in `ImageService`.
4. **`nullable` sugli array** va scritto `int /*@ nullable @*/ []`, altrimenti l'annotazione si applica al tipo `int` degli elementi.
5. **Due bug di OpenJML 21.0.29**, riprodotti su file minimi con codice Java valido:
   - `static final long X = -1;` → `Initializer does not match compiled value: -1 vs. -1`. Workaround: `-1L` in `RationalNumber.serialVersionUID`, che produce lo stesso bytecode.
   - `Objects.requireNonNull(x, "m").clone()` → OpenJML deduce `T = Object` sulle chiamate generiche concatenate. Workaround: una variabile locale nel costruttore di `SimplePalette`.

   Entrambi i workaround sono stati approvati e commentati nel codice.
6. **Apache RAT** fa fallire la build per ogni file senza header di licenza fuori dalle cartelle escluse (per questo le note sono in `reports/`). `verification.md` nella root richiederà un `<inputExclude>` nel `pom.xml`, come per `README.md`.

---

# ESC Risultati e Fix

**Classificazione usata per ogni avviso:**
- **(S)** errore di specifica: la specifica è sbagliata o troppo forte, e si corregge;
- **(B)** difetto reale del codice: si documenta e non si corregge;
- **(L)** limite dello strumento o delle specifiche JDK di OpenJML: si documenta e la proprietà si verifica con RAC.

## SimplePalette, run 1 (`esc-SimplePalette.log`)
Riepilogo: **Valid 7, Invalid 2**, Skipped 3 (metodi astratti di `Palette`), 12 metodi in totale, 4 verification failures, 6,5 s.

| Metodo | Esito | Avviso | Classe | Analisi |
|---|---|---|---|---|
| `getPaletteIndex` | ✅ | — | — | Loop invariant, `decreases` e le 4 postcondizioni (inclusa "prima occorrenza") dimostrati. |
| `length` | ✅ | — | — | |
| `getEntry` | ❌ | `PossiblyNegativeIndex`, `PossiblyTooLargeIndex` | **S** | `Palette.getEntry` non aveva specifica. In JML un metodo senza specifica ha un caso di default `requires true`, che con `also` si aggiunge al caso di `SimplePalette`, quindi il contratto complessivo ammetteva qualunque indice. |
| costruttore | ❌ | `ExceptionList` su `signals_only NullPointerException` | **L** | La specifica OpenJML di `Objects.requireNonNull(obj, msg)` dice che con `null` lancia, ma non quale eccezione (nel file c'è un `TODO`). |

**Fix dopo il run 1:**
- `Palette` (interfaccia): aggiunto `getEntry` con `public normal_behavior requires 0 <= index && index < length(); pure`, e `length` con `ensures \result >= 0; pure`. Il contratto ora sta nel supertipo e l'implementazione lo può solo indebolire.
- Costruttore: nessun fix (L). Che l'eccezione sia una NPE va mostrato con RAC.

**Run 2 (`esc-SimplePalette-v2.log`)**: **Valid 8, Invalid 1**, 2 failure (prima erano 4), 5,2 s. Il risultato coincide con l'atteso: `getEntry` diventa valido e resta solo l'`ExceptionList` del costruttore (L). È lo stato finale, non serve un run 3.

## Allocator, run 1 (`esc-Allocator.log`)
Riepilogo: **Valid 17, Invalid 8**, 25 metodi in totale (includono le classi usate, verificate insieme), 43 verification failures, 8 warnings, 38,6 s.
- Validi tra quelli specificati: `byteArray(int)`, `byteArray(long)`, `intArray`, `checkByteArray`. ESC li dimostra usando solo il *contratto* di `check(...)`, anche se `check(...)` stesso non è dimostrato: è la verifica modulare.
- Invalidi: `check(int)`, `check(int,int)`, `check(long,int)` e i non specificati `charArray`, `doubleArray`, `floatArray`, `longArray`, `shortArray`.

| Avviso | Metodi | Classe | Analisi |
|---|---|---|---|
| `PossiblyNegativeSize` (5) | `charArray`, `doubleArray`, `floatArray`, `longArray`, `shortArray` | **B** | `check()` restituisce le richieste negative senza rifiutarle, e `new T[-1]` lancia `NegativeArraySizeException`, un'eccezione non documentata nella Javadoc (`@throws` cita solo `AllocationRequestException`). **Conferma l'ipotesi.** Nei metodi specificati la precondizione `0 <= request` rende esplicito il requisito. |
| warning `assignable \everything` + `Assignable` (11) + `ExceptionList` (3) | `check(int)`, `check(int,int)`, `check(long,int)` | **S** (specifica mancante) | I costruttori di `AllocationRequestException` non avevano specifica, quindi per default potevano modificare tutto e lanciare qualunque eccezione. Questo rompe il `pure` e il `signals_only AllocationRequestException` dei tre `check`. |
| `Postcondition` (3) + `ExceptionalPostcondition` (2) | `check(int,int)`, `check(long,int)` | **L** | `Math.multiplyExact` e `Math.toIntExact` non hanno specifica in `specs/java/lang/Math.jml`, quindi il prover non conosce né il risultato né quando lanciano `ArithmeticException`. |

**Fix dopo il run 1** (solo commenti JML, nessuna modifica al codice):
- `ImagingRuntimeException`: i 2 costruttori ora sono `public normal_behavior pure` (chiamano `RuntimeException(...)`, già `pure` nelle specifiche di OpenJML).
- `AllocationRequestException`:
  - campi `limit` e `request` resi `spec_public`;
  - i 4 costruttori sono `public normal_behavior pure`, con `ensures this.limit == limit` (e `this.request == request` dove il parametro è il `BigInteger`);
  - l'helper privato `format` è dichiarato `pure` (usa `String.format`, che è `pure` nelle specifiche di OpenJML).
- `Math.*Exact`: **opzione 3a scelta.** Niente `--specs-path` personalizzato, perché un `Math.jml` nostro sostituirebbe per intero quello di OpenJML. Si documenta come limite e il comportamento di `check(int,int)` / `check(long,int)` si verifica con RAC.
- Ulteriore limite già noto: anche `BigInteger.valueOf` non ha specifica in OpenJML. È usato nel percorso di overflow di `check(int,int)` e nei costruttori `(int, int)` / `(int, long, Throwable)` di `AllocationRequestException`, quindi lì potrebbero restare avvisi (L).
- Type-check dopo i fix: `openjml -check` pulito su `Palette`, `SimplePalette`, `ImagingRuntimeException`, `AllocationRequestException` e `Allocator`; `mvn compile` verde.

**Run 2 (`esc-Allocator-v2.log`)**: **Valid 18, Invalid 7**, 27 failure (prima erano 43), 0 warning (prima erano 8), 27,3 s.
- `check(int)` diventa valido, e spariscono il warning `assignable \everything` e gli avvisi dovuti ai costruttori.
- Restano i 5 `PossiblyNegativeSize` (B).
- Su `check(int,int)` e `check(long,int)` restano `Assignable`, `ExceptionList`, `Postcondition` ed `ExceptionalPostcondition`. Sono dovuti a `Math.*Exact` e `BigInteger.valueOf`, che non hanno specifica (L, come previsto).
- È lo stato finale, non serve un run 3.

## RationalNumber, run 1 (`esc-RationalNumber.log`)
Riepilogo: **Valid 7, Invalid 8, Timeout 1** (`valueOf`), 16 metodi, 34 failure, circa 20 minuti.

| Metodo | Avviso | Classe | Analisi |
|---|---|---|---|
| `intValue`, `longValue` | `PossiblyDivideByZero`, `overflow in long divide` | **S** | `Number.jml` dichiara questi metodi `helper`, quindi l'invariante `divisor != 0` (e i range) **non viene assunto**. |
| `doubleValue` | `Postcondition` | **S** | Stessa causa: con `divisor == 0` il risultato è NaN, e `NaN == NaN` è falso. |
| `intValue` | `ArithmeticCastRange` (codice e `ensures`) | **S** | Il troncamento a int degli unsigned > 2^31-1 è voluto (`Number.intValue` restituisce un'approssimazione), ma OpenJML in modalità *safe* lo segnala come overflow. |
| `negate` | `Precondition` del costruttore privato, `Postcondition` | **B** | **Conferma le ipotesi 1 e 2**: `-Integer.MIN_VALUE` esce dal range int; un unsigned con divisore ≥ 2^31 diventa signed con divisore fuori range. |
| `negate` | `ExceptionList` (riga 358) | da capire | Si ricontrolla nel run 2. |
| `factoryMethod` | `ArithmeticCastRange` sul cast `(int) n`, timeout | **B** + L | **Conferma l'ipotesi 3** (`MIN_VALUE, -1` → `n = 2^31`). |
| `gcd` | `TerminationDecreases`, timeout | **L** | `%` su long è aritmetica non lineare. |
| `valueOf` | timeout (300 s) | **L** | L'ipotesi su NaN si verifica con RAC. |
| `toString`, `Option.toString` | `Postcondition` di `Object.jml` | **L** | `Object.toString()` in OpenJML è `assignable \everything`. |

**Fix dopo il run 1** (solo commenti JML):
- **R1:** `doubleValue` ha `requires divisor != 0`; `intValue` e `longValue` hanno `requires divisor != 0 && Integer.MIN_VALUE <= numerator && numerator <= 0xFFFFFFFFL`.
- **R2:** `intValue` è dichiarato `code_java_math spec_java_math`, perché l'aritmetica Java con wrap-around è il comportamento voluto.

**Run 2 (`esc-RationalNumber-v2.log`)**: **Valid 8, Invalid 7, Timeout 1**, 24 failure (prima erano 34), circa 17 minuti. L'atteso si è avverato **solo in parte**:
- `doubleValue` diventa **valido**: R1 funziona.
- In `intValue` spariscono i 4 `ArithmeticCastRange`: R2 funziona.
- **Restano** `PossiblyDivideByZero` su `intValue` e `longValue`, più `overflow in long divide` su `longValue`. Causa (**L**, più precisamente **subtyping comportamentale**): con `also` il metodo deve rispettare *anche* il caso ereditato da `Number.jml`, che non ha precondizione (`requires true`) ed è `helper`. Nel caso ereditato `divisor == 0` resta quindi possibile, e una sottoclasse non può rafforzare la precondizione del supertipo. L'invariante sarebbe l'unico strumento giusto, ma `helper` lo disattiva. Per eliminarli servirebbe una specifica `Number.jml` personalizzata, scartata come per `Math.jml` (opzione 3a). `doubleValue` passa perché la divisione tra double per zero non lancia eccezioni e la `ensures` ereditata è informale.
- `factoryMethod`: oltre all'`ArithmeticCastRange` ora il prover trova anche un controesempio alla **`Postcondition`** "stesso valore" (riga 64), e prima del timeout ci arrivava. Rafforza il **B** dell'ipotesi 3.
- `negate`: invariato (B). L'`ExceptionList` alla riga 365 (prima 358) resta **senza causa accertata**. La specifica JDK del costruttore di `NumberFormatException` è `pure`, quindi il sospetto è la concatenazione di stringhe nel messaggio. Lo trattiamo come L e lo verifichiamo con RAC.
- Invariati `gcd` (timeout), `valueOf` (timeout) e `toString` (L).
- È lo stato finale, non serve un run 3.

## SafeOperations, run 1 (`esc-SafeOperations.log`)
Riepilogo: **Valid 1, Invalid 1**, 2,2 s.
- `add`: le `Postcondition` alle righe 40 (somma) e 44 (il caso `ArithmeticException`) non sono dimostrate. È un limite (**L**): OpenJML non sa ragionare su `Arrays.stream(...).reduce(0, Math::addExact)`, quindi non collega il risultato a `\sum`.
- I warning sui trigger SMT dei quantificatori sono solo informativi.
- Il solo fix possibile sarebbe riscrivere il codice come ciclo: non lo facciamo, la proprietà si verifica con RAC. Non serve un run 2.

## ImageAsset, run 1 (`esc-ImageAsset.log`)
Riepilogo: **Valid 15, Invalid 3**, 18 metodi, 47 failure, circa 17 minuti (quasi tutti in timeout).
- Validi: costruttore pubblico, getter e setter.
- `ImageAsset()`: `NullField` → **B** per scelta di design. **Conferma l'ipotesi** sul costruttore richiesto da JPA.
- `onCreate` e `onUpdate`: `NullField` su tutti i campi, `InvariantExit`, `Assignable`, `ExceptionalPostcondition` e timeout → **L**. In `specs/java/time/Instant.jml` la specifica di `Instant.now()` è commentata, quindi la chiamata vale come `assignable \everything` e può lanciare qualunque eccezione.
- Non serve un run 2.

## ImageService, run 1 (`esc-ImageService.log`)
Riepilogo: **Valid 19, Invalid 11, Timeout 1** (`upload`), **Error 1**, 33 metodi (includono `ImageAsset` e `ImageResponse`), circa 55 minuti.

| Avviso | Dove | Classe |
|---|---|---|
| `Precondition` su `new ImageResponse(...)`: `createdAt` non-null | `ImageResponse.from` | **S** |
| `Object.equals` "precondition conjunct is false", `UndefinedCalledMethodPrecondition` | `findById`, `update`, `getAsset` e i chiamanti | **S** (`.equals` su `UUID`, che non ha specifica) |
| `InvariantEntrance` / `InvariantExit` di `ImageAsset` | `getAsset`, `content`, `convertToPng` | **L** (`repository.findById` di Spring Data non ha specifica) |
| `PossiblyNullReturn` | `findAll` | **L** (`Stream.toList`) |
| `Postcondition` | `update` (trim), `convertToPng` (`writeImageToBytes`, `endsWith`) | **L** |
| `INTERNAL ERROR: finally stack is unexpectedly empty` | tutti i metodi | **L** (bug di OpenJML) |
| errore `unknown constant i` | model method `isNotBlank` | **L** (bug di OpenJML con `\exists` nel corpo di un model method) |
| timeout | `upload`, `content`, `convertToPng`, `update`, `getAsset` | **L** |

**Fix dopo il run 1** (strada b: applicati e **non** rieseguiti, perché il run dura circa un'ora; si verificano con RAC):
- **S1:** in `ImageResponse` i campi `createdAt` e `updatedAt` sono `/*@ nullable @*/`.
- **S2:** le clausole `.equals(id)` sono state tolte dalla specifica statica e spostate in clausole `//+RAC@ ensures ...`, attive solo con `-rac`.

## Da eseguire
- RAC su tutte le 7 classi (servono i driver).
