-- Stable development IDs keep local accounts aligned across database and provider resets.
INSERT INTO persons (id, display_name, created_at, updated_at, deleted_at)
VALUES
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000001'), 'Darth Vader', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL),
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000002'), 'Luke Skywalker', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL)
ON DUPLICATE KEY UPDATE updated_at = IF(deleted_at IS NOT NULL, UTC_TIMESTAMP(6), updated_at), deleted_at = NULL;

UPDATE users
SET updated_at = IF(NOT (person_id = UUID_TO_BIN('01900000-0000-7000-8000-000000000001')
    AND issuer = '${identityIssuer}' AND external_subject = '01900000-0000-7000-8000-000000000201'
    AND account_role = 'ADMIN' AND deleted_at IS NULL), UTC_TIMESTAMP(6), updated_at),
    auth_version = auth_version + IF(NOT (person_id = UUID_TO_BIN('01900000-0000-7000-8000-000000000001')
    AND issuer = '${identityIssuer}' AND external_subject = '01900000-0000-7000-8000-000000000201'
    AND account_role = 'ADMIN' AND deleted_at IS NULL), 1, 0),
    person_id = UUID_TO_BIN('01900000-0000-7000-8000-000000000001'), issuer = '${identityIssuer}',
    external_subject = '01900000-0000-7000-8000-000000000201', account_role = 'ADMIN', deleted_at = NULL
WHERE id = UUID_TO_BIN('01900000-0000-7000-8000-000000000101');

UPDATE users
SET updated_at = IF(NOT (person_id = UUID_TO_BIN('01900000-0000-7000-8000-000000000002')
    AND issuer = '${identityIssuer}' AND external_subject = '01900000-0000-7000-8000-000000000202'
    AND account_role = 'USER' AND deleted_at IS NULL), UTC_TIMESTAMP(6), updated_at),
    auth_version = auth_version + IF(NOT (person_id = UUID_TO_BIN('01900000-0000-7000-8000-000000000002')
    AND issuer = '${identityIssuer}' AND external_subject = '01900000-0000-7000-8000-000000000202'
    AND account_role = 'USER' AND deleted_at IS NULL), 1, 0),
    person_id = UUID_TO_BIN('01900000-0000-7000-8000-000000000002'), issuer = '${identityIssuer}',
    external_subject = '01900000-0000-7000-8000-000000000202', account_role = 'USER', deleted_at = NULL
WHERE id = UUID_TO_BIN('01900000-0000-7000-8000-000000000102');

INSERT INTO users (id, person_id, issuer, external_subject, account_role, created_at, updated_at, deleted_at)
VALUES
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000101'), UUID_TO_BIN('01900000-0000-7000-8000-000000000001'), '${identityIssuer}', '01900000-0000-7000-8000-000000000201', 'ADMIN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL),
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000102'), UUID_TO_BIN('01900000-0000-7000-8000-000000000002'), '${identityIssuer}', '01900000-0000-7000-8000-000000000202', 'USER', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL)
ON DUPLICATE KEY UPDATE id = IF(id = VALUES(id), id, NULL),
    updated_at = IF(deleted_at IS NOT NULL, UTC_TIMESTAMP(6), updated_at), deleted_at = NULL;
