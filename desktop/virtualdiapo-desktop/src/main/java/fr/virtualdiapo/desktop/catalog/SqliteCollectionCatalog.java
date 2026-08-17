package fr.virtualdiapo.desktop.catalog;

import fr.virtualdiapo.core.CollectionCatalog;
import fr.virtualdiapo.core.Slide;
import fr.virtualdiapo.core.SlideCollection;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SqliteCollectionCatalog implements CollectionCatalog {
    private final JdbcTemplate jdbc;

    public SqliteCollectionCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void initializeSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS collections (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    year INTEGER,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS slides (
                    id TEXT PRIMARY KEY,
                    collection_id TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    image_path TEXT NOT NULL,
                    FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
                    UNIQUE (collection_id, position)
                )
                """);
    }

    @Override
    public List<SlideCollection> findAll() {
        Map<UUID, CollectionBuilder> collections = new LinkedHashMap<>();
        jdbc.query("SELECT id, title, description, year FROM collections ORDER BY created_at, rowid", rs -> {
            var id = UUID.fromString(rs.getString("id"));
            collections.put(id, new CollectionBuilder(id, rs.getString("title"),
                    rs.getString("description"), (Integer) rs.getObject("year")));
        });
        if (!collections.isEmpty()) {
            jdbc.query("SELECT id, collection_id, position, image_path FROM slides ORDER BY collection_id, position",
                    rs -> {
                        var owner = collections.get(UUID.fromString(rs.getString("collection_id")));
                        if (owner != null) {
                            owner.slides.add(new Slide(UUID.fromString(rs.getString("id")),
                                    rs.getInt("position"), rs.getString("image_path")));
                        }
                    });
        }
        return collections.values().stream().map(CollectionBuilder::build).toList();
    }

    @Override
    public Optional<SlideCollection> findById(UUID id) {
        return findAll().stream().filter(collection -> collection.id().equals(id)).findFirst();
    }

    @Override
    @Transactional
    public void save(SlideCollection collection) {
        jdbc.update("INSERT INTO collections(id, title, description, year) VALUES (?, ?, ?, ?)",
                collection.id().toString(), collection.title(), collection.description(), collection.year());
        for (var slide : collection.slides()) {
            jdbc.update("INSERT INTO slides(id, collection_id, position, image_path) VALUES (?, ?, ?, ?)",
                    slide.id().toString(), collection.id().toString(), slide.position(), slide.imagePath());
        }
    }

    @Override
    @Transactional
    public void update(SlideCollection collection) {
        int changed = jdbc.update("UPDATE collections SET title = ?, description = ?, year = ? WHERE id = ?",
                collection.title(), collection.description(), collection.year(), collection.id().toString());
        if (changed == 0) {
            throw new IllegalArgumentException("Collection introuvable");
        }
    }

    @Override
    @Transactional
    public boolean deleteById(UUID id) {
        jdbc.update("DELETE FROM slides WHERE collection_id = ?", id.toString());
        return jdbc.update("DELETE FROM collections WHERE id = ?", id.toString()) > 0;
    }

    private static final class CollectionBuilder {
        private final UUID id;
        private final String title;
        private final String description;
        private final Integer year;
        private final List<Slide> slides = new java.util.ArrayList<>();

        private CollectionBuilder(UUID id, String title, String description, Integer year) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.year = year;
        }

        private SlideCollection build() {
            return new SlideCollection(id, title, description, year, slides);
        }
    }
}
