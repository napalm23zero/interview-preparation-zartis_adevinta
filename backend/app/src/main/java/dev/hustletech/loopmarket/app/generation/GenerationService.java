package dev.hustletech.loopmarket.app.generation;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.app.listing.ListingCategories;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import dev.hustletech.loopmarket.shared.domain.GenericRepository;
import dev.hustletech.loopmarket.shared.domain.Outcome;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;
import org.hibernate.SessionFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

// Enforces draft ownership, request idempotency, and admin-only status changes for generation records.
public final class GenerationService extends GenericCrudService<GenerationEntity, GenerationService.Create, GenerationService.Update> {
    public record Create(@NotNull UUID listingId, @NotBlank @Size(max = 128) String requestKey,
                         @NotNull @PositiveOrZero Long inputVersion, @NotBlank @Size(max = 128) String clientRevision,
                         @NotBlank @Pattern(regexp = "DETERMINISTIC|LOCAL") String mode) {}
    public record Update(@NotNull @Pattern(regexp = "FAILED|UNKNOWN") String status) {}
    private final Clock clock;
    private final UUID actor;
    private final boolean administrator;
    private final SessionFactory sessions;
    public GenerationService(GenericRepository<GenerationEntity> repository, Clock clock, UUID actor,
                             boolean administrator, SessionFactory sessions) {
        super(repository, clock); this.clock = clock; this.actor = actor; this.administrator = administrator; this.sessions = sessions;
    }
    @Override public GenerationEntity create(Create input) {
        if (!java.util.Set.of("DETERMINISTIC", "LOCAL").contains(input.mode()))
            throw new ClientErrorException("Generation mode is unsupported", 400);
        ListingEntity listing = requireOwnedListing(input.listingId());
        String fingerprint = fingerprint(input);
        // A request key may be retried, but never reused to describe different generation input.
        GenerationEntity existing = sessions.getCurrentSession().createQuery(
                "from GenerationEntity where actorId = :actor and requestKey = :key", GenerationEntity.class)
                .setParameter("actor", actor).setParameter("key", input.requestKey()).uniqueResult();
        if (existing != null) {
            if (existing.isDeleted() || !existing.fingerprint().equals(fingerprint)) {
                throw new ClientErrorException("Request key conflicts with an existing attempt", 409);
            }
            return existing;
        }
        if (!"DRAFT".equals(listing.getStatus())) throw new ClientErrorException("Generation requires a draft", 409);
        if (listing.getVersion() != input.inputVersion()) throw new ClientErrorException("Listing version does not match", 409);
        return super.create(input);
    }
    @Override public Outcome<GenerationEntity> find(UUID id) {
        return repository.findActive(id).<Outcome<GenerationEntity>>map(attempt -> {
            if (isStale(attempt)) {
                // Recheck under lock; a completed transmission must not join the lost fleet.
                sessions.getCurrentSession().refresh(attempt, LockModeType.PESSIMISTIC_WRITE);
                if (isStale(attempt)) {
                    attempt.status("UNKNOWN");
                    attempt.touch(clock);
                    repository.flush();
                }
            }
            return new Outcome.Success<>(attempt);
        }).orElseGet(Outcome.NotFound::new);
    }
    private boolean isStale(GenerationEntity attempt) {
        return "PROCESSING".equals(attempt.status())
                && !attempt.getUpdatedAt().plusSeconds(140).isAfter(clock.instant());
    }
    @Override protected GenerationEntity createEntity(Create input) {
        GenerationEntity attempt = new GenerationEntity(input, actor, fingerprint(input));
        if ("DETERMINISTIC".equals(input.mode())) {
            // Persist the demo result with its request identity; retries never consult a changed draft.
            Object[] fields = sessions.getCurrentSession().createQuery(
                    "select l.title, l.description, l.category from ListingEntity l where l.id = :id", Object[].class)
                    .setParameter("id", input.listingId()).getSingleResult();
            java.util.Map<String, String> suggestions = new java.util.LinkedHashMap<>();
            addDemoField(suggestions, "title", (String) fields[0], 120);
            addDemoField(suggestions, "description", (String) fields[1], 3000);
            if (fields[2] != null) {
                if (!ListingCategories.contains((String) fields[2])) throw new ClientErrorException("Listing category is unsupported", 400);
                suggestions.put("category", (String) fields[2]);
            }
            if (suggestions.isEmpty()) throw new ClientErrorException("Draft has no usable generation input", 400);
            attempt.completeDemo(suggestions);
        }
        return attempt;
    }

    private static void addDemoField(java.util.Map<String, String> output, String name, String value, int limit) {
        if (value == null || value.isBlank()) return;
        String text = value.trim();
        // Keep demo content as plain text, not a tiny HTML side quest for the frontend.
        if (text.indexOf('<') >= 0 || text.indexOf('>') >= 0) throw new ClientErrorException("Suggestions must not contain markup", 400);
        if (text.length() > limit) text = text.substring(0, limit);
        output.put(name, text);
    }
    @Override protected void updateEntity(GenerationEntity entity, Update input) {
        if (!administrator) throw new ForbiddenException();
        sessions.getCurrentSession().refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        if (!"PENDING".equals(entity.status()) || !java.util.Set.of("FAILED", "UNKNOWN").contains(input.status())) {
            throw new ClientErrorException("Generation status cannot be changed", 409);
        }
        requireDraft(entity.listingId());
        entity.status(input.status());
    }
    @Override public dev.hustletech.loopmarket.shared.domain.Outcome<GenerationEntity> delete(UUID id) {
        repository.findActive(id).ifPresent(attempt -> {
            sessions.getCurrentSession().refresh(attempt, LockModeType.PESSIMISTIC_WRITE);
            if ("PROCESSING".equals(attempt.status())) throw new ClientErrorException("Generation is in progress", 409);
        });
        return super.delete(id);
    }
    private ListingEntity requireDraft(UUID id) {
        ListingEntity listing = requireOwnedListing(id);
        if (!"DRAFT".equals(listing.getStatus())) throw new ClientErrorException("Generation requires a draft", 409);
        return listing;
    }
    private ListingEntity requireOwnedListing(UUID id) {
        ListingEntity listing = sessions.getCurrentSession().find(ListingEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (listing == null || listing.isDeleted() || !actor.equals(listing.getSellerId())) throw new ClientErrorException("Listing not found", 404);
        return listing;
    }
    private static String fingerprint(Create input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Length-prefix fields so distinct input tuples cannot collapse to identical concatenated bytes.
            for (String part : new String[]{input.listingId().toString(), Long.toString(input.inputVersion()), input.clientRevision(), input.mode()}) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
