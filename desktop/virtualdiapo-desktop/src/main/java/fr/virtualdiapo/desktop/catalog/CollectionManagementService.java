package fr.virtualdiapo.desktop.catalog;

import fr.virtualdiapo.core.CollectionCatalog;
import fr.virtualdiapo.core.SlideCollection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public final class CollectionManagementService {
    private final CollectionCatalog catalog;
    private final Path imageDirectory;

    public CollectionManagementService(CollectionCatalog catalog,
                                       @Value("${virtualdiapo.image-directory}") Path imageDirectory) {
        this.catalog = catalog;
        this.imageDirectory = imageDirectory.toAbsolutePath().normalize();
    }

    public SlideCollection update(UUID id, String title, String description, Integer year) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Le titre est obligatoire");
        }
        if (title.trim().length() > SlideCollection.MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Le titre est limité à " + SlideCollection.MAX_TITLE_LENGTH + " caractères");
        }
        var current = catalog.findById(id).orElseThrow(() -> new IllegalArgumentException("Collection introuvable"));
        var updated = new SlideCollection(id, title.trim(), blankToNull(description), year, current.slides());
        catalog.update(updated);
        return updated;
    }

    public boolean delete(UUID id) throws IOException {
        var collection = catalog.findById(id);
        if (collection.isEmpty()) {
            return false;
        }
        if (!catalog.deleteById(id)) {
            return false;
        }
        IOException failure = null;
        for (var slide : collection.get().slides()) {
            try {
                Files.deleteIfExists(imageDirectory.resolve(slide.id() + ".jpg"));
            } catch (IOException exception) {
                failure = exception;
            }
        }
        if (failure != null) {
            throw failure;
        }
        return true;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
