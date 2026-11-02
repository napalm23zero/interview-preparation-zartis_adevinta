package dev.hustletech.loopmarket.shared.adapter.persistence;

import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import dev.hustletech.loopmarket.shared.domain.GenericRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

// Uses the current unit of work; ownership remains the caller's responsibility.
public class GenericHibernateRepository<E extends GenericEntity> implements GenericRepository<E> {
    private final SessionFactory sessions;
    private final Class<E> entityType;

    public GenericHibernateRepository(SessionFactory sessions, Class<E> entityType) {
        this.sessions = Objects.requireNonNull(sessions);
        this.entityType = Objects.requireNonNull(entityType);
    }

    @Override
    public E create(E entity) {
        sessions.getCurrentSession().persist(entity);
        return entity;
    }

    @Override
    public Optional<E> findActive(UUID id) {
        return Optional.ofNullable(sessions.getCurrentSession().get(entityType, id)).filter(entity -> !entity.isDeleted());
    }

    @Override
    public List<E> findAllActive(int offset, int limit) {
        Session session = sessions.getCurrentSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<E> query = builder.createQuery(entityType);
        Root<E> root = query.from(entityType);
        query.select(root).where(builder.isNull(root.get("deletedAt")))
                .orderBy(builder.desc(root.get("createdAt")), builder.asc(root.get("id")));
        return session.createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    @Override
    public long countActive() {
        Session session = sessions.getCurrentSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<E> root = query.from(entityType);
        query.select(builder.count(root)).where(builder.isNull(root.get("deletedAt")));
        return session.createQuery(query).getSingleResult();
    }
}
