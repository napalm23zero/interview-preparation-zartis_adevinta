package dev.hustletech.loopmarket.app.listing;

import dev.hustletech.loopmarket.shared.domain.GenericEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "listings")
// Stores seller-owned marketplace items; status and version guard publication and concurrent edits.
public class ListingEntity extends GenericEntity {
    @Column(name = "seller_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)") private UUID sellerId;
    @Column(length = 160) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(length = 80) private String category;
    @Column(name = "item_condition", length = 80) private String condition;
    @Column(length = 120) private String city;
    @Column(precision = 12, scale = 2) private BigDecimal price;
    @Column(length = 3) private String currency;
    @Column(nullable = false, length = 16) private String status = "DRAFT";
    @Version @Column(nullable = false) private long version;

    protected ListingEntity() {}
    ListingEntity(UUID sellerId) { this.sellerId = sellerId; }
    void update(ListingService.Input input) {
        title = input.title(); description = input.description(); category = input.category(); condition = input.condition();
        city = input.city(); price = input.price(); currency = input.currency();
    }
    UUID sellerId() { return sellerId; }
    String title() { return title; }
    String description() { return description; }
    String category() { return category; }
    String condition() { return condition; }
    String city() { return city; }
    BigDecimal price() { return price; }
    String currency() { return currency; }
    String status() { return status; }
    long version() { return version; }
    public UUID getSellerId() { return sellerId; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
    void status(String value) { status = value; }
}
