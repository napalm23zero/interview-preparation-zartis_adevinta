package dev.hustletech.loopmarket.app.photo;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.app.listing.ListingService;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import io.dropwizard.testing.junit5.DAOTestExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import jakarta.ws.rs.ClientErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.time.Clock;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
class PhotoPersistenceTest {
    final DAOTestExtension database = DAOTestExtension.newBuilder()
            .addEntityClass(ListingEntity.class).addEntityClass(PhotoEntity.class).build();
    private final java.nio.file.Path storage = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "loopmarket-photos-" + UUID.randomUUID());

    @Test void photoIsPendingOwnerScopedAndUniqueForActiveListing() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        ListingService listings = new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class), Clock.systemUTC(), owner, database.getSessionFactory());
        UUID listing = database.inTransaction(() -> listings.create(new ListingService.Input("Item", null, null, null, null, null, null, null)).getId());
        PhotoService photos = service(owner);
        PhotoService.Input input = new PhotoService.Input(listing, "image/png", 100);
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> service(stranger).create(input)));
        PhotoEntity photo = database.inTransaction(() -> photos.create(input));
        assertEquals("PENDING", photo.validationStatus());
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> photos.create(input)));
        database.inTransaction(() -> { assertEquals(0, service(stranger).count()); return null; });
    }

    @Test void rejectsInvalidImageAndMarksValidatedImage() throws Exception {
        UUID owner = UUID.randomUUID();
        ListingService listings = new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class), Clock.systemUTC(), owner, database.getSessionFactory());
        UUID listing = database.inTransaction(() -> listings.create(new ListingService.Input("Item", null, null, null, null, null, null, null)).getId());
        PhotoService photos = service(owner);
        UUID photo = database.inTransaction(() -> photos.create(new PhotoService.Input(listing, "image/png", 1)).getId());
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> photos.upload(photo, new PhotoService.Content("YWJj"))));
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes));
        String image = java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        String status = database.inTransaction(() -> photos.upload(photo, new PhotoService.Content(image))
                .fold(PhotoEntity::validationStatus, () -> "MISSING"));
        assertEquals("VALID", status);
        byte[] saved = database.inTransaction(() -> photos.content(photo).orElseThrow().bytes());
        assertArrayEquals(bytes.toByteArray(), saved);
        try (var files = java.nio.file.Files.list(storage)) { assertEquals(1, files.count()); }
    }

    private PhotoService service(UUID owner) {
        return new PhotoService(new GenericHibernateRepository<>(database.getSessionFactory(), PhotoEntity.class,
                (builder, root) -> builder.equal(root.get("listing").get("sellerId"), owner)), Clock.systemUTC(), owner, database.getSessionFactory(), storage);
    }
}
