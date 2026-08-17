package fr.virtualdiapo.core;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionCatalog {
    List<SlideCollection> findAll();

    Optional<SlideCollection> findById(UUID id);

    void save(SlideCollection collection);

    void update(SlideCollection collection);

    boolean deleteById(UUID id);
}
