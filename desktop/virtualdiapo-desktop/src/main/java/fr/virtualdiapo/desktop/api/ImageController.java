package fr.virtualdiapo.desktop.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/images")
public final class ImageController {
    private final Path imageDirectory;

    public ImageController(@Value("${virtualdiapo.image-directory}") Path imageDirectory) {
        this.imageDirectory = imageDirectory.toAbsolutePath().normalize();
    }

    @GetMapping(value = "/{id}.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Resource> findById(@PathVariable("id") UUID id) throws MalformedURLException {
        var image = imageDirectory.resolve(id + ".jpg");
        Resource resource = new UrlResource(image.toUri());
        if (!resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(resource);
    }
}
