# Lab5 - Formal Verification with JML

Seven classes were **specified with JML** and checked with **OpenJML 21.0.29** through **ESC** (static checks) and **RAC** (runtime checks).

## Static Verification (ESC)

We choosed to verify 7 classes, from the library and the backend:

- **Library**: `SimplePalette`, `Allocator`, `RationalNumber`, `SafeOperations` (`PixelDensity` is RAC only). 
- **Backend**: `ImageAsset`, `ImageService`.

Each warning found in the verification was classified in three ways: 
- **S** spec error (fixed, then re-run).
- **B** code defect (documented, **not fixed**).
- **L** tool/JDK-spec limitation (left to RAC).

| Class | Before fix (valid/invalid) | After fix | Logs |
|---|---|---|---|
| SimplePalette | 7 / 2 | 8 / 1 | [1](./exports/jml-logs/esc-SimplePalette.log), [2](./exports/jml-logs/esc-SimplePalette-v2.log) |
| Allocator | 17 / 8 | 18 / 7 | [1](./exports/jml-logs/esc-Allocator.log), [2](./exports/jml-logs/esc-Allocator-v2.log) |
| RationalNumber | 7 / 8, 1 timeout | 8 / 7, 1 timeout | [1](./exports/jml-logs/esc-RationalNumber.log), [2](./exports/jml-logs/esc-RationalNumber-v2.log) |
| SafeOperations | 1 / 1 | - | [1](./exports/jml-logs/esc-SafeOperations.log) |
| ImageAsset | 15 / 3 | - | [1](./exports/jml-logs/esc-ImageAsset.log) |
| ImageService | 19 / 11, 1 timeout | - | [1](./exports/jml-logs/esc-ImageService.log) |

- **SimplePalette**: 
    - **S** - `getEntry` had no precondition in the `Palette` interface, which now carries the contract. 
    - **L** - the JDK spec of `Objects.requireNonNull` does not declare its `NullPointerException`.
- **Allocator**: 
    - **S** - exception constructors were unspecified (`assignable \everything`), now `pure`. 
    - **B** - `charArray`, `doubleArray`, `floatArray`, `longArray`, `shortArray` never reject a negative size (`PossiblyNegativeSize`). 
    - **L** - `Math.multiplyExact`/`toIntExact` have no spec.
- **RationalNumber**: 
    - **S** - `Number.jml` marks `intValue`/`longValue`/`doubleValue` as `helper`, so the invariant `divisor != 0` is not assumed. Restating it as `requires` fixed `doubleValue`, `code_java_math` fixed the intended int narrowing. 
    - **L** - the divide-by-zero warnings remain. The inherited `Number` spec has no precondition and a subtype cannot strengthen it. 
    - **B** - `negate()` of `Integer.MIN_VALUE` (or of an unsigned value with divisor > 2^31-1) breaks the invariant. `factoryMethod(MIN_VALUE, -1)` overflows its `int` cast.
- **SafeOperations**: 
    - **L** - `Arrays.stream().reduce(Math::addExact)` cannot be linked to `\sum`.
- **ImageAsset**: 
    - **B** (required by JPA) - the no-arg constructor leaves non-null fields `null`.
    - **L** - `Instant.now()` has no spec, so `onCreate`/`onUpdate` may modify every field.
- **ImageService** (~55 min, fixes not re-run, checked with RAC): 
    - **S** - `ImageResponse` timestamps made `nullable`; `UUID.equals` in `ensures` is unprovable, moved to RAC-only clauses (`//+RAC@`). 
    - **L** - unspecified Spring/stream calls, an OpenJML internal error, a timeout on `upload`.
