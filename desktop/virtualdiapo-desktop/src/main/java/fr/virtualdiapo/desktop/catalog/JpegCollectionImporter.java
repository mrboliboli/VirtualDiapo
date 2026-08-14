package fr.virtualdiapo.desktop.catalog;

import fr.virtualdiapo.core.CollectionCatalog;
import fr.virtualdiapo.core.Slide;
import fr.virtualdiapo.core.SlideCollection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public final class JpegCollectionImporter {
    private final CollectionCatalog catalog;
    private final Path imageDirectory;

    public JpegCollectionImporter(CollectionCatalog catalog,
                                  @Value("${virtualdiapo.image-directory}") Path imageDirectory) {
        this.catalog = catalog;
        this.imageDirectory = imageDirectory.toAbsolutePath().normalize();
    }

    public SlideCollection importCollection(String title, String description, Integer year,
                                            List<MultipartFile> images) throws IOException {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Le titre est obligatoire");
        }
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins une image JPEG");
        }

        Files.createDirectories(imageDirectory);
        var collectionId = UUID.randomUUID();
        var slides = new ArrayList<Slide>();
        var writtenFiles = new ArrayList<Path>();
        try {
            for (int position = 0; position < images.size(); position++) {
                var image = images.get(position);
                validateJpeg(image);
                var slideId = UUID.randomUUID();
                var target = imageDirectory.resolve(slideId + ".jpg");
                try (InputStream input = image.getInputStream()) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
                writtenFiles.add(target);
                slides.add(new Slide(slideId, position, "/api/v1/images/" + slideId + ".jpg"));
            }
            var collection = new SlideCollection(collectionId, title.trim(), blankToNull(description), year, slides);
            catalog.save(collection);
            return collection;
        } catch (IOException | RuntimeException failure) {
            for (var file : writtenFiles) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    failure.addSuppressed(ignored);
                }
            }
            throw failure;
        }
    }

    private static void validateJpeg(MultipartFile image) throws IOException {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Une image est vide");
        }
        try (var input = ImageIO.createImageInputStream(image.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext() || !"JPEG".equalsIgnoreCase(readers.next().getFormatName())) {
                throw new IllegalArgumentException("Seules les images JPEG sont acceptées");
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
