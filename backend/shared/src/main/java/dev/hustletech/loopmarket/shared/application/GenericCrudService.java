package dev.hustletech.loopmarket.shared.application;

import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import dev.hustletech.loopmarket.shared.domain.GenericRepository;
import dev.hustletech.loopmarket.shared.domain.Outcome;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public abstract class GenericCrudService<E extends GenericEntity, C, U> {
    protected final GenericRepository<E> repository;
    private final Clock clock;

    protected GenericCrudService(GenericRepository<E> repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    // Concrete services shape their own entities; the shared workflow keeps order.
    protected abstract E createEntity(C input);
    protected abstract void updateEntity(E entity, U input);

    public E create(C input) {
        E entity = createEntity(input);
        entity.useClock(clock);
        E persisted = repository.create(entity);
        repository.flush();
        return persisted;
    }
    public Outcome<E> find(UUID id) {
        return repository.findActive(id).<Outcome<E>>map(Outcome.Success::new).orElseGet(Outcome.NotFound::new);
    }
    public List<E> list(int offset, int limit) { return repository.findAllActive(offset, limit); }
    public long count() { return repository.countActive(); }
    public Outcome<E> update(UUID id, U input) {
        return repository.findActive(id).<Outcome<E>>map(entity -> {
            updateEntity(entity, input);
            entity.touch(clock);
            repository.flush();
            return new Outcome.Success<>(entity);
        }).orElseGet(Outcome.NotFound::new);
    }
    public Outcome<E> delete(UUID id) {
        return repository.findActive(id).<Outcome<E>>map(entity -> {
            entity.softDelete(clock);
            return new Outcome.Success<>(entity);
        }).orElseGet(Outcome.NotFound::new);
    }
}
