package it.course.imaging;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import it.course.imaging.image.ImageController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts the application through its real {@code main} entry point and verifies that the context comes up.
 */
class ImagingBackendApplicationMainTest {

    private final AtomicReference<ConfigurableApplicationContext> started = new AtomicReference<>();

    @TempDir
    Path storageDir;

    @AfterEach
    void closeContext() {
        ConfigurableApplicationContext context = started.get();
        if (context != null) {
            context.close();
        }
    }

    @Test
    void testMainStartsApplicationContext() {
        SpringApplicationRunListener capture = new SpringApplicationRunListener() {
            @Override
            public void ready(ConfigurableApplicationContext context, Duration timeTaken) {
                started.set(context);
            }
        };

        SpringApplication.withHook(application -> capture, () -> ImagingBackendApplication.main(new String[] {
            "--spring.profiles.active=test",
            "--server.port=0",
            "--spring.datasource.url=jdbc:h2:mem:imaging-main;DB_CLOSE_DELAY=-1",
            "--app.storage-dir=" + storageDir
        }));

        ConfigurableApplicationContext context = started.get();
        assertNotNull(context, "main() must start a Spring context");
        assertTrue(context.isActive());
        assertNotNull(context.getBean(ImageController.class));
    }
}
