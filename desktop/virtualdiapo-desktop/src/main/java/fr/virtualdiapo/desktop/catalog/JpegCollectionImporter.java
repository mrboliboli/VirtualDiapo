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
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.HashSet;

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
        if (images == null) {
            throw new IllegalArgumentException("Sélectionnez au moins une image JPEG");
        }
        return importSources(title, description, year, images.stream()
                .map(image -> new ImageSource(image.getOriginalFilename(), () -> open(image)))
                .toList());
    }

    public SlideCollection importFiles(String title, String description, Integer year,
                                       List<Path> images) throws IOException {
        if (images == null) {
            throw new IllegalArgumentException("Sélectionnez au moins une image JPEG");
        }
        return importSources(title, description, year, images.stream()
                .map(path -> new ImageSource(path.getFileName().toString(), () -> open(path)))
                .toList());
    }

    public Path storedImagePath(UUID slideId) {
        return imageDirectory.resolve(slideId + ".jpg");
    }

    public SlideCollection updateFiles(UUID collectionId, String title, String description, Integer year,
                                       List<Path> images) throws IOException {
        validateTitle(title);
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("Une collection doit contenir au moins une image JPEG");
        }
        var normalized = images.stream().map(path -> path.toAbsolutePath().normalize()).toList();
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new IllegalArgumentException("Une même image ne peut pas apparaître deux fois");
        }
        var current = catalog.findById(collectionId)
                .orElseThrow(() -> new IllegalArgumentException("Collection introuvable"));
        var existingByPath = new HashMap<Path, Slide>();
        current.slides().forEach(slide -> existingByPath.put(storedImagePath(slide.id()), slide));
        Files.createDirectories(imageDirectory);
        var slides = new ArrayList<Slide>();
        var writtenFiles = new ArrayList<Path>();
        try {
            for (int position = 0; position < normalized.size(); position++) {
                var source = normalized.get(position);
                var existing = existingByPath.get(source);
                if (existing != null) {
                    slides.add(new Slide(existing.id(), position, existing.imagePath()));
                    continue;
                }
                var imageSource = new ImageSource(source.getFileName().toString(), () -> open(source));
                validateJpeg(imageSource);
                var slideId = UUID.randomUUID();
                var target = storedImagePath(slideId);
                try (var input = imageSource.open()) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
                writtenFiles.add(target);
                slides.add(new Slide(slideId, position, "/api/v1/images/" + slideId + ".jpg"));
            }
            var updated = new SlideCollection(collectionId, title.trim(), blankToNull(description), year, slides);
            catalog.replace(updated);
            var retained = slides.stream().map(Slide::id).collect(java.util.stream.Collectors.toSet());
            for (var removed : current.slides()) {
                if (!retained.contains(removed.id())) {
                    try { Files.deleteIfExists(storedImagePath(removed.id())); } catch (IOException ignored) { }
                }
            }
            return updated;
        } catch (IOException | RuntimeException failure) {
            for (var file : writtenFiles) {
                try { Files.deleteIfExists(file); } catch (IOException ignored) { failure.addSuppressed(ignored); }
            }
            throw failure;
        }
    }

    private SlideCollection importSources(String title, String description, Integer year,
                                          List<ImageSource> images) throws IOException {
        validateTitle(title);
        if (images.isEmpty()) {
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
                try (InputStream input = image.open()) {
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

    private static void validateJpeg(ImageSource image) throws IOException {
        try (var input = ImageIO.createImageInputStream(image.open())) {
            if (input == null) {
                throw new IllegalArgumentException("Le fichier " + image.name() + " n'est pas une image JPEG");
            }
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext() || !"JPEG".equalsIgnoreCase(readers.next().getFormatName())) {
                throw new IllegalArgumentException("Le fichier " + image.name() + " n'est pas une image JPEG");
            }
        }
    }

    private static InputStream open(MultipartFile image) {
        try {
            if (image == null || image.isEmpty()) {
                throw new IllegalArgumentException("Une image est vide");
            }
            return image.getInputStream();
        } catch (IOException exception) {
            throw new ImageReadException(exception);
        }
    }

    private static InputStream open(Path path) {
        try {
            return Files.newInputStream(path);
        } catch (IOException exception) {
            throw new ImageReadException(exception);
        }
    }

    private record ImageSource(String name, Supplier<InputStream> input) {
        InputStream open() throws IOException {
            try {
                return input.get();
            } catch (ImageReadException exception) {
                throw (IOException) exception.getCause();
            }
        }
    }

    private static final class ImageReadException extends RuntimeException {
        private ImageReadException(IOException cause) {
            super(cause);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Le titre est obligatoire");
        }
        if (title.trim().length() > SlideCollection.MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Le titre est limité à " + SlideCollection.MAX_TITLE_LENGTH + " caractères");
        }
    }
}
