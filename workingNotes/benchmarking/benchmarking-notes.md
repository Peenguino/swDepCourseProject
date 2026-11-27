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
