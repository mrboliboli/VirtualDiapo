package fr.virtualdiapo.core;

import java.util.Objects;
import java.util.UUID;

public record Slide(UUID id, int position, String imagePath) {
    public Slide {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(imagePath, "imagePath");
        if (position < 0) {
            throw new IllegalArgumentException("position must be positive or zero");
        }
        if (imagePath.isBlank()) {
            throw new IllegalArgumentException("imagePath must not be blank");
        }
    }
}

