package dev.hustletech.loopmarket.app.generation;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.app.listing.ListingCategories;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

// Claim, call, and finish in separate steps; no database lock travels to a galaxy far, far away.
final class GenerationRunner {
    @FunctionalInterface interface Gateway { GatewayClient.Result generate(Map<String, String> facts) throws IOException, InterruptedException; }
    private record Claim(GenerationEntity existing, Map<String, String> facts) {}
    private final SessionFactory sessions;
    private final Gateway gateway;
    private final Clock clock;

    GenerationRunner(SessionFactory sessions, Gateway gateway, Clock clock) {
        this.sessions = sessions; this.gateway = gateway; this.clock = clock;
    }

    GenerationEntity run(UUID id, UUID actor) throws IOException, InterruptedException {
        Claim claim = transaction(session -> {
            GenerationEntity attempt = session.find(GenerationEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (attempt == null || attempt.isDeleted() || !actor.equals(attempt.actorId())) throw new NotFoundException();
            if (!"LOCAL".equals(attempt.mode())) throw new ClientErrorException("Generation is not a local attempt", 409);
            if (!"PENDING".equals(attempt.status())) {
                // A retry reports the current state; it never summons the provider twice.
                if ("PROCESSING".equals(attempt.status()) && !attempt.getUpdatedAt().plusSeconds(60).isAfter(clock.instant())) {
                    attempt.status("UNKNOWN");
                    attempt.touch(clock);
                }
                return new Claim(attempt, null);
            }
            ListingEntity listing = session.find(ListingEntity.class, attempt.listingId(), LockModeType.PESSIMISTIC_READ);
            if (listing == null || listing.isDeleted() || !actor.equals(listing.getSellerId())) throw new NotFoundException();
            if (!"DRAFT".equals(listing.getStatus()) || listing.getVersion() != attempt.inputVersion()) {
                throw new ClientErrorException("Draft version or status changed", 409);
            }
            Object[] values = session.createQuery("select l.title, l.description, l.category from ListingEntity l where l.id = :id", Object[].class)
                    .setParameter("id", attempt.listingId()).getSingleResult();
            Map<String, String> snapshot = new LinkedHashMap<>();
            addFact(snapshot, "title", (String) values[0], 120);
            addFact(snapshot, "description", (String) values[1], 3000);
            if (values[2] != null) {
                if (!ListingCategories.contains((String) values[2])) throw new BadRequestException("Listing category is unsupported");
                snapshot.put("category", (String) values[2]);
            }
            if (snapshot.isEmpty()) throw new BadRequestException("A title or description is required");
            attempt.status("PROCESSING");
            attempt.touch(clock);
            return new Claim(null, Map.copyOf(snapshot));
        });
        if (claim.existing() != null) return claim.existing();
        GatewayClient.Result result;
        try {
            result = gateway.generate(claim.facts());
            if (!"ollama".equals(result.provider())) throw new IOException("Configured provider did not match LOCAL mode");
        } catch (IOException | InterruptedException failure) {
            // Once a provider request may have started, never silently retry an uncertain outcome.
            transaction(session -> { GenerationEntity attempt = session.find(GenerationEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
                if (attempt != null && "PROCESSING".equals(attempt.status())) { attempt.status("UNKNOWN"); attempt.touch(clock); }
                return null; });
            throw failure;
        }
        return transaction(session -> {
            GenerationEntity attempt = session.find(GenerationEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (attempt == null || attempt.isDeleted() || !actor.equals(attempt.actorId()) || !"PROCESSING".equals(attempt.status())) {
                throw new ClientErrorException("Generation is no longer available", 409);
            }
            attempt.completeGateway(result); attempt.touch(clock);
            return attempt;
        });
    }

    private static void addFact(Map<String, String> facts, String name, String value, int limit) {
        if (value == null || value.isBlank()) return;
        String text = value.trim();
        if (text.length() > limit || text.indexOf('<') >= 0 || text.indexOf('>') >= 0) {
            throw new BadRequestException("Draft text is too long or contains markup");
        }
        facts.put(name, text);
    }

    private <T> T transaction(Function<Session, T> work) {
        try (Session session = sessions.openSession()) {
            Transaction tx = session.beginTransaction();
            try { T result = work.apply(session); tx.commit(); return result; }
            catch (RuntimeException exception) { if (tx.isActive()) tx.rollback(); throw exception; }
        }
    }
}
