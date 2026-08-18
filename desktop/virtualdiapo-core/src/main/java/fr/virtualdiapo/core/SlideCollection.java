package fr.virtualdiapo.core;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SlideCollection(
        UUID id,
        String title,
        String description,
        Integer year,
        List<Slide> slides
) {
    public static final int MAX_TITLE_LENGTH = 50;

    public SlideCollection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(slides, "slides");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title must not exceed " + MAX_TITLE_LENGTH + " characters");
        }
        slides = slides.stream()
                .sorted(Comparator.comparingInt(Slide::position))
                .toList();
    }
}
