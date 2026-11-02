package dev.hustletech.loopmarket.app.generation;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import dev.hustletech.loopmarket.shared.domain.GenericRepository;
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

public final class GenerationService extends GenericCrudService<GenerationEntity, GenerationService.Create, GenerationService.Update> {
    public record Create(@NotNull UUID listingId, @NotBlank @Size(max = 128) String requestKey,
                         @NotNull @PositiveOrZero Long inputVersion, @NotBlank @Size(max = 128) String clientRevision,
                         @NotBlank @Size(max = 32) String mode) {}
    public record Update(@NotNull @Pattern(regexp = "PENDING|PROCESSING|FAILED|UNKNOWN") String status) {}
    private final UUID actor;
    private final boolean administrator;
    private final SessionFactory sessions;
    public GenerationService(GenericRepository<GenerationEntity> repository, Clock clock, UUID actor,
                             boolean administrator, SessionFactory sessions) {
        super(repository, clock); this.actor = actor; this.administrator = administrator; this.sessions = sessions;
    }
    @Override public GenerationEntity create(Create input) {
        ListingEntity listing = requireDraft(input.listingId());
        String fingerprint = fingerprint(input);
        GenerationEntity existing = sessions.getCurrentSession().createQuery(
                "from GenerationEntity where actorId = :actor and requestKey = :key", GenerationEntity.class)
                .setParameter("actor", actor).setParameter("key", input.requestKey()).uniqueResult();
        if (existing != null) {
            if (existing.isDeleted() || !existing.fingerprint().equals(fingerprint)) {
                throw new ClientErrorException("Request key conflicts with an existing attempt", 409);
            }
            return existing;
        }
        if (listing.getVersion() != input.inputVersion()) throw new ClientErrorException("Listing version does not match", 409);
        return super.create(input);
    }
    @Override protected GenerationEntity createEntity(Create input) { return new GenerationEntity(input, actor, fingerprint(input)); }
    @Override protected void updateEntity(GenerationEntity entity, Update input) {
        if (!administrator) throw new ForbiddenException();
        requireDraft(entity.listingId());
        entity.status(input.status());
    }
    private ListingEntity requireDraft(UUID id) {
        ListingEntity listing = sessions.getCurrentSession().find(ListingEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (listing == null || listing.isDeleted() || !actor.equals(listing.getSellerId())) throw new ClientErrorException("Listing not found", 404);
        if (!"DRAFT".equals(listing.getStatus())) throw new ClientErrorException("Generation requires a draft", 409);
        return listing;
    }
    private static String fingerprint(Create input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : new String[]{input.listingId().toString(), Long.toString(input.inputVersion()), input.clientRevision(), input.mode()}) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
