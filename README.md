<h1 align="center">LoopMarket</h1>

<p align="center">
  <strong>A marketplace for second chances, built with an agentic mindset.</strong>
</p>

<p align="center">
  <img alt="Status: in development" src="https://img.shields.io/badge/status-in%20development-00c853?style=for-the-badge&amp;labelColor=101820">
  <img alt="Backend: Java and Dropwizard" src="https://img.shields.io/badge/backend-Java%20%2B%20Dropwizard-f89820?style=for-the-badge&amp;labelColor=101820">
  <img alt="Infrastructure: Docker Compose" src="https://img.shields.io/badge/infra-Docker%20Compose-2496ed?style=for-the-badge&amp;labelColor=101820">
  <img alt="AI: local models and external providers planned" src="https://img.shields.io/badge/AI-local%20models%20%2B%20external%20planned-9c6ade?style=for-the-badge&amp;labelColor=101820">
</p>

LoopMarket is a second-hand marketplace where sellers create listings, optionally request AI suggestions, and approve publication. Buyers can browse published listings and express interest; interest is a stored signal, not a messaging or payment system.

**Status:** The React web and Flutter mobile clients, backend and development authentication are implemented. External AI providers and production deployment are planned.  

<p align="center">
  <img src="docs/img/001 - web.png" 
alt="Description">
</p>
<p align="center">
  <img src="docs/img/002 - mobile.png" 
alt="Description">
</p>

## Run locally

Requires Docker and Docker Compose 2.20 or later. From the repository root:

```sh
./.docker/server.sh dev start backend frontend nginx  # Starts the web app, API and ingress
./.docker/server.sh dev list                # Available services
./.docker/server.sh dev status              # State and memory usage
./.docker/server.sh dev logs backend redis  # Follow selected logs
./.docker/server.sh dev stop backend        # Stop without deleting data
```

The first run creates `.env-dev` from `.env-dev.example` and may download images and build dependencies. `dev` is configured; `qat`, `uat` and `prd` are reserved. With default ports, open [LoopMarket](http://localhost:8088/) or [Swagger UI](http://localhost:8080/api/docs/) or the [hello endpoint](http://localhost:8080/api/hello).

The `dev` migration context seeds four Star Wars characters, six fictional published listings with bundled illustrative images, a draft with demo suggestions, and sample interests. Luke sells a training saber; Vader is among the interested buyers. These records are created once and do not overwrite listing edits. Development accounts are for local use only:

| Username | Password | Role |
|---|---|---|
| `darth.vader` | `dark-side-1977` | Admin |
| `luke.skywalker` | `force-1977` | User |
| `leia.organa` | `rebel-1977` | User |
| `han.solo` | `falcon-1977` | User |

Development identity provisioning runs in the background; the accounts may take up to a minute to become available after startup. Their demo passwords are restored automatically.

Use **POST /sessions** in Swagger to obtain an `accessToken`, then select **Authorize** and enter the token. The backend issues opaque, 30-minute sessions, checks Redis and the local account on each protected request, and revokes a session on logout. Keycloak verifies the development credentials. These credentials and the development login flow are not a production identity setup.

`photoMaxBytes` in `backend/config.yml` controls the decoded image limit: **20 MiB by default**, configurable from 1 byte to 64 MiB. The development NGINX reads the same setting and accounts for base64 overhead. After a change, run `./.docker/server.sh dev start backend nginx`.

## Database migrations

Liquibase runs ordered, versioned changelogs when the backend starts. Existing migration identities and checksums are preserved; add a new changeset for each future change instead of editing an applied migration. Demo data and images are loaded only with the `dev` migration context.

Production deployment is not configured yet. Before deploying, omit the `dev` context, disable demo identity provisioning, provide production services and credentials, back up the database, and review migrations. Migrations are forward-only. See the [migration guide](docs/database-migrations.md) for details.

## Web application

The React and TypeScript client follows the LoopMarket design reference. Buyers can search, filter and browse listings, then sign in to express interest. Sellers can create and edit drafts, upload up to five photos, review local-model suggestions, publish, and manage listings and interest from their dashboard. Publication always requires confirmation. Interest is a stored signal; messaging and payments are not implemented.

The browser uses the same-origin `/api` gateway. The development session is kept in browser session storage and checked by the backend; this local identity flow is not a production authentication design. The frontend dev container installs the locked dependencies on startup. For host development, use `cd frontend && npm ci && npm run dev`; the default API proxy targets `localhost:8080`.

Run `npm test` and `npm run build` in `frontend/`. An opt-in integration test exercises the actual local gateway and creates a published test listing and buyer interest in the development database: `LOOPMARKET_LIVE_TEST=1 npm test`. It requires the backend, frontend, NGINX and demo identities to be running.

## Mobile application

The Flutter client provides the same marketplace essentials: catalog browsing, sign-in, buyer interest, seller listings, photo uploads and reviewed AI suggestions. Sessions remain in memory. Run the development preview with `./.docker/server.sh dev start backend mobile` and open [localhost:3001](http://localhost:3001). Native Android and iOS setup, device API configuration and checks are in the [mobile guide](mobile/README.md).

## API and design decisions

- **Clear boundaries:** `/api/catalog` exposes published listings and photos; `/api/users/me/listings` and the CRUD endpoints are scoped to the authenticated owner. Seller views include interest counts and buyer display names without exposing contact information.
- **Safe writes:** draft creation and publication use an `Idempotency-Key`; listing edits and state changes use an expected version. MySQL constraints and transactional locks protect duplicate interests and the archive/interest race.
- **Review before publication:** deterministic suggestions are labelled as demo output. Local inference uses the configured gateway and never edits or publishes a listing. The seller reviews every suggestion; malformed output and uncertain provider outcomes leave manual editing available.
- **Auditable data:** application tables use UUIDv7 IDs and UTC creation, update and soft-deletion timestamps. Liquibase manages schema changes; Redis stores temporary sessions rather than marketplace data.
- **Predictable errors:** API errors use `application/problem+json`; validation responses can include field-level details. The frontend can use the attempt status and `GENERATION_OUTCOME_UNKNOWN` to handle an interrupted model call.

Swagger documents the request schemas and examples. See the [frontend integration guide](docs/frontend-api.md) for the complete API flow, [database model](docs/database-model.md) for fields and constraints, and [migration guide](docs/database-migrations.md) for changelogs and demo-data isolation.

## Generic CRUD strategy

The `backend/shared` module centralizes CRUD behavior shared by multiple resources while keeping entity-specific rules in their own services and resources:

| Layer | Responsibility |
|---|---|
| `GenericEntity` | UUIDv7 identity, UTC audit timestamps and soft deletion. |
| `GenericRepository` and Hibernate adapter | Persistence operations, active-record filtering and caller-specific scopes. |
| `GenericCrudService` | Common create, read, list, update and soft-delete workflows; concrete services map inputs and enforce domain rules. |
| `GenericCrudResource` | HTTP endpoints, pagination, request validation and response mapping; concrete resources determine authorization and safe views. |

This removes repeated endpoint and persistence plumbing while keeping rules visible: for example, listing publication checks ownership, version and validated photo; interest creation checks listing visibility and buyer identity. Those operations have explicit domain methods and database boundaries rather than being reduced to generic CRUD.

```mermaid
flowchart LR
    Client --> Resource[Resource: authorization and response view]
    Resource --> Service[Service: domain rules]
    Service --> CRUD[Shared CRUD workflow]
    CRUD --> Port[Repository port]
    Port --> Adapter[Hibernate adapter]
    Adapter --> MySQL[(MySQL)]
```

## Services and structure

Each service has a Dockerfile and Compose definition, and containers share the `zartis` network. Backend Maven modules are shown separately to make their responsibilities clear.

```text
LoopMarket
├── backend
│   ├── app                    (Dropwizard API, marketplace workflows and migrations)
│   ├── shared                 (generic CRUD, persistence port and common entity)
│   ├── platform/identity      (identity and Redis session components)
│   └── product/marketplace    (initial marketplace domain and greeting)
├── frontend                   (React, TypeScript and Vite marketplace)
├── mobile                     (Flutter marketplace for Android and iOS)
├── ai
│   ├── ai-gateway             (deterministic and Ollama routing)
│   └── ollama                 (optional containerized model runtime)
└── infrastructure
    ├── nginx                  (application ingress)
    ├── mysql / redis          (marketplace data / sessions)
    ├── keycloak               (development identity)
    ├── qdrant                 (vector database; retrieval not integrated)
    ├── elasticsearch / kibana / elastic-agent  (observability)
    └── sonarqube / postgresql (code quality and its database)
```

```mermaid
flowchart LR
    Browser[Browser] --> NGINX[NGINX]
    Mobile[Flutter mobile] --> NGINX
    NGINX --> Web[React web app]
    NGINX --> API[Dropwizard API]
    API --> Identity[Keycloak: dev login]
    API --> Redis[(Redis sessions)]
    API --> MySQL[(MySQL)]
    API --> Photos[(Photo volume)]
    API --> Gateway[AI gateway]
    Gateway --> Ollama[Ollama on host]
    Gateway -. future .-> Providers[External providers]
    Gateway -. future .-> Qdrant[(Qdrant)]
```

## AI configuration

For local suggestions from a photo, run Ollama on the host and set `AI_GATEWAY_MODE=ollama`, `OLLAMA_MODEL` to an installed vision-capable model, and `OLLAMA_URL=http://host.docker.internal:11434` in `.env-dev`. The model can suggest a title, description, category, condition and an estimated price in CAD. Review its output: photos cannot reliably establish condition or market value. City remains seller-provided. On Docker Desktop, the containerized gateway reaches the host through that address, so native Ollama can use the Mac's GPU. Restart the gateway after changing the configuration:

```sh
ollama list
./.docker/server.sh dev start ai-gateway
```

If Docker cannot resolve `host.docker.internal`, set `OLLAMA_URL` to the host address reachable from the gateway container. The optional Ollama container publishes port `11435` by default to avoid conflicting with the host's port `11434`; it is not required for host inference. Set `AI_GATEWAY_MODE=deterministic` for suggestions without a model.

Local inference was evaluated on 21 cases; output may omit or invent details, and human review remains pending. External provider adapters are future work. See [generation evaluation](docs/generation-evaluation.md).

## Verification

With the backend container running, run the Java suite and Compose/resource checks:

```sh
docker compose --project-directory "$PWD" --env-file .env-dev -f .docker/docker-compose.yaml \
  --profile apps --profile mobile exec backend mvn -B -ntp test
make check
```

The isolated two-user API journey has been exercised with MySQL, Redis and Keycloak, including publication, discovery, interest, concurrent requests, session revocation and failure recovery. Local Ollama inference and the 20 MiB photo boundary have also been exercised. Integration tests are opt-in and create records; their scope and results are described in [backend verification](docs/backend-api-plan.md).

Old image files and files left by a failed database transaction are retained; automatic orphan cleanup is not implemented.

## Data model

The MVP model uses UUIDv7 IDs and UTC creation, update, and soft-deletion timestamps on every table. The diagrams show relationship keys; see [the database model](docs/database-model.md) for all fields and rules. MySQL stores product data, while Redis holds temporary session state and is not a table in these diagrams.

### Accounts and listings

```mermaid
erDiagram
    direction LR
    persons ||--o| users : has
    users ||--o{ listings : owns
    listings ||--o{ listing_photos : has

    persons {
        uuid_v7 id PK
        string display_name
    }
    users {
        uuid_v7 id PK
        uuid_v7 person_id FK
        string issuer
        string external_subject
        string account_role
        int auth_version
    }
    listings {
        uuid_v7 id PK
        uuid_v7 seller_id FK
        string status
        int version
    }
    listing_photos {
        uuid_v7 id PK
        uuid_v7 listing_id FK
        string validation_status
    }
```

A listing may have at most five **active** photos; older soft-deleted records can remain.

### Buyer interest and AI

```mermaid
erDiagram
    direction LR
    users ||--o{ interests : expresses
    listings ||--o{ interests : receives
    users ||--o{ generation_attempts : requests
    listings ||--o{ generation_attempts : has

    users {
        uuid_v7 id PK
    }
    listings {
        uuid_v7 id PK
    }
    interests {
        uuid_v7 id PK
        uuid_v7 listing_id FK "Unique with buyer_id"
        uuid_v7 buyer_id FK "Unique with listing_id"
    }
    generation_attempts {
        uuid_v7 id PK
        uuid_v7 listing_id FK
        uuid_v7 actor_id FK
        string request_key "Unique with actor_id"
    }
```
