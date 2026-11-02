package dev.hustletech.loopmarket.app.photo;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.shared.application.GenericCrudService;
import dev.hustletech.loopmarket.shared.domain.GenericRepository;
import dev.hustletech.loopmarket.shared.domain.Outcome;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.ClientErrorException;
import org.hibernate.SessionFactory;

import java.time.Clock;
import java.util.UUID;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
import java.util.Optional;

public final class PhotoService extends GenericCrudService<PhotoEntity, PhotoService.Input, PhotoService.Input> {
    public record Input(@NotNull UUID listingId, @NotNull @Pattern(regexp = "image/(jpeg|png)") String mediaType,
                        @Min(1) @Max(10485760) long byteSize) {}
    public record Content(@NotNull @jakarta.validation.constraints.Size(max = 13981016) String base64) {}
    public record Image(byte[] bytes, String mediaType) {}
    private final UUID owner;
    private final SessionFactory sessions;
    private final Path storage;
    private final Clock clock;
    public PhotoService(GenericRepository<PhotoEntity> repository, Clock clock, UUID owner, SessionFactory sessions, Path storage) {
        super(repository, clock); this.owner = owner; this.sessions = sessions; this.storage = storage; this.clock = clock;
    }
    @Override protected PhotoEntity createEntity(Input input) {
        editableListing(input.listingId());
        Long existing = sessions.getCurrentSession().createQuery(
                "select count(p) from PhotoEntity p where p.listingId = :listing and p.deletedAt is null", Long.class)
                .setParameter("listing", input.listingId()).getSingleResult();
        if (existing > 0) throw new ClientErrorException("Listing already has an active photo", 409);
        return new PhotoEntity(input.listingId(), input.mediaType(), input.byteSize());
    }
    @Override protected void updateEntity(PhotoEntity entity, Input input) {
        if (!entity.listingId().equals(input.listingId())) throw new ClientErrorException("Photo listing cannot be changed", 409);
        editableListing(entity.listingId());
        if (!"PENDING".equals(entity.validationStatus())) throw new ClientErrorException("Validated photos require replacement", 409);
        entity.update(input.mediaType(), input.byteSize());
    }
    @Override public Outcome<PhotoEntity> delete(UUID id) {
        repository.findActive(id).ifPresent(photo -> editableListing(photo.listingId()));
        return super.delete(id);
    }
    public Outcome<PhotoEntity> upload(UUID id, Content content) {
        return repository.findActive(id).<Outcome<PhotoEntity>>map(photo -> {
            editableListing(photo.listingId());
            byte[] bytes;
            try { bytes = Base64.getDecoder().decode(content.base64()); }
            catch (IllegalArgumentException exception) { throw new ClientErrorException("Invalid image encoding", 400); }
            if (bytes.length == 0 || bytes.length > 10485760) throw new ClientErrorException("Invalid image size", 400);
            String actualType = validateImage(bytes);
            if (!actualType.equals(photo.mediaType())) throw new ClientErrorException("Image type does not match metadata", 400);
            String key = UUID.randomUUID().toString();
            try {
                Files.createDirectories(storage);
                Files.write(storage.resolve(key), bytes, StandardOpenOption.CREATE_NEW);
            } catch (IOException exception) { throw new IllegalStateException("Could not store image", exception); }
            photo.uploaded(key, actualType, bytes.length);
            photo.touch(clock);
            repository.flush();
            return new Outcome.Success<>(photo);
        }).orElseGet(Outcome.NotFound::new);
    }

    public Optional<Image> content(UUID id) {
        return repository.findActive(id).filter(photo -> "VALID".equals(photo.validationStatus())).map(photo -> {
            String key = photo.storageKey();
            try {
                if (!UUID.fromString(key).toString().equals(key)) throw new IllegalStateException("Invalid stored image key");
                Path file = storage.resolve(key);
                if (Files.size(file) > 10485760) throw new IllegalStateException("Stored image exceeds the size limit");
                return new Image(Files.readAllBytes(file), photo.mediaType());
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("Could not read stored image", exception);
            }
        });
    }

    private static String validateImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ClientErrorException("Unsupported image", 400);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 16000000) throw new ClientErrorException("Image dimensions exceed the limit", 400);
                String format = reader.getFormatName();
                String type = "png".equalsIgnoreCase(format) ? "image/png" : "jpeg".equalsIgnoreCase(format) ? "image/jpeg" : "";
                if (type.isEmpty() || reader.read(0) == null) throw new ClientErrorException("Invalid image", 400);
                return type;
            } finally { reader.dispose(); }
        } catch (IOException exception) { throw new ClientErrorException("Invalid image", 400); }
    }
    private void editableListing(UUID id) {
        ListingEntity listing = sessions.getCurrentSession().find(ListingEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (listing == null || listing.isDeleted() || !owner.equals(listing.getSellerId())) throw new ClientErrorException("Listing not found", 404);
        if (!"DRAFT".equals(listing.getStatus())) throw new ClientErrorException("Only draft photos can be changed", 409);
    }
}
