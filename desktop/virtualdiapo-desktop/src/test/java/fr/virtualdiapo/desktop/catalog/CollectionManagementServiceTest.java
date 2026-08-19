package fr.virtualdiapo.desktop.catalog;

import fr.virtualdiapo.core.CollectionCatalog;
import fr.virtualdiapo.core.Slide;
import fr.virtualdiapo.core.SlideCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionManagementServiceTest {
    @TempDir
    Path directory;

    @Test
    void reportsLogicalDeletionAsSuccessfulWhenFileCleanupFails() throws Exception {
        var collectionId = UUID.randomUUID();
        var slideId = UUID.randomUUID();
        var collection = new SlideCollection(collectionId, "Test", null, null,
                List.of(new Slide(slideId, 0, "/api/v1/images/" + slideId)));
        var catalog = new InMemoryCatalog(collection);
        var undeletablePath = directory.resolve(slideId + ".jpg");
        Files.createDirectory(undeletablePath);
        Files.writeString(undeletablePath.resolve("child"), "keeps directory non-empty");
        var service = new CollectionManagementService(catalog, directory);

        assertTrue(service.delete(collectionId));
        assertFalse(catalog.findById(collectionId).isPresent());
        assertTrue(Files.exists(undeletablePath));
    }

    private static final class InMemoryCatalog implements CollectionCatalog {
        private SlideCollection collection;

        private InMemoryCatalog(SlideCollection collection) {
            this.collection = collection;
        }

        @Override public List<SlideCollection> findAll() { return collection == null ? List.of() : List.of(collection); }
        @Override public Optional<SlideCollection> findById(UUID id) {
            return collection != null && collection.id().equals(id) ? Optional.of(collection) : Optional.empty();
        }
        @Override public boolean deleteById(UUID id) {
            if (collection == null || !collection.id().equals(id)) return false;
            collection = null;
            return true;
        }
        @Override public void save(SlideCollection ignored) { throw new UnsupportedOperationException(); }
        @Override public void update(SlideCollection ignored) { throw new UnsupportedOperationException(); }
        @Override public void replace(SlideCollection ignored) { throw new UnsupportedOperationException(); }
    }
}
