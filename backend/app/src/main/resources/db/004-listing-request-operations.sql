-- Keep each operation in its own retry lane; the Empire dislikes accidental collisions.
RENAME TABLE draft_requests TO listing_requests;
ALTER TABLE listing_requests ADD COLUMN operation VARCHAR(16) NOT NULL DEFAULT 'CREATE';
ALTER TABLE listing_requests DROP INDEX uq_draft_request, ADD CONSTRAINT uq_listing_request UNIQUE (actor_id, operation, request_key);
