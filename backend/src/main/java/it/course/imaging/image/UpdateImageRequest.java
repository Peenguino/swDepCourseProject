package it.course.imaging.image;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateImageRequest(
        @NotBlank @Size(max = 120) String title,
        @Size(max = 1000) String description) {
}
