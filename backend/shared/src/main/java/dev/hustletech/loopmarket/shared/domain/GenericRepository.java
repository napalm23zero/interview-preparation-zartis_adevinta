package dev.hustletech.loopmarket.shared.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// This port defines shared persistence operations; adapters keep soft-deleted rows out of active reads.
public interface GenericRepository<E extends GenericEntity> {
    E create(E entity);
    Optional<E> findActive(UUID id);
    List<E> findAllActive(int offset, int limit);
    long countActive();
    default void flush() {}
}
