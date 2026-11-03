-- Luke keeps one draft for the seller dashboard; the Force does not publish it for him.
INSERT INTO listings (id, seller_id, title, description, category, item_condition, city, price, currency, status, version, created_at, updated_at, deleted_at)
VALUES (UUID_TO_BIN('01900000-0000-7000-8000-000000000307'), UUID_TO_BIN('01900000-0000-7000-8000-000000000102'), 'X-wing pilot gloves', 'Orange pilot gloves. A little worn after a few flights.', 'FASHION', 'USED', 'Yavin', 30.00, 'CAD', 'DRAFT', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL)
ON DUPLICATE KEY UPDATE id = id;

-- Demo suggestions preserve their request identity and never become a Jedi mind trick on the draft.
INSERT INTO generation_attempts (id, listing_id, actor_id, request_key, request_fingerprint, input_version, client_revision, mode, status, provider, model, prompt_version, suggestions, usage_metadata, created_at, updated_at, deleted_at)
VALUES (UUID_TO_BIN('01900000-0000-7000-8000-000000000701'), UUID_TO_BIN('01900000-0000-7000-8000-000000000307'), UUID_TO_BIN('01900000-0000-7000-8000-000000000102'), 'seed-luke-gloves-1', '04c6860f77fedaa9bf22321dd675518de4dfd472d22a94eb339104f4925cbd3e', 0, 'seed-1', 'DETERMINISTIC', 'COMPLETED', 'deterministic', NULL, NULL, JSON_OBJECT('title', 'X-wing pilot gloves', 'description', 'Orange pilot gloves. A little worn after a few flights.', 'category', 'FASHION'), NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL)
ON DUPLICATE KEY UPDATE id = id;
