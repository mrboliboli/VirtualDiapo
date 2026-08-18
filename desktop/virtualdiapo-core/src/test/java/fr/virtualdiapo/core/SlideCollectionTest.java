package fr.virtualdiapo.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlideCollectionTest {
    @Test
    void sortsSlidesByPositionAndKeepsAnImmutableCopy() {
        var first = new Slide(UUID.randomUUID(), 0, "/first.jpg");
        var second = new Slide(UUID.randomUUID(), 1, "/second.jpg");

        var collection = new SlideCollection(
                UUID.randomUUID(), "Auvergne 2026", null, 2026, List.of(second, first));

        assertEquals(List.of(first, second), collection.slides());
        assertThrows(UnsupportedOperationException.class, () -> collection.slides().add(first));
    }

    @Test
    void titleIsLimitedToFiftyCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new SlideCollection(
                UUID.randomUUID(), "x".repeat(51), null, null, List.of()
        ));
    }
}
