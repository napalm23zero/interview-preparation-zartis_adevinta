CREATE TABLE persons (
    id BINARY(16) NOT NULL PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL
);

CREATE TABLE users (
    id BINARY(16) NOT NULL PRIMARY KEY,
    person_id BINARY(16) NOT NULL,
    issuer VARCHAR(255) NOT NULL,
    external_subject VARCHAR(255) NOT NULL,
    account_role VARCHAR(16) NOT NULL DEFAULT 'USER',
    auth_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_users_person FOREIGN KEY (person_id) REFERENCES persons(id),
    CONSTRAINT uq_users_person UNIQUE (person_id),
    CONSTRAINT uq_users_identity UNIQUE (issuer, external_subject),
    CONSTRAINT ck_users_role CHECK (account_role IN ('USER', 'ADMIN'))
);

CREATE TABLE listings (
    id BINARY(16) NOT NULL PRIMARY KEY,
    seller_id BINARY(16) NOT NULL,
    title VARCHAR(160) NULL,
    description TEXT NULL,
    category VARCHAR(80) NULL,
    item_condition VARCHAR(80) NULL,
    city VARCHAR(120) NULL,
    price DECIMAL(12, 2) NULL,
    currency CHAR(3) NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_listings_seller FOREIGN KEY (seller_id) REFERENCES users(id),
    CONSTRAINT ck_listing_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_listing_price CHECK (price IS NULL OR price >= 0)
);

CREATE TABLE listing_photos (
    id BINARY(16) NOT NULL PRIMARY KEY,
    listing_id BINARY(16) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    validation_status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    active_listing_id BINARY(16) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN listing_id ELSE NULL END) STORED,
    CONSTRAINT fk_photos_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT uq_photos_active_listing UNIQUE (active_listing_id),
    CONSTRAINT ck_photos_byte_size CHECK (byte_size > 0)
);

CREATE TABLE interests (
    id BINARY(16) NOT NULL PRIMARY KEY,
    listing_id BINARY(16) NOT NULL,
    buyer_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_interests_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT fk_interests_buyer FOREIGN KEY (buyer_id) REFERENCES users(id),
    CONSTRAINT uq_interests_listing_buyer UNIQUE (listing_id, buyer_id)
);

CREATE TABLE generation_attempts (
    id BINARY(16) NOT NULL PRIMARY KEY,
    listing_id BINARY(16) NOT NULL,
    actor_id BINARY(16) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    input_version BIGINT NOT NULL,
    client_revision VARCHAR(128) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider VARCHAR(120) NULL,
    model VARCHAR(120) NULL,
    prompt_version VARCHAR(80) NULL,
    suggestions JSON NULL,
    usage_metadata JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_generations_listing FOREIGN KEY (listing_id) REFERENCES listings(id),
    CONSTRAINT fk_generations_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT uq_generations_actor_key UNIQUE (actor_id, request_key)
);
