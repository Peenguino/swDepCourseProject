# Lab 6 - Microbenchmarking (JMH) - note di lavoro

Note di riferimento per la stesura di `reports/benchmarking.md` (max 5000 caratteri).

## Piano

| Sessione | Codice | Warm-up | Scopo |
|---|---|---|---|
| S1 baseline | originale | fisso (5 x 1 s) | riferimento |
| S2 | fix H1 + H2 + H3 | fisso, identico a S1 | delta vs S1 |
| S3 | come S2 | dinamico con AMBER `@DynamicHalt` | warm-up dinamico vs fisso |

### Metodi scelti (percorso critico `ImageService.convertToPng` -> `PngWriter.writeImage`)

| # | Benchmark | Classe | Ipotesi |
|---|---|---|---|
| B1 | `Imaging.writeImageToBytes(img, PNG)` | `src/test/java/org/apache/commons/imaging/benchmarks/PngWriterBenchmark.java` | H1 (+H2, H3 nel totale) |
| B2 | `PaletteFactory.hasTransparency` / `isGrayscale` (caso peggiore) | `src/test/java/org/apache/commons/imaging/benchmarks/PaletteFactoryBenchmark.java` | H2 |
| B3 | `PngCrc` come in `writeChunk` vs `java.util.zip.CRC32` | `src/test/java/org/apache/commons/imaging/formats/png/PngCrcBenchmark.java` | H3 |
| B4 | `ImageService.convertToPng` end-to-end | `backend/src/test/java/it/course/imaging/benchmarks/ConvertToPngBenchmark.java` | effetto applicativo |

Immagini: `BenchmarkImages` (gradiente + rumore con seed 42), cosi' il Deflater lavora come su una foto.
Tipi in B1: `RGB` (true-color, isGrayscale esce subito), `ARGB_OPAQUE` (hasTransparency scansiona tutto),
`GRAY_CONTENT` (isGrayscale scansiona tutto -> percorso grayscale).

Esclusi: `guessFormat` (legge 2 byte, ns), `upload()` (scrive file nuovi: effetti collaterali),
`SimplePalette`, `RationalNumber` (non sul percorso del backend).

### Ipotesi di collo di bottiglia

- **H1** `PngWriter.writeImage`: `baos.write(int)` per ogni byte (3-4 per pixel) su `ByteArrayOutputStream`
  (metodo `synchronized`, buffer che parte da 32 byte e raddoppia). Fix: `byte[]` preallocato `height * (1 + width * bpp)`.
- **H2** `PaletteFactory.hasTransparency` / `isGrayscale`: `getRGB(x, y)` per pixel, chiamati a ogni scrittura PNG.
  Fix: `getRGB` per riga.
- **H3** `PngCrc`: `new PngCrc()` per chunk (tabella ricalcolata), CRC byte per byte in `long`.
  Fix: delegare a `java.util.zip.CRC32` (intrinsic). Attenzione: `crc(buf, len)` ignora `len` (vedi `PngCrcTest`).

## Configurazione fissa (S1, S2)

Annotazioni su ogni classe: `@BenchmarkMode(AverageTime)`, `@Warmup(iterations = 5, time = 1 s)`,
`@Measurement(iterations = 5, time = 1 s)`, `@Fork(3)`, `@State(Scope.Benchmark)`.
-> 15 campioni per combinazione di parametri (3 fork x 5 iterazioni).

Parametri: B1 `size` {256, 1024} x `kind` {RGB, ARGB_OPAQUE, GRAY_CONTENT}; B2 `size` {256, 1024};
B3 `length` {1 KiB, 64 KiB, 1 MiB}; B4 `size` {256, 1024}.

## Setup e note tecniche

- Commit setup pom: `1e189e06 configuring POMs for JMH`.
- Libreria: profilo `benchmark` ereditato da commons-parent 107 (exec-maven-plugin, classpath di test, risultati JSON).
  Backend: profilo `benchmark` con lo stesso schema in `backend/pom.xml` (exec-maven-plugin 3.6.4).
- JDK 27: la scoperta implicita degli annotation processor e' disabilitata (da JDK 23) -> `annotationProcessorPaths`
  esplicito in entrambi i pom. Senza, nessun `META-INF/BenchmarkList`.
- Libreria: commons-parent passa `-proc:none` nella build normale e lo rimuove solo con `-Pbenchmark`
  -> i benchmark si generano/eseguono solo con il profilo (build normale invariata).
- `maven-shade-plugin` del tutorial non usato: la libreria e' un artefatto riusato dal backend, lo shade lo
  sostituirebbe con un uber-jar; inoltre JMH e i benchmark sono in scope test.
- Avviso JMH 1.37 su JDK 27: `sun.misc.Unsafe::objectFieldOffset` deprecato (non bloccante).
- Blackhole mode: `compiler` (auto-detected): da mantenere uguale in S1/S2/S3.
- B4: repository simulato con `java.lang.reflect.Proxy` (risponde solo a `findById`), niente Spring/DB/Mockito.
- B4 usa la libreria installata in `~/.m2`: prima di ogni sessione `mvn install` della libreria.
- Checkstyle: i nuovi file passano; restano 4 violazioni preesistenti in test del Lab 4
  (`TagInfoAsciiTest`, `IcnsParserBranchTest`, `JpegSyntheticHeaderTest`, `PsdSyntheticFilesTest`). RAT: OK.

### Isolamento delle esecuzioni (problema trovato al primo tentativo di S1)

Primo tentativo S1 (12:45) **scartato**: il language server Java di VS Code (`redhat.java`, JDT/ECJ) compila il
progetto Maven dentro `target/` e ha riscritto le classi generate da JMH subito dopo il `clean` di Maven
(`PngCrcBenchmark` fallito con `java.lang.Error: Unresolved compilation problems`, errore tipico di ECJ).
Anche le classi misurate da B1/B2 e il jar installato per B4 potevano essere compilati da ECJ o sovrascritti
a meta' run -> condizioni non riproducibili.

Procedura adottata per S1, S2, S3: copia isolata del progetto (rsync, senza `target/`, `.git`, `workingNotes`,
`frontend`) in una cartella non osservata da VS Code, poi install + benchmark da li'.

## Comandi eseguiti

```bash
# copia isolata (BENCH = cartella temporanea fuori dal workspace)
rsync -a --exclude target --exclude .git --exclude workingNotes --exclude frontend ./ $BENCH/
cd $BENCH

# 1. libreria installata in ~/.m2 (la usa il backend in B4)
mvn -q install -DskipTests -Djacoco.skip=true

# 2. libreria: B1, B2, B3
mvn -Pbenchmark clean test -Dbenchmark=org.apache.commons.imaging
#    -> target/jmh-result.org.apache.commons.imaging.json

# 3. backend: B4
cd backend && mvn -Pbenchmark clean test -Dbenchmark=it.course.imaging
#    -> backend/target/jmh-result.it.course.imaging.json
```

I JSON e i log vengono copiati in `workingNotes/benchmarking/<sessione>/` (`target/` si svuota a ogni `clean`).

# S1 Baseline

**Risultati:** [libreria (B1-B3) JSON](./S1/library.json) · [backend (B4) JSON](./S1/backend.json) ·
log: [libreria](./S1/library.log), [backend](./S1/backend.log), [install](./S1/install.log) · [condizioni del run](./S1/run-info.txt)

**Condizioni:** 27/11/2026 12:53-13:03 (~10 min) · AMD Ryzen 5 5500U (6C/12T), 7 GB RAM · Linux 7.3.5 (CachyOS) ·
OpenJDK 27.0.1.1 · JMH 1.37, blackhole mode `compiler` · **a batteria, governor `powersave`** (S2 e S3 nelle stesse
condizioni) · codice al commit `1e189e06` + sorgenti dei benchmark · copia isolata, fuori dal workspace VS Code.
Configurazione: AverageTime, warm-up 5 x 1 s, misura 5 x 1 s, 3 fork -> 15 campioni. Errore = intervallo di confidenza 99,9%.

### B1 - `PngWriterBenchmark.writePng` (ms/op)

| kind | 256 | 1024 |
|---|---|---|
| RGB | 6.727 ± 0.021 | 112.407 ± 0.460 |
| ARGB_OPAQUE | 6.832 ± 0.014 | 114.612 ± 0.462 |
| GRAY_CONTENT | 2.183 ± 0.005 | 37.702 ± 0.174 |

### B2 - `PaletteFactoryBenchmark` (us/op, caso peggiore = scansione completa)

| metodo | 256 | 1024 |
|---|---|---|
| hasTransparencyFullScan (ARGB opaca) | 122.251 ± 0.943 | 1943.551 ± 7.356 |
| isGrayscaleFullScan (contenuto grigio) | 122.931 ± 0.733 | 1904.277 ± 12.357 |

### B3 - `PngCrcBenchmark` (us/op)

| length | pngCrcPerChunk | jdkCrc32 | rapporto |
|---|---|---|---|
| 1 KiB | 3.102 ± 0.045 | 0.111 ± 0.001 | ~28x |
| 64 KiB | 132.500 ± 0.898 | 4.146 ± 0.005 | ~32x |
| 1 MiB | 2092.723 ± 3.852 | 65.649 ± 0.621 | ~32x |

### B4 - `ConvertToPngBenchmark.convertToPng` (ms/op, sorgente BMP RGB)

| size | score |
|---|---|
| 256 | 7.553 ± 0.050 |
| 1024 | 123.763 ± 0.554 |

### Prime osservazioni (da verificare in S2)

- **Misure stabili:** errore sotto l'1% in tutte le combinazioni, anche a batteria.
- **B4 ~= B1:** la codifica PNG e' ~91% di `convertToPng` (1024: 112.4 ms di B1 RGB su 123.8 ms di B4; il resto,
  ~11 ms, e' lettura del file + decodifica BMP). Il backend eredita direttamente il costo del writer.
- **H2 si vede nel totale:** ARGB_OPAQUE - RGB a 1024 = 2.2 ms ~= costo di `hasTransparency` misurato in B2 (1.94 ms).
  Per RGB la scansione non avviene (`TYPE_INT_RGB` non ha alpha, `isGrayscale` esce al primo pixel).
  Per GRAY_CONTENT `isGrayscale` (1.9 ms) e' ~5% di 37.7 ms. Scala lineare: ~1.85 ns/pixel per entrambi.
- **H3 confermata in isolamento:** `PngCrc` ~2.0 ns/byte vs `CRC32` ~0.063 ns/byte (~32x). Su 1 KiB il rapporto
  scende a ~28x per il costo fisso della tabella ricalcolata a ogni chunk (~1 us).
  Peso stimato in B1: IDAT di RGB 1024 = 2.78 MB (rapporto di compressione 0.88 sui dati grezzi, contenuto
  "fotografico") -> ~5.6 ms di CRC su 112 ms (~5%); con `CRC32` ~0.2 ms.
- **H1** non si puo' isolare senza la modifica: lo stimeremo da S2 come differenza residua.
  Il resto del tempo di B1 e' presumibilmente `Deflater` (non modificabile, livello di compressione fisso):
  i guadagni attesi su B1/B4 sono quindi percentualmente contenuti, mentre B2/B3 mostrano il fix in isolamento.

Dimensioni immagini usate (grezze -> PNG): RGB/ARGB 1024: 3 146 752 -> 2 782 234 B; GRAY 1024: 1 049 600 -> 933 778 B;
RGB/ARGB 256: 196 864 -> 179 903 B; GRAY 256: 65 792 -> 59 822 B.

# Fix H1-H3

Commit di partenza: `b59fbc55` (S1). File modificati (solo `src/main`, sorgenti dei benchmark invariati):

- **H1** `formats/png/PngWriter.java` (`writeImage`, entrambi i rami con e senza predictor): i byte filtrati vanno
  in un `byte[]` allocato una volta con la dimensione esatta `height * (1 + width * samplesPerPixel)`
  (`Allocator.byteArray(long)`, che rispetta il limite di allocazione della libreria) e riempito con un indice,
  invece di `ByteArrayOutputStream.write(int)` per ogni byte. `samplesPerPixel`: 1 indicizzata; 1 grigio / 3 RGB, +1 con alpha.
- **H2** `palette/PaletteFactory.java` (`hasTransparency`, `isGrayscale`): `getRGB(0, y, width, 1, row, 0, width)`
  per riga invece di `getRGB(x, y)` per pixel; l'uscita anticipata e' invariata.
- **H3** `formats/png/PngWriter.java` (`writeChunk`): CRC del chunk con `java.util.zip.CRC32` (stesso CRC-32 standard,
  polinomio 0xEDB88320). `formats/png/PngCrc.java`: tabella `static final` calcolata una volta per classe
  (prima ricalcolata a ogni `new PngCrc()`); `PngCrc` non e' piu' usata da `PngWriter` ma resta per la sua API.
  Comportamento di `crc(buf, len)` (ignora `len`) invariato -> `PngCrcTest` passa.

Nota su B3 in S2: il benchmark e' rimasto identico a S1. `pngCrcPerChunk` ora misura `PngCrc` con tabella statica
(effetto della sola tabella), mentre `jdkCrc32` e' esattamente cio' che fa il nuovo `writeChunk`.

### Verifiche (copia isolata)

- `mvn clean verify`: **1679 test, 0 fallimenti, 0 errori, 7 skipped**; "All coverage checks have been met" (gate JaCoCo).
- `mvn apache-rat:check pmd:check spotbugs:check`: BUILD SUCCESS, SpotBugs `BugInstance size is 0`.
- `mvn checkstyle:check`: solo le 4 violazioni preesistenti (test Lab 4), nessuna nei file modificati.
- **Output identico byte per byte** a S1: SHA-256 dei PNG scritti dal jar S1 (`~/.m2`) e dal codice corretto,
  immagini 300x300, 9 percorsi del writer (rgb, argb opaca, argb traslucida, grigio, grigio+alpha,
  predictor rgb/argb, indicizzata, indicizzata+alpha): 9/9 identici.
  (Primo tentativo di `verify` fallito per RAT: avevo scritto i log nella radice della copia -> file senza licenza.)

```bash
# verifiche (VERIFY = copia isolata, log fuori dalla copia)
rsync -a --exclude target --exclude .git --exclude frontend ./ $VERIFY/ && cd $VERIFY
mvn clean verify
mvn apache-rat:check pmd:check spotbugs:check
mvn checkstyle:check
```

# S2 Dopo i fix (warm-up fisso)

**Risultati:** [libreria (B1-B3) JSON](./S2/library.json) · [backend (B4) JSON](./S2/backend.json) ·
log: [libreria](./S2/library.log), [backend](./S2/backend.log), [install](./S2/install.log) · [condizioni del run](./S2/run-info.txt)

**Condizioni:** 27/11/2026 13:22-13:32 (~10 min), stessa macchina, stesso JDK, stessa configurazione JMH di S1,
**a batteria, governor `powersave`** come S1 · codice = commit `b59fbc55` + fix H1-H3 (non committati al momento del run)
· sorgenti dei benchmark identici a S1 · copia isolata, stessa procedura e stessi comandi di S1 (sezione "Comandi eseguiti").

Delta = (S2 - S1) / S1: negativo = piu' veloce. Speedup = S1 / S2.
Il confronto e' stato calcolato direttamente dai JSON di S1 e S2 (`primaryMetric.score` / `scoreError`).

### B1 - `PngWriterBenchmark.writePng` (ms/op)

| kind | size | S1 | S2 | delta | speedup |
|---|---|---|---|---|---|
| RGB | 256 | 6.727 ± 0.021 | 6.090 ± 0.042 | -9.5% | x1.10 |
| RGB | 1024 | 112.407 ± 0.460 | 102.394 ± 0.778 | -8.9% | x1.10 |
| ARGB_OPAQUE | 256 | 6.832 ± 0.014 | 6.312 ± 0.042 | -7.6% | x1.08 |
| ARGB_OPAQUE | 1024 | 114.612 ± 0.462 | 105.148 ± 0.509 | -8.3% | x1.09 |
| GRAY_CONTENT | 256 | 2.183 ± 0.005 | 1.987 ± 0.021 | -9.0% | x1.10 |
| GRAY_CONTENT | 1024 | 37.702 ± 0.174 | 34.740 ± 0.384 | -7.9% | x1.09 |

### B2 - `PaletteFactoryBenchmark` (us/op) - **regressione**

| metodo | size | S1 | S2 | delta | speedup |
|---|---|---|---|---|---|
| hasTransparencyFullScan | 256 | 122.251 ± 0.943 | 167.357 ± 1.274 | **+36.9%** | x0.73 |
| hasTransparencyFullScan | 1024 | 1943.551 ± 7.356 | 2720.597 ± 68.585 | **+40.0%** | x0.71 |
| isGrayscaleFullScan | 256 | 122.931 ± 0.733 | 172.873 ± 2.917 | **+40.6%** | x0.71 |
| isGrayscaleFullScan | 1024 | 1904.277 ± 12.357 | 2857.781 ± 137.302 | **+50.1%** | x0.67 |

### B3 - `PngCrcBenchmark` (us/op)

| metodo | length | S1 | S2 | delta |
|---|---|---|---|---|
| pngCrcPerChunk | 1 KiB | 3.102 ± 0.045 | 2.049 ± 0.006 | **-34.0%** |
| pngCrcPerChunk | 64 KiB | 132.500 ± 0.898 | 131.587 ± 0.344 | -0.7% |
| pngCrcPerChunk | 1 MiB | 2092.723 ± 3.852 | 2112.461 ± 6.066 | +0.9% |
| jdkCrc32 (controllo, codice JDK invariato) | 1 KiB | 0.111 ± 0.000 | 0.112 ± 0.001 | +0.6% |
| jdkCrc32 | 64 KiB | 4.146 ± 0.005 | 4.160 ± 0.019 | +0.3% |
| jdkCrc32 | 1 MiB | 65.649 ± 0.621 | 65.608 ± 0.265 | -0.1% |

### B4 - `ConvertToPngBenchmark.convertToPng` (ms/op)

| size | S1 | S2 | delta | speedup |
|---|---|---|---|---|
| 256 | 7.553 ± 0.050 | 6.763 ± 0.053 | -10.4% | x1.12 |
| 1024 | 123.763 ± 0.554 | 112.872 ± 1.039 | -8.8% | x1.10 |

### Analisi S2

**Validita' del confronto.** `jdkCrc32` misura codice del JDK che non e' cambiato tra S1 e S2: resta entro ±0.6%.
La macchina era quindi nelle stesse condizioni nelle due sessioni (anche a batteria) e le differenze sugli altri
benchmark sono attribuibili al codice. Unica nota: in S2 l'errore di B2 a 1024 e' piu' ampio (±68 / ±137 us, 2.5-5%),
comunque molto inferiore alla variazione misurata (+40/+50%).

**Esito per ipotesi**

| Ipotesi | Esito | Evidenza |
|---|---|---|
| H1 buffer preallocato | **confermata** | residuo del guadagno su B1 dopo aver tolto H3 (vedi scomposizione): ~4.6 ms su RGB 1024 |
| H2 `getRGB` per riga | **smentita** | B2 +37% / +50%; peggiora anche il totale di B1 dove la scansione avviene |
| H3 `CRC32` del JDK | **confermata** | B3: `CRC32` ~32x piu' veloce di `PngCrc` sui buffer grandi; tabella statica: -34% su chunk da 1 KiB |

**H2 smentita: perche'.** L'ipotesi era che il costo fosse il numero di chiamate (`getRGB(x, y)` per pixel).
Nel JDK pero' `BufferedImage.getRGB(startX, startY, w, h, ...)` non e' un percorso diverso: per ogni pixel
esegue comunque `raster.getDataElements(x, y, data)` + `colorModel.getRGB(data)`, cioe' le stesse operazioni
della versione singola, piu' la scrittura in `row[]` e la rilettura. Il costo dominante e' quindi la conversione
di colore per pixel, non la chiamata; la versione per pixel, interamente inlineata dal JIT nel chiamante,
risulta piu' veloce della versione bulk. Conclusione: **la versione pre-fix di H2 (S1) e' migliore**.
(Spiegazione dedotta dall'implementazione del JDK, non verificata con un profiler.)

**Scomposizione del guadagno su B1 (1024).** Stime da B2/B3, non misure dirette:

| kind | delta B1 | H3 (CRC) stimato | H2 (regressione) misurata in B2 | H1 = residuo |
|---|---|---|---|---|
| RGB | -10.0 ms | ~-5.4 ms (2.78 MB x 1.94 ns/B) | 0 (nessuna scansione: `TYPE_INT_RGB` senza alpha, `isGrayscale` esce al 1° pixel) | ~-4.6 ms |
| ARGB_OPAQUE | -9.5 ms | ~-5.4 ms | +0.78 ms (`hasTransparency`) | ~-4.9 ms |
| GRAY_CONTENT | -3.0 ms | ~-1.8 ms (0.93 MB) | +0.95 ms (`isGrayscale`) | ~-2.1 ms |

H1 scala con il numero di byte scritti (~1.5 ns/byte: 3.1 MB per RGB, 1.05 MB per grigio): coerente.
Senza la regressione di H2, ARGB_OPAQUE e GRAY_CONTENT avrebbero guadagnato ~0.8-1 ms in piu'.

**Effetto sull'applicazione.** B4 a 1024 guadagna 10.9 ms (-8.8%), praticamente quanto B1 RGB (-10.0 ms):
il miglioramento della libreria arriva intero al backend. Il ~90% rimanente del tempo e' `Deflater` (non toccato).

**B3 nel dettaglio.** `pngCrcPerChunk` migliora solo sui chunk piccoli (-34% a 1 KiB), dove pesava la
ricostruzione della tabella (~1 us); su 64 KiB e 1 MiB il costo e' il loop byte per byte e non cambia.
Il guadagno vero di H3 in `PngWriter` viene dal passaggio a `CRC32` (ora usato da `writeChunk`), che B3 misura
con `jdkCrc32`.

## Stato delle versioni (per il report)

- **S1 / pre-fix:** commit `b59fbc55` ("microbenchmarking: defining baseline, first session S1 of benchmarks").
- **S2 / post-fix H1+H2+H3:** working tree dopo `b59fbc55` (`PngWriter.java`, `PaletteFactory.java`, `PngCrc.java`);
  da committare per conservare la versione misurata in S2.
- H2 resta nel codice di S2 cosi' com'e' (ipotesi smentita, documentata); la versione pre-fix migliore e' nel commit S1.
- **S2 committata:** `d0b82c97` ("microbenchmarking: defined and runned S2 benchmark session").
- **Decisione su S3 (presa dopo S2):** AMBER gira sul codice S2 cosi' com'e', H2 compreso. S3 deve isolare una sola
  variabile (warm-up fisso vs dinamico); togliere H2 prima cambierebbe anche il codice e richiederebbe un'altra
  sessione a warm-up fisso come riferimento. AMBER non giudica la velocita' del codice ma quando fermare il warm-up:
  B2 con la regressione e' un workload valido come gli altri.

## Step successivi (in ordine, da riprendere)

1. ~~**S3 con AMBER sul codice S2**~~ **fatto** (vedi # S3 AMBER) (`d0b82c97`): verifica compatibilita' AMBER (jar esteso su JDK 27, servizio JPT),
   poi esecuzione B1-B4 con `@DynamicHalt`. Domande: quanto warm-up si risparmia/spende rispetto a 5 x 1 s? i punteggi
   restano dentro l'errore di S2? il warm-up fisso era sovradimensionato?
2. **Annullamento di H2, documentato:** `PaletteFactory.java` riportato byte per byte alla versione del commit S1
   (`b59fbc55`), cosi' i numeri B2 di S1 restano validi senza rieseguire. Nel report: "ipotesi smentita dalla misura,
   modifica annullata".
3. **JU2JMH sugli stessi metodi**, eseguito sul codice di S1 e di S2 (stessa config fissa, copia isolata con i tre file
   `src/main` di `b59fbc55`). Domanda: un benchmark generato in automatico dai test avrebbe visto le stesse cose,
   in particolare la regressione H2?
   - `PngWritePredictorTest#testWriteWithPredictor` -> B1 (H1 ramo predictor + H3);
   - `PaletteFactoryTest#testIsGrayscale`, `#testHasTransparency` -> B2 (H2; immagini 10x10, sotto 1 ms);
   - opzionale `PngCrcTest#testCrc` -> B3 (buffer di 4 byte, dominato dalla tabella);
   - escluso `ImageServiceTest` (Mockito, `@TempDir`; il backend e' coperto da B4).
   - Rischi: JU2JMH usa Gradle e qui c'e' solo JDK 27; output in `src/test/java` (package `...benchmarks.ju2jmh`)
     invece di `src/jmh/java`; header di licenza (RAT) e Checkstyle sul codice generato.
4. **Report** `reports/benchmarking.md` (max 5000 caratteri) e link dal README.

---

# S3 AMBER (warm-up dinamico, codice S2)

## Verifica di compatibilita' (2026-11-28)

Repo AMBER: https://github.com/AntonioTrovato/AMBER, commit `dcc5c654be1e87bf78079830b27c6a67eb8b8d25`, clonato nello
scratchpad (fuori dal workspace). Jar `jmh-core-1.37-all.jar` (sha256 `b13f082d...cb88d0d`).

| Punto | Esito |
|---|---|
| Jar su JDK 27 | OK: class file versione 52 (Java 8); stesso warning `sun.misc.Unsafe` di JMH 1.37 |
| Installazione Maven | `mvn install:install-file ... -DartifactId=jmh-core -Dversion=1.37-extended` (come da tutorial) |
| Generatore (BenchmarkList) | **serve il jmh-core esteso anche nel processor path**: il formato della BenchmarkList esteso ha 3 campi in piu' (host/port/model). Con solo `jmh-generator-annprocess:1.37` il processor userebbe il jmh-core vanilla (dipendenza transitiva). Aggiunto `jmh-core:1.37-extended` come primo `<path>` di `annotationProcessorPaths`; verificato che la BenchmarkList generata contiene i 3 campi e che nel classpath c'e' solo il jar esteso |
| Docker | **non installato** su questa macchina. Il servizio JPT e' stato avviato senza container: venv Python 3.9 (come l'immagine `python:3.9-bookworm`) in `~/.cache/amber-jpt/venv`, creato con `uv`, `requirements.txt` del servizio identico tranne `torch==2.0.1+cpu` (niente CUDA) |
| Avvio servizio | `python service.py` va in abort (`random_device could not be read`) per un conflitto tra le librerie native di TF 2.13 e torch 2.0.1 quando tensorflow e' importato per primo (ogni modello si carica bene da solo). Workaround senza toccare il servizio: wrapper che importa `torch` e poi esegue `service.py` con `runpy` |
| Porta | il servizio ascolta su **5001** e `@DynamicHalt` ha default `5001`: il `50001` del tutorial e' un refuso |
| Modelli | prova manuale: serie piatta -> `{"steady":true}`, serie decrescente -> `{"steady":false}` su tutti e 3 gli endpoint. **Bug nel servizio** (`setup.py`): `models["OSCNN"]` e `models["Rocket"]` sono assegnati a `fcn_mdl`, quindi tutti e tre gli endpoint usano di fatto il modello **FCN**. Usiamo `oscnn` come da tutorial ma la decisione la prende FCN |
| Smoke test | `PngCrcBenchmark.jdkCrc32`, length=1024, 1 fork, `-hmodel oscnn`: "Halt eseguito" dopo 107 iterazioni, 0.110 us/op (S2: 0.111). Funziona, ma il fork e' durato 1 min 48 s |

### Differenza tra jar pubblicato e sorgente AMBER (iterazioni da 100 ms)

Nel sorgente (`BaseRunner.runBenchmark`) con dynamic halt attivo il warm-up diventa fino a 500 iterazioni da **100 ms**
(`wp.setTime(100, MILLISECONDS)`); dalla 100a iterazione, a ogni iterazione le ultime 100 medie vanno al servizio; se
risponde `steady` il warm-up si ferma e **le ultime 100 iterazioni di warm-up diventano la misura**; altrimenti seguono
100 iterazioni di misura (tetto 600 iterazioni totali). `@Warmup`/`@Measurement` (conteggi) sono ignorati.

Il jar pubblicato invece **non contiene `setTime`** (verificato con `javap`: assente sia in `BaseRunner` sia in
`IterationParams`), quindi le iterazioni restano della durata di `@Warmup` (1 s): nello smoke test 107 iterazioni =
~108 s, e le richieste al servizio arrivavano a ~1.1 s l'una dall'altra. Con 18 configurazioni x 3 fork S3 durerebbe
da 1.5 h (halt alla 100a) a 9 h (nessun halt).

**Scelta:** riproduciamo il comportamento del sorgente passando `-w 100ms -r 100ms` da riga di comando (il resto della
logica del jar coincide col sorgente: 500 / 100 / 600 verificati nel bytecode). Durata attesa per fork: da 10 s
(halt alla 100a iterazione) a 60 s.

**Conseguenza per il confronto:** con AMBER la finestra minima e' 100 x 100 ms = 10 s per fork prima del primo halt
possibile, pari al nostro warm-up + misura fissi (5 s + 5 s). AMBER quindi qui non puo' risparmiare tempo: puo' solo
costare uguale o di piu'. La domanda utile diventa se i risultati restano coerenti con S2 e quanto spesso il modello
giudica stabile la serie alla prima occasione.

### Annotazione vs riga di comando

Il dynamic halt si attiva con `-hmodel oscnn` (opzione documentata da AMBER, equivalente a `@DynamicHalt(model="oscnn")`).
Le classi di benchmark restano quelle di S1/S2: `@DynamicHalt` esiste solo nel jar esteso, che non e' su Maven Central,
quindi metterla nei sorgenti romperebbe la build normale e la CI. Il pom del workspace non e' stato modificato: le
modifiche (versione `1.37-extended` + processor path) sono solo nella copia isolata (diff in `S3/pom-*.diff`).

## Comandi S3

```bash
# servizio JPT (una volta), fuori dal workspace
git clone https://github.com/AntonioTrovato/AMBER.git        # commit dcc5c654
python3 -m venv ~/.cache/amber-jpt/tools && ~/.cache/amber-jpt/tools/bin/pip install uv
uv venv --python 3.9 ~/.cache/amber-jpt/venv
uv pip install -r requirements-cpu.txt --extra-index-url https://download.pytorch.org/whl/cpu --index-strategy unsafe-best-match
cd AMBER/jpt_service && ~/.cache/amber-jpt/venv/bin/python run_jpt.py    # import torch; runpy.run_path("service.py")
curl localhost:5001/                                                     # {"status":"Service running"}

# jar AMBER in ~/.m2
mvn install:install-file -Dfile=jmh-core-1.37-all.jar -DgroupId=org.openjdk.jmh -DartifactId=jmh-core \
    -Dversion=1.37-extended -Dpackaging=jar

# copia isolata come S1/S2, poi pom con jmh-core 1.37-extended (dipendenza + primo path del processor)
mvn -q install -DskipTests -Djacoco.skip=true
mvn -q -Pbenchmark clean test-compile -Djacoco.skip=true
mvn -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=library.cp
java -cp target/test-classes:target/classes:$(cat library.cp) org.openjdk.jmh.Main \
    -hmodel oscnn -hhost localhost -hport 5001 -w 100ms -r 100ms -rf json -rff library.json org.apache.commons.imaging
# backend: stessi passi in backend/ con it.course.imaging -> backend.json
```

Primo lancio fallito subito (exit 0 ma nessun benchmark, solo l'help di JMH): il tutorial indica `-host`, l'opzione
reale del jar e' **`-hhost`** (`-h` + `ost` veniva interpretato male). Corretto e rilanciato; log del tentativo scartato
non conservati nel repo.

Il profilo `benchmark` non permette di aggiungere argomenti a `org.openjdk.jmh.Main`, quindi in S3 il runner e' lanciato
direttamente con lo stesso classpath di test che usa exec-maven-plugin. Script completo: `S3/run-s3.sh`.

## Risultati S3 (2026-11-28, 12:56-13:07)

Condizioni identiche a S1/S2: batteria (88% -> 82%), governor `powersave`, JDK 27.0.1.1, copia isolata, codice S2
(`d0b82c97`). Unica differenza: `-hmodel oscnn -hhost localhost -hport 5001 -w 100ms -r 100ms` (AMBER).

File: [S3/library.json](S3/library.json), [S3/backend.json](S3/backend.json), [S3/library.log](S3/library.log),
[S3/backend.log](S3/backend.log), [S3/run-info.txt](S3/run-info.txt), [S3/run-s3.sh](S3/run-s3.sh),
[S3/run_jpt.py](S3/run_jpt.py), [S3/requirements-cpu.txt](S3/requirements-cpu.txt), [S3/pom-library.diff](S3/pom-library.diff),
[S3/pom-backend.diff](S3/pom-backend.diff).

Errore = intervallo di confidenza 99.9% di JMH. S1/S2: 3 fork x 5 iterazioni da 1 s (15 campioni); S3: 3 fork x 100
iterazioni da 100 ms (300 campioni). "Iter. totali per fork" = iterazioni eseguite prima dell'halt (minimo 100).

| Benchmark | Param | Unita' | S1 | S2 | S3 (AMBER) | S3 vs S2 | dentro errore S2? | iter. totali per fork | halt |
|---|---|---|---|---|---|---|---|---|---|
| PaletteFactoryBenchmark.hasTransparencyFullScan | size=256 | us/op | 122.251 ± 0.943 | 167.357 ± 1.274 | 169.517 ± 0.423 | +1.3% | no | 101/101/101 | halt |
| PaletteFactoryBenchmark.hasTransparencyFullScan | size=1024 | us/op | 1943.551 ± 7.356 | 2720.597 ± 68.585 | 2697.616 ± 14.385 | -0.8% | si' | 100/101/101 | halt |
| PaletteFactoryBenchmark.isGrayscaleFullScan | size=256 | us/op | 122.931 ± 0.733 | 172.873 ± 2.917 | 178.169 ± 2.064 | +3.1% | no | 101/101/101 | halt |
| PaletteFactoryBenchmark.isGrayscaleFullScan | size=1024 | us/op | 1904.277 ± 12.357 | 2857.781 ± 137.302 | 2771.160 ± 7.205 | -3.0% | si' | 101/101/101 | halt |
| PngWriterBenchmark.writePng | kind=RGB,size=256 | ms/op | 6.727 ± 0.021 | 6.090 ± 0.042 | 6.155 ± 0.025 | +1.1% | si' | 103/102/101 | halt |
| PngWriterBenchmark.writePng | kind=RGB,size=1024 | ms/op | 112.407 ± 0.460 | 102.394 ± 0.778 | 103.822 ± 1.209 | +1.4% | si' | 101/103/101 | halt |
| PngWriterBenchmark.writePng | kind=ARGB_OPAQUE,size=256 | ms/op | 6.832 ± 0.014 | 6.312 ± 0.042 | 6.368 ± 0.029 | +0.9% | si' | 101/101/102 | halt |
| PngWriterBenchmark.writePng | kind=ARGB_OPAQUE,size=1024 | ms/op | 114.612 ± 0.462 | 105.148 ± 0.509 | 107.108 ± 1.262 | +1.9% | no | 101/101/103 | halt |
| PngWriterBenchmark.writePng | kind=GRAY_CONTENT,size=256 | ms/op | 2.183 ± 0.005 | 1.987 ± 0.021 | 1.999 ± 0.014 | +0.6% | si' | 107/105/101 | halt |
| PngWriterBenchmark.writePng | kind=GRAY_CONTENT,size=1024 | ms/op | 37.702 ± 0.174 | 34.740 ± 0.384 | 35.169 ± 0.631 | +1.2% | si' | 101/101/101 | halt |
| PngCrcBenchmark.jdkCrc32 | length=1024 | us/op | 0.111 ± 0.000 | 0.112 ± 0.001 | 0.113 ± 0.000 | +0.7% | si' | 101/100/101 | halt |
| PngCrcBenchmark.jdkCrc32 | length=65536 | us/op | 4.146 ± 0.005 | 4.160 ± 0.019 | 4.194 ± 0.004 | +0.8% | no | 100/100/102 | halt |
| PngCrcBenchmark.jdkCrc32 | length=1048576 | us/op | 65.649 ± 0.621 | 65.608 ± 0.265 | 66.224 ± 0.048 | +0.9% | no | 100/101/100 | halt |
| PngCrcBenchmark.pngCrcPerChunk | length=1024 | us/op | 3.102 ± 0.045 | 2.049 ± 0.006 | 2.069 ± 0.002 | +1.0% | no | 101/101/101 | halt |
| PngCrcBenchmark.pngCrcPerChunk | length=65536 | us/op | 132.500 ± 0.898 | 131.587 ± 0.344 | 132.814 ± 0.142 | +0.9% | no | 104/100/109 | halt |
| PngCrcBenchmark.pngCrcPerChunk | length=1048576 | us/op | 2092.723 ± 3.852 | 2112.461 ± 6.066 | 2131.512 ± 1.682 | +0.9% | no | 101/101/101 | halt |
| ConvertToPngBenchmark.convertToPng | size=256 | ms/op | 7.553 ± 0.050 | 6.763 ± 0.053 | 6.884 ± 0.030 | +1.8% | no | 103/103/102 | halt |
| ConvertToPngBenchmark.convertToPng | size=1024 | ms/op | 123.763 ± 0.554 | 112.872 ± 1.039 | 114.108 ± 1.311 | +1.1% | si' | 101/101/103 | halt |

fork totali: 54; iterazioni totali: 5484; media per fork: 101.6; halt alla 100a: 7

Tabella generata da [S3/compare.py](S3/compare.py) (`python3 S3/compare.py workingNotes/benchmarking`).

## Analisi S3

**1. Halt sempre alla prima occasione utile.** 54 fork su 54 fermati da AMBER; 100-109 iterazioni per fork (media
101.6), mai il tetto di 500. Il modello giudica stabile la serie gia' alla prima finestra di 100 iterazioni: il vincolo
che decide la durata e' la finestra minima, non il modello. Coerente con S1/S2, dove gli errori erano gia' sotto l'1-2%
con 5 x 1 s di warm-up: per questi benchmark il warm-up fisso non era sottodimensionato.

**2. Tempo.** Libreria 8 min 42 s (S2: 8 min 18 s, S1: 8 min 16 s); backend 1 min 11 s (S2: 1 min 03 s). AMBER costa
~5-13% in piu': ~10 s per fork di finestra minima (= 5 s + 5 s di S2) piu' le chiamate HTTP al servizio dalla 100a
iterazione in poi (~90 ms ciascuna) e la latenza tra iterazioni. Nessun risparmio, come previsto dall'analisi del jar.

**3. Warm-up incluso nella misura.** Quando l'halt arriva alla 100a iterazione, "le ultime 100 iterazioni di warm-up"
sono **tutte** le iterazioni del fork, prima compresa: AMBER non scarta nulla. Verificato sui rawData: la prima
iterazione e' piu' lenta (es. GRAY_CONTENT 1024: 49.3 ms contro ~36.6), ma pesa 1/100 sulla media; le prime 10
iterazioni sono entro +-1% delle ultime 10 quasi ovunque (max +3.8% su GRAY_CONTENT 1024). Effetto sulla media: ~0.3%
al piu'. Con iterazioni da 100 ms il JIT e' gia' a regime dopo poche iterazioni.

**4. Punteggi.** S3 e' entro **+-3%** da S2 su tutti i 18 casi, ma con uno scostamento sistematico verso l'alto di
~+1% (16/18 casi tra +0.6% e +1.9%; anche il controllo `jdkCrc32`, codice non modificato, +0.7/+0.9%). In 9/18 casi
gli intervalli non si sovrappongono: gli errori di S3 sono molto stretti (300 campioni), quindi anche differenze
dell'1% risultano "fuori errore". Lo scostamento non e' spiegato dal warm-up incluso (punto 3, <=0.3%); cause possibili,
non separabili con questi dati: iterazioni da 100 ms (piu' overhead di sincronizzazione per iterazione), attivita' del
servizio JPT (TensorFlow) sulla stessa CPU tra un'iterazione e l'altra, stato termico/batteria diverso. Le due celle
B2 1024 (-0.8%, -3.0%) vanno nella direzione opposta, ma li' l'errore di S2 era ampio (+-68, +-137 us).

**5. Le conclusioni di S1 -> S2 non cambiano.** Rispetto a S1, S3 riproduce: H1+H3 su B1 (es. RGB 1024 112.4 -> 103.8
ms, -7.6%), la regressione H2 su B2 (+39-46%), il guadagno di `pngCrcPerChunk` solo a 1 KiB (-33%), il miglioramento
del backend B4 (-8.9% / -7.8%), e `jdkCrc32` invariato. Il warm-up dinamico conferma le misure a warm-up fisso; con
questa configurazione di AMBER il suo valore e' di verifica (la serie e' stabile), non di risparmio.

**Limiti da citare nel report:** (a) jar AMBER pubblicato diverso dal sorgente (iterazioni da 100 ms riprodotte via
CLI); (b) tutti gli endpoint del servizio usano FCN (bug `setup.py`), quindi "oscnn" e' nominale; (c) servizio senza
Docker (venv Python 3.9 + wrapper import order); (d) `-hhost` e porta 5001, non `-host`/50001 come nel tutorial.
