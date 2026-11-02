package dev.hustletech.loopmarket.app.photo;

import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.app.listing.ListingService;
import dev.hustletech.loopmarket.app.person.PersonEntity;
import dev.hustletech.loopmarket.app.person.PersonService;
import dev.hustletech.loopmarket.app.account.AccountEntity;
import dev.hustletech.loopmarket.app.account.AccountService;
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
// Verifies one-photo ownership, pending-to-valid image transitions, and content validation boundaries.
class PhotoPersistenceTest {
    final DAOTestExtension database = DAOTestExtension.newBuilder()
            .addEntityClass(ListingEntity.class).addEntityClass(PhotoEntity.class)
            .addEntityClass(PersonEntity.class).addEntityClass(AccountEntity.class).build();
    private final java.nio.file.Path storage = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "loopmarket-photos-" + UUID.randomUUID());

    @Test void photoIsPendingOwnerScopedAndUniqueForActiveListing() {
        UUID owner = owner();
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
        UUID owner = owner();
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

    @Test void publicPhotoFollowsPublicationAndValidation() throws Exception {
        UUID owner = owner();
        ListingService listings = new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class), Clock.systemUTC(), owner, database.getSessionFactory());
        UUID listing = database.inTransaction(() -> listings.create(new ListingService.Input("Item", null, null, null, null, null, null, null)).getId());
        PhotoService photos = service(owner);
        UUID photo = database.inTransaction(() -> photos.create(new PhotoService.Input(listing, "image/png", 1)).getId());
        PublicPhotoResource publicPhotos = new PublicPhotoResource(database.getSessionFactory(), storage, 20971520);
        assertEquals(404, database.inTransaction(() -> publicPhotos.content(listing).getStatus()));
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes));
        database.inTransaction(() -> photos.upload(photo, new PhotoService.Content(java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()))));
        assertEquals(404, database.inTransaction(() -> publicPhotos.content(listing).getStatus()));
        database.inTransaction(() -> { database.getSessionFactory().getCurrentSession().createMutationQuery("update ListingEntity set status = 'PUBLISHED' where id = :id").setParameter("id", listing).executeUpdate(); return null; });
        var response = database.inTransaction(() -> publicPhotos.content(listing));
        assertEquals(200, response.getStatus());
        assertArrayEquals(bytes.toByteArray(), (byte[]) response.getEntity());
        database.inTransaction(() -> { database.getSessionFactory().getCurrentSession().createMutationQuery("update AccountEntity set deletedAt = :now where id = :id").setParameter("now", java.time.Instant.now()).setParameter("id", owner).executeUpdate(); return null; });
        assertEquals(404, database.inTransaction(() -> publicPhotos.content(listing).getStatus()));
        database.inTransaction(() -> { database.getSessionFactory().getCurrentSession().createMutationQuery("update AccountEntity set deletedAt = null where id = :id").setParameter("id", owner).executeUpdate(); return null; });
        database.inTransaction(() -> { database.getSessionFactory().getCurrentSession().createMutationQuery("update ListingEntity set status = 'ARCHIVED' where id = :id").setParameter("id", listing).executeUpdate(); return null; });
        assertEquals(404, database.inTransaction(() -> publicPhotos.content(listing).getStatus()));
    }

    @Test void publishedPhotoReplacementPreservesOldImageWhenValidationFails() throws Exception {
        UUID owner = owner();
        ListingService listings = new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class), Clock.systemUTC(), owner, database.getSessionFactory());
        UUID listing = database.inTransaction(() -> listings.create(new ListingService.Input("Item", null, null, null, null, null, null, null)).getId());
        PhotoService photos = service(owner);
        UUID photo = database.inTransaction(() -> photos.create(new PhotoService.Input(listing, "image/png", 1)).getId());
        var png = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png));
        database.inTransaction(() -> photos.upload(photo, new PhotoService.Content(java.util.Base64.getEncoder().encodeToString(png.toByteArray()))));
        database.inTransaction(() -> { database.getSessionFactory().getCurrentSession().createMutationQuery("update ListingEntity set status = 'PUBLISHED' where id = :id").setParameter("id", listing).executeUpdate(); return null; });
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> photos.upload(photo, new PhotoService.Content("YWJj"))));
        assertArrayEquals(png.toByteArray(), database.inTransaction(() -> photos.content(photo).orElseThrow().bytes()));
        var jpeg = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", jpeg));
        database.inTransaction(() -> photos.upload(photo, new PhotoService.Content(java.util.Base64.getEncoder().encodeToString(jpeg.toByteArray()))));
        var publicImage = database.inTransaction(() -> new PublicPhotoResource(database.getSessionFactory(), storage, 20971520).content(listing));
        assertEquals(200, publicImage.getStatus());
        assertEquals("image/jpeg", publicImage.getMediaType().toString());
        assertArrayEquals(jpeg.toByteArray(), (byte[]) publicImage.getEntity());
        assertInstanceOf(dev.hustletech.loopmarket.shared.domain.Outcome.NotFound.class,
                database.inTransaction(() -> service(UUID.randomUUID()).upload(photo, new PhotoService.Content("YWJj"))));
    }

    @Test void configuredLimitAppliesToNewUploadsWithoutHidingStoredImages() throws Exception {
        UUID owner = owner();
        ListingService listings = new ListingService(new GenericHibernateRepository<>(database.getSessionFactory(), ListingEntity.class), Clock.systemUTC(), owner, database.getSessionFactory());
        UUID listing = database.inTransaction(() -> listings.create(new ListingService.Input("Item", null, null, null, null, null, null, null)).getId());
        var image = new java.io.ByteArrayOutputStream();
        assertTrue(javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", image));
        long limit = image.size();
        var photos = service(owner, limit);
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> photos.create(new PhotoService.Input(listing, "image/png", limit + 1))));
        UUID photo = database.inTransaction(() -> photos.create(new PhotoService.Input(listing, "image/png", limit)).getId());
        assertThrows(ClientErrorException.class, () -> database.inTransaction(() -> photos.upload(photo,
                new PhotoService.Content(java.util.Base64.getEncoder().encodeToString(new byte[(int) limit + 4])))));
        database.inTransaction(() -> photos.upload(photo, new PhotoService.Content(java.util.Base64.getEncoder().encodeToString(image.toByteArray()))));
        assertArrayEquals(image.toByteArray(), database.inTransaction(() -> service(owner, 1).content(photo).orElseThrow().bytes()));
    }

    private UUID owner() {
        var factory = database.getSessionFactory();
        var people = new PersonService(new GenericHibernateRepository<>(factory, PersonEntity.class), Clock.systemUTC(), factory);
        var accounts = new AccountService(new GenericHibernateRepository<>(factory, AccountEntity.class), Clock.systemUTC(), factory);
        return database.inTransaction(() -> accounts.create(new AccountService.Input(people.create(new PersonService.Input("Seller")).getId(), "issuer", UUID.randomUUID().toString(), "USER")).getId());
    }

    private PhotoService service(UUID owner) { return service(owner, 20971520); }
    private PhotoService service(UUID owner, long limit) {
        return new PhotoService(new GenericHibernateRepository<>(database.getSessionFactory(), PhotoEntity.class,
                (builder, root) -> builder.equal(root.get("listing").get("sellerId"), owner)), Clock.systemUTC(), owner, database.getSessionFactory(), storage, limit);
    }
}
