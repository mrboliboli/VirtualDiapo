package fr.virtualdiapo.desktop.api;

import fr.virtualdiapo.core.CollectionCatalog;
import fr.virtualdiapo.core.SlideCollection;
import fr.virtualdiapo.desktop.catalog.JpegCollectionImporter;
import fr.virtualdiapo.desktop.catalog.CollectionManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/collections")
public final class CollectionController {
    private final CollectionCatalog catalog;
    private final JpegCollectionImporter importer;
    private final CollectionManagementService management;

    public CollectionController(CollectionCatalog catalog, JpegCollectionImporter importer,
                                CollectionManagementService management) {
        this.catalog = catalog;
        this.importer = importer;
        this.management = management;
    }

    @GetMapping
    public List<CollectionSummaryResponse> findAll() {
        return catalog.findAll().stream().map(CollectionSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CollectionResponse> findById(@PathVariable("id") UUID id) {
        return catalog.findById(id)
                .map(CollectionResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CollectionResponse> importCollection(
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam("images") List<MultipartFile> images) throws IOException {
        var collection = importer.importCollection(title, description, year, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(CollectionResponse.from(collection));
    }

    @PutMapping("/{id}")
    public CollectionResponse update(@PathVariable("id") UUID id, @RequestBody UpdateCollectionRequest request) {
        return CollectionResponse.from(management.update(id, request.title(), request.description(), request.year()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) throws IOException {
        return management.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    public record CollectionSummaryResponse(UUID id, String title, String description, Integer year,
                                            int slideCount) {
        static CollectionSummaryResponse from(SlideCollection collection) {
            return new CollectionSummaryResponse(collection.id(), collection.title(), collection.description(),
                    collection.year(), collection.slides().size());
        }
    }

    public record CollectionResponse(UUID id, String title, String description, Integer year,
                                     List<SlideResponse> slides) {
        static CollectionResponse from(SlideCollection collection) {
            return new CollectionResponse(collection.id(), collection.title(), collection.description(),
                    collection.year(), collection.slides().stream()
                            .map(slide -> new SlideResponse(slide.id(), slide.position(), slide.imagePath()))
                            .toList());
        }
    }

    public record SlideResponse(UUID id, int position, String imagePath) {}

    public record UpdateCollectionRequest(String title, String description, Integer year) {}
}
