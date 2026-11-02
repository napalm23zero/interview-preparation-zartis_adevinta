-- Keep retry identities and their original response together, even after the listing changes.
CREATE TABLE draft_requests (
    id BINARY(16) NOT NULL PRIMARY KEY,
    actor_id BINARY(16) NOT NULL,
    request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    listing_id BINARY(16) NOT NULL,
    response_json TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT uq_draft_request UNIQUE (actor_id, request_key),
    CONSTRAINT fk_draft_request_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT fk_draft_request_listing FOREIGN KEY (listing_id) REFERENCES listings(id)
);
