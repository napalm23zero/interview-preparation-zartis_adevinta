package dev.hustletech.loopmarket.app.listing;

import io.dropwizard.hibernate.UnitOfWork;
import dev.hustletech.loopmarket.app.account.AccountEntity;
import dev.hustletech.loopmarket.app.person.PersonEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Public discovery has its own boundary, so private drafts cannot escape the Empire's archives.
@Path("/catalog")
@Produces(MediaType.APPLICATION_JSON)
public final class CatalogResource {
    public record View(UUID id, String title, String description, String category, String condition,
                       String city, BigDecimal price, String currency, String sellerDisplayName, Instant createdAt) {}
    public record Page(List<View> content, int page, int size, long total) {}
    private final SessionFactory sessions;

    public CatalogResource(SessionFactory sessions) { this.sessions = sessions; }

    @GET @UnitOfWork
    public Page list(@QueryParam("page") @DefaultValue("1") int page,
                     @QueryParam("size") @DefaultValue("10") int size,
                     @QueryParam("text") String text, @QueryParam("category") String category,
                     @QueryParam("condition") String condition, @QueryParam("city") String city,
                     @QueryParam("minPrice") BigDecimal minPrice, @QueryParam("maxPrice") BigDecimal maxPrice) {
        if (page < 1 || size < 1 || invalidPrice(minPrice) || invalidPrice(maxPrice)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
                || (category != null && !category.isBlank() && !ListingCategories.contains(category))) {
            throw new BadRequestException("Invalid catalog filters or pagination");
        }
        // Normalize equivalent decimals so zero with an extreme scale stays harmless.
        if (minPrice != null) minPrice = minPrice.stripTrailingZeros();
        if (maxPrice != null) maxPrice = maxPrice.stripTrailingZeros();
        size = Math.min(size, 50);
        long offset = (long) (page - 1) * size;
        if (offset > Integer.MAX_VALUE) throw new BadRequestException("Page is out of range");
        Session session = sessions.getCurrentSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<Long> count = builder.createQuery(Long.class);
        Root<ListingEntity> countRoot = count.from(ListingEntity.class);
        Root<AccountEntity> countAccount = count.from(AccountEntity.class);
        Root<PersonEntity> countPerson = count.from(PersonEntity.class);
        count.select(builder.count(countRoot)).where(filters(builder, countRoot, countAccount, countPerson, text, category, condition, city, minPrice, maxPrice));
        long total = session.createQuery(count).getSingleResult();
        if (page > 1 && offset >= total) throw new BadRequestException("Page is out of range");
        CriteriaQuery<Object[]> query = builder.createQuery(Object[].class);
        Root<ListingEntity> root = query.from(ListingEntity.class);
        Root<AccountEntity> account = query.from(AccountEntity.class);
        Root<PersonEntity> person = query.from(PersonEntity.class);
        query.multiselect(root, person.get("displayName")).where(filters(builder, root, account, person, text, category, condition, city, minPrice, maxPrice))
                .orderBy(builder.desc(root.get("createdAt")), builder.asc(root.get("id")));
        return new Page(session.createQuery(query).setFirstResult((int) offset).setMaxResults(size)
                .getResultList().stream().map(row -> view((ListingEntity) row[0], (String) row[1])).toList(), page, size, total);
    }

    @GET @Path("/{id}") @UnitOfWork
    public View find(@PathParam("id") UUID id) {
        List<Object[]> results = sessions.getCurrentSession().createQuery(
                "select l, p.displayName from ListingEntity l, AccountEntity a, PersonEntity p " +
                "where l.id = :id and l.status = 'PUBLISHED' and l.deletedAt is null " +
                "and l.sellerId = a.id and a.deletedAt is null and a.personId = p.id and p.deletedAt is null", Object[].class)
                .setParameter("id", id).setMaxResults(1).getResultList();
        if (results.isEmpty()) throw new NotFoundException();
        return view((ListingEntity) results.get(0)[0], (String) results.get(0)[1]);
    }

    private static boolean invalidPrice(BigDecimal value) {
        if (value == null) return false;
        BigDecimal normalized = value.stripTrailingZeros();
        // Match the listing's decimal precision before a wild exponent reaches MySQL.
        return normalized.signum() < 0 || normalized.scale() > 2
                || (long) normalized.precision() - normalized.scale() > 10;
    }

    private static Predicate[] filters(CriteriaBuilder b, Root<ListingEntity> root, Root<AccountEntity> account,
                                       Root<PersonEntity> person, String text, String category,
                                       String condition, String city, BigDecimal min, BigDecimal max) {
        // Count and content share these predicates; no hidden draft gets counted as public intelligence.
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(b.equal(root.get("status"), "PUBLISHED"));
        predicates.add(b.isNull(root.get("deletedAt")));
        predicates.add(b.equal(root.get("sellerId"), account.get("id")));
        predicates.add(b.isNull(account.get("deletedAt")));
        predicates.add(b.equal(account.get("personId"), person.get("id")));
        predicates.add(b.isNull(person.get("deletedAt")));
        if (text != null && !text.isBlank()) {
            String pattern = "%" + text.toLowerCase(java.util.Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            predicates.add(b.or(b.like(b.lower(root.get("title")), pattern, '!'), b.like(b.lower(root.get("description")), pattern, '!')));
        }
        if (category != null && !category.isBlank()) predicates.add(b.equal(root.get("category"), category));
        if (condition != null && !condition.isBlank()) predicates.add(b.equal(root.get("condition"), condition));
        if (city != null && !city.isBlank()) predicates.add(b.equal(root.get("city"), city));
        if (min != null) predicates.add(b.ge(root.get("price"), min));
        if (max != null) predicates.add(b.le(root.get("price"), max));
        return predicates.toArray(Predicate[]::new);
    }

    private static View view(ListingEntity item, String displayName) {
        return new View(item.getId(), item.title(), item.description(), item.category(), item.condition(),
                item.city(), item.price(), item.currency(), displayName, item.getCreatedAt());
    }
}
