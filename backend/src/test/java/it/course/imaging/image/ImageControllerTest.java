package it.course.imaging.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exercises the controller branches and the error handler mapping without starting the full application.
 */
class ImageControllerTest {

    private ImageService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ImageService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ImageController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void testConvertRejectsNonPngFormat() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/images/{id}/convert", id).param("format", "JPEG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Solo la conversione PNG è supportata"));

        verifyNoInteractions(service);
    }

    @Test
    void testConvertAcceptsLowerCasePng() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] bytes = {(byte) 0x89, 'P', 'N', 'G'};
        when(service.convertToPng(id)).thenReturn(new ImageService.PngImage(bytes, "foto.png"));

        mvc.perform(post("/api/images/{id}/convert", id).param("format", "png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(header().longValue("Content-Length", bytes.length))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment;")));
    }

    @Test
    void testResponseStatusExceptionWithoutReasonUsesDefaultMessage() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/images/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Richiesta non valida"));
    }

    @Test
    void testInvalidUpdateBodyIsReportedAsValidationError() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(patch("/api/images/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \",\"description\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Controlla i dati inseriti"));

        verifyNoInteractions(service);
    }

    @Test
    void testUploadSizeExceededIsMappedToPayloadTooLarge() {
        ResponseEntity<ApiExceptionHandler.ApiError> response =
                new ApiExceptionHandler().handleUploadSize(new MaxUploadSizeExceededException(10));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals("Il file supera il limite di 10 MB", response.getBody().message());
    }
}
