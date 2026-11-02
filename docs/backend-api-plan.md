# Backend API completion

The `/api/listings` and `/api/photos` CRUD routes are owner-scoped; public discovery uses `/api/catalog`. The product plan and OpenAPI describe this distinction.

## Manual marketplace

- Expose a public catalog and listing detail containing only published, non-deleted listings. Filter by text, category, condition, city and price; paginate with a stable ordering.
- Expose validated photo content only while its listing remains public. Keep owner photo access available for drafts.
- Provide the authenticated user's identity, their listings by status, and their listings' buyer interests. Derive ownership from the session, never from client input.
- Preserve the existing version checks, soft deletion and one-interest rule. Verify publication, replacement and concurrency against actual database constraints.
- Return documented, consistent errors for invalid input, missing authentication, forbidden access, missing resources, conflicts and unavailable dependencies.

## Generation

- Keep generation separate from publication. Provide deterministic suggestions and explicitly identify their source.
- Route local inference through the AI gateway, validate results and limit failures to the generation request. Preserve manual editing and reject stale or conflicting requests.
- Keep external providers in the future backlog unless individually approved.

## Completion evidence

Use two identities to verify login, private drafts, publication, public discovery, buyer interest and seller review. Verify deterministic and local generation, ownership, invalid input, concurrency, dependency failure and revocation with the relevant Docker services. Update OpenAPI and the product plan to reflect implemented behavior.

On 2026-11-02, the Java gateway client reached a local `qwen2.5:0.5b` model through a temporary gateway on the Docker network. The response passed structural validation, but one sampled description added unsupported adjectives; seller review remains required. This check did not exercise the complete authenticated generation workflow or establish model quality.

On 2026-11-02, `MvpJourneyIntegrationTest` passed against a separately started backend, MySQL database and gateway, using the existing development Keycloak, Redis and local model. It covered two-user login, draft isolation, deterministic and local generation, photo upload, publication, catalog and photo reads, concurrent duplicate interest, seller interest visibility and archive. It also checked problem responses for missing authentication and an invalid category. Generation execution replay has since been changed to return the current attempt without a second provider call. An additional test held an InnoDB row lock, committed an archive through SQL and verified that the waiting HTTP interest request returned 404. This does not establish model quality; later tests exercised both archive/interest HTTP orderings. Set `LOOPMARKET_ISOLATED_URL` only to an isolated stack: the test creates records and does not delete them.

Polling an attempt that has remained in `PROCESSING` for at least 60 seconds marks it `UNKNOWN` under a database lock. No retry is automatic; a new attempt requires a new request key. This recovery happens when the individual attempt is read.

Draft creation requires an `Idempotency-Key` header. The seller and key identify a permanently retained record containing a payload fingerprint and the original response, written transactionally with the listing. Concurrent matching requests returned the same original response against isolated MySQL; reusing a key for a different payload returned 409.

Publication also requires an `Idempotency-Key` scoped to the seller and operation. Its original response is stored with the state transition; concurrent retries return that response, including after later archival. A changed listing or version with the same key returns 409. The migration from existing draft request records and concurrent publication were verified against isolated MySQL.

On 2026-11-02, simultaneous version-0 listing edits against isolated MySQL produced one success and one 409. With the isolated gateway stopped, a local generation returned RFC 9457 status 503 with code `GENERATION_OUTCOME_UNKNOWN`, persisted `UNKNOWN`, avoided another provider call and left the draft editable.

On 2026-11-02, isolated MySQL tests held a listing row lock while issuing two HTTP operations one second apart. Interest followed by archive succeeded and preserved seller-visible history; archive followed by interest returned 200 then 404; deletion followed by interest returned 204 then 404. The wait is time-based and does not inspect MySQL lock-wait instrumentation.

Gateway tests cover timeouts, unavailable providers, malformed responses, bounded concurrency and separation of seller facts from system instructions. The 24 authored evaluation cases and review rubric are in `generation-evaluation.md`; the cases were authored after the current prompt and have not established local-model quality.

The seller listing page includes `interestCount`, calculated in one grouped query using the same active-buyer rules as the interest detail. Invalid numeric query parameters return a 400 problem response rather than 404. Both were exercised through the HTTP API; the seller count was also checked with isolated MySQL.

On 2026-11-02, an isolated HTTP test confirmed that logout revoked one Redis-backed session while a second session for the same user remained valid. A temporary NGINX listener accepted a 13,981,032-byte body and returned a JSON 413 for a 15 MiB body. Those requests tested the gateway size boundary, not successful decoding of a maximum-size image. The running development NGINX was not restarted and needs a restart to load the changed template.

On 2026-11-02, draft creation through an isolated NGINX listener preserved the client host and non-default port in the Location header, including on an idempotent retry. The running development NGINX needs a restart to load its changed template.

Generation creation accepts only `DETERMINISTIC` and `LOCAL`; an unsupported mode or deterministic request with no usable draft facts returns 400. Existing historical attempts with older mode values are retained. The live HTTP journey and persistence tests passed after the validation change.

Generation request keys use MySQL `utf8mb4_0900_bin` so case, accents and trailing spaces remain significant. Migration 005 preserves existing data. Matching generation requests return the existing attempt after publication or archive while the listing remains owned and active; a new attempt still requires a draft. These cases passed against isolated MySQL.

An interest remains tied to its original listing. A buyer-scoped PUT with another listing ID returns 409; the existing interest and seller-visible history remain unchanged. Verified through persistence and the isolated MySQL HTTP journey.

The isolated HTTP/MySQL journey verified that a rejected image replacement keeps the original bytes, another user cannot replace it, a valid JPEG replacement becomes the public image, and the image returns 404 after archive. The earlier gateway body-limit test did not decode a maximum-size image.

The user selected a configurable 20 MiB default for photos. `photoMaxBytes` in backend YAML controls decoded bytes, with an accepted range of 1–67,108,864 bytes. The development NGINX reads that same value at startup and accounts for base64/JSON overhead. A valid PNG exceeding 10 MiB uploaded and round-tripped through an isolated NGINX and backend; a smaller configured limit rejected larger uploads while preserving access to existing images. Run `./.docker/server.sh dev start backend nginx` after changing the setting.

The actual NGINX container entrypoint was also exercised in isolation with the shared backend YAML: the default calculated a 28,027,564-byte request limit and accepted the valid large-photo journey. A separate 1 MiB test configuration calculated 1,463,640 bytes and rejected a 2 MiB request with problem JSON and status 413. The production development container was not recreated.

A fresh isolated MySQL 8.4.13 database passed every Liquibase migration and two repeated development-seed runs. The backend then started against that database and passed HTTP tests for login/logout, concurrent draft idempotency and case-sensitive generation keys. Existing development data was not reset.

Logout now returns a 503 problem response if the session store fails during revocation, rather than claiming success. A resource-level test confirmed the session remains usable until a subsequent revocation succeeds.

In an isolated runtime, stopping its dedicated Redis produced RFC 9457 responses with 503 for login and authenticated reads, while the public catalog stayed available (200). After Redis was restored, stopping the isolated MySQL produced 503 for login and authenticated reads and 500 for the public catalog; all failure responses used problem JSON. These checks did not stop the shared development Redis or MySQL.

In the isolated runtime, Redis and MySQL were each stopped and restarted while the backend stayed running. The original session worked again after the Redis restart in that run, and both catalog and authenticated reads recovered after MySQL returned. Redis persistence and session survival are not guaranteed by this single test.

Catalog price filters use the listing price range (0 to 9,999,999,999.99) and at most two significant decimal places. Equivalent decimals are normalized before reaching MySQL. Real HTTP checks against isolated MySQL accepted the endpoints and normalized zero while rejecting out-of-range and fractional-cent filters.

An isolated HTTP test verified that a non-admin cannot list accounts, an administrator update to that user account invalidates its existing opaque session, and a fresh login still has only USER permissions. This uses real MySQL, Redis and the development identity provider.

A valid PNG between 19 MiB and 20 MiB initially exposed Jackson's default 20,000,000-character string limit, which rejected base64 before image validation. The backend now derives Jackson's limit from `photoMaxBytes` while retaining at least its default; the same near-limit image then uploaded and round-tripped through the isolated NGINX successfully.

A valid PNG with an ancillary chunk was constructed at exactly 20,971,520 bytes and successfully uploaded and round-tripped through the isolated NGINX. A replacement of 20,971,521 bytes returned 400 and the original bytes remained intact.

The current generation execution contract was checked with an isolated counting stub: a duplicate while processing returned 202, a later replay returned the completed result, and the provider was called exactly once. After a simulated outage, replay returned the existing UNKNOWN attempt without calling the provider. Audit timestamps are truncated to MySQL microseconds so a completed result and its replay serialize consistently.

The existing development database contained the original schema checksum from before two SQL comments were added. Git history confirmed the schema SQL was otherwise identical. The changelog explicitly accepts that historical checksum; migrations 003–005 then applied successfully without clearing history or resetting data. The updated development backend started and `/hello`, `/docs/`, `/spec/openapi.yaml`, `/catalog` and a seeded login returned 200.

The development NGINX and AI gateway were recreated after authorization. NGINX now starts with the configured photo limit and serves the API; the gateway is healthy in its default deterministic mode. Two local evaluation runs of 21 cases used `qwen2.5:0.5b`; the first exposed an unsupported promise and contradictory-fact handling. A conservative explicit-claims filter was added; the second run returned 19 successes and two invalid-output errors. Human review remains pending. API timestamps now serialize as ISO-8601 UTC strings, except exact replays of response snapshots created before that change, which preserve their original format.

After recreating the development NGINX and gateway, login, an administrator listing with ISO-8601 UTC timestamps, and logout were confirmed through the NGINX endpoint. The remaining local-model quality decision requires human review of the recorded outputs.
