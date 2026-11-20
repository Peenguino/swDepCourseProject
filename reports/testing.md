# Lab3 - Testing

The testing strategy was designed to **preserve the original Apache Commons Imaging test** suite while **adding** a **dedicated test layer** for the **new Spring Boot backend**.

## Commons Imaging Library Testing

Apache Commons Imaging **already includes** a *comprehensive test suite* covering format parsing, metadata handling, color conversion, compression and round-trip image generation. The **suite was executed without modification** to ensure that the containerized application did not introduce regressions.
- 1,259 tests were executed.
- **JaCoCo coverage** thresholds **inherited** from the Apache Commons parent project were all satisfied.
The library report is generated under `target/site/jacoco`, while raw execution data is stored in `target/jacoco.exec`.

## Backend Unit Testing

A new `ImageServiceTest` class was introduced to test the application’s core service layer. These tests follow the same JUnit 5 style used by Commons Imaging, with explicit assertions and descriptive test method names. Spring and PostgreSQL are not started during these tests: the repository is mocked with Mockito, while uploaded files are generated deterministically in memory and stored in a JUnit temporary directory.

The tests cover the following service behavior:
- Storage initialization
    - `createStorageDirectory()` creates nested storage directories when required.
- Catalog retrieval and data transformation
    - `findAll()` maps `ImageAsset` entities into `ImageResponse` DTOs.
    - `findById()` returns an existing image and produces 404 Not Found for unknown IDs.
- Image upload
    - Valid images are analyzed through Commons Imaging and saved with correct metadata.
    - Titles and descriptions are normalized.
    - Format, MIME type, dimensions and binary content are verified.
    - Empty or unknown files are rejected with 400 Bad Request.
    - Storage failures produce 500 Internal Server Error.
    - If repository persistence fails, the uploaded file is deleted to prevent orphaned data.
- Metadata update and deletion
    - `update()` trims fields and converts a null description into an empty string.
    - `delete()` removes both the database record and the associated file.
- File download
    - `content()` returns the correct filename, MIME type, size and bytes.
    - A missing physical file produces a controlled server error.
- PNG conversion
    - A BMP image is converted to PNG through Commons Imaging.
    - The output filename, format and dimensions are verified.
    - Invalid image data produces 422 Unprocessable Entity.

## FIRST Principles in Testing the Spring Backend Service

The backend tests were designed according to the FIRST principles:
- **Fast**: Mocks and in-memory images avoid network and database overhead.
- **Independent**: Every test uses a fresh repository mock and temporary directory.
- **Repeatable**: Test images are generated programmatically and do not depend on external files.
- **Self-validating**: Results are checked through JUnit assertions.
- **Thorough**: Every service operation is tested on the main both success and error paths, including side effects such as stored bytes, repository calls or cleanup of orphaned files.

## Integration in the CI Workflow

The existing [CI Pipeline workflow](../.github/workflows/ci.yml) was updated to **test both projects** in the **correct dependency order**.

The actions that allows us to build the first library artifact to assure that we are running always all the original tests and then use the library in the backend are these two:

```yaml
    - name: Test and install Commons Imaging Library
      run: mvn -B clean install jacoco:report

    - name: Test and generate coverage on Backend Service
      run: mvn -B -f backend/pom.xml verify
```
