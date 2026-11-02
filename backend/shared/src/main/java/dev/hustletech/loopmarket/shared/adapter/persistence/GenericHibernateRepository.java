package dev.hustletech.loopmarket.shared.adapter.persistence;

import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import dev.hustletech.loopmarket.shared.domain.GenericRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

// Uses the current unit of work; ownership remains the caller's responsibility.
public class GenericHibernateRepository<E extends GenericEntity> implements GenericRepository<E> {
    private final SessionFactory sessions;
    private final Class<E> entityType;
    private final BiFunction<CriteriaBuilder, Root<E>, Predicate> scope;

    public GenericHibernateRepository(SessionFactory sessions, Class<E> entityType) {
        this(sessions, entityType, (builder, root) -> builder.conjunction());
    }

    public GenericHibernateRepository(SessionFactory sessions, Class<E> entityType,
                                      BiFunction<CriteriaBuilder, Root<E>, Predicate> scope) {
        this.sessions = Objects.requireNonNull(sessions);
        this.entityType = Objects.requireNonNull(entityType);
        this.scope = Objects.requireNonNull(scope);
    }

    @Override
    public E create(E entity) {
        sessions.getCurrentSession().persist(entity);
        return entity;
    }

    @Override
    public void flush() { sessions.getCurrentSession().flush(); }

    @Override
    public Optional<E> findActive(UUID id) {
        Session session = sessions.getCurrentSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<E> query = builder.createQuery(entityType);
        Root<E> root = query.from(entityType);
        query.select(root).where(builder.equal(root.get("id"), id), builder.isNull(root.get("deletedAt")), scope.apply(builder, root));
        return session.createQuery(query).uniqueResultOptional();
    }

    @Override
    public List<E> findAllActive(int offset, int limit) {
        Session session = sessions.getCurrentSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<E> query = builder.createQuery(entityType);
        Root<E> root = query.from(entityType);
        query.select(root).where(builder.isNull(root.get("deletedAt")), scope.apply(builder, root))
                .orderBy(builder.desc(root.get("createdAt")), builder.asc(root.get("id")));
        return session.createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    @Override
    public long countActive() {
        Session session = sessions.getCurrentSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<E> root = query.from(entityType);
        query.select(builder.count(root)).where(builder.isNull(root.get("deletedAt")), scope.apply(builder, root));
        return session.createQuery(query).getSingleResult();
    }
}
