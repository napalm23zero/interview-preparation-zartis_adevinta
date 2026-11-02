-- Request keys are opaque identities; case and trailing spaces do not belong to the same Sith.
ALTER TABLE generation_attempts MODIFY request_key VARCHAR(128)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL;
