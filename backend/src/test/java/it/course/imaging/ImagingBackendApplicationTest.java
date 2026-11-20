package it.course.imaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;

import it.course.imaging.image.ImageResponse;
import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.Imaging;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ImagingBackendApplicationTest {

    @TempDir
    static Path storageDir;

    @Autowired
    private TestRestTemplate rest;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage-dir", storageDir::toString);
    }

    @Test
    void exposesCrudContentAndPngConversion() throws Exception {
        BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFF0000);
        byte[] png = Imaging.writeImageToBytes(image, ImageFormats.PNG);

        ByteArrayResource file = new ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "test.png";
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", file);
        form.add("title", "Test");
        form.add("description", "Immagine di prova");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<ImageResponse> created = rest.postForEntity(
                "/api/images", new HttpEntity<>(form, headers), ImageResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().width()).isEqualTo(2);
        assertThat(created.getBody().height()).isEqualTo(3);

        String id = created.getBody().id().toString();
        ResponseEntity<byte[]> content = rest.getForEntity("/api/images/{id}/content", byte[].class, id);
        assertThat(content.getBody()).isEqualTo(png);

        ResponseEntity<ImageResponse> updated = rest.exchange(
                "/api/images/{id}", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("title", "Aggiornata", "description", "")), ImageResponse.class, id);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().title()).isEqualTo("Aggiornata");

        ResponseEntity<byte[]> converted = rest.postForEntity(
                "/api/images/{id}/convert?format=PNG", null, byte[].class, id);
        assertThat(converted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(converted.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/images/{id}", HttpMethod.DELETE, null, Void.class, id);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity("/api/images/{id}", String.class, id).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void healthEndpointIsUp() {
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");
    }
}
