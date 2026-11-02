package dev.hustletech.loopmarket.app.listing;

import java.util.Set;

// The MVP catalog is deliberately small; even the Empire cannot sell under an invented category.
public final class ListingCategories {
    private static final Set<String> VALUES = Set.of("OTHER", "ELECTRONICS", "HOME", "FASHION", "SPORTS", "BOOKS");
    private ListingCategories() {}
    public static boolean contains(String value) { return value != null && VALUES.contains(value); }
}
