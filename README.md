<h1 align="center">LoopMarket</h1>

<p align="center">
  <strong>A marketplace for second chances, built with an agentic mindset.</strong>
</p>

<p align="center">
  <img alt="Status: in development" src="https://img.shields.io/badge/status-in%20development-00c853?style=for-the-badge&amp;labelColor=101820">
  <img alt="Backend: Java and Dropwizard" src="https://img.shields.io/badge/backend-Java%20%2B%20Dropwizard-f89820?style=for-the-badge&amp;labelColor=101820">
  <img alt="Infrastructure: Docker Compose" src="https://img.shields.io/badge/infra-Docker%20Compose-2496ed?style=for-the-badge&amp;labelColor=101820">
  <img alt="AI: local and external models planned" src="https://img.shields.io/badge/AI-local%20%2B%20external%20planned-9c6ade?style=for-the-badge&amp;labelColor=101820">
</p>

LoopMarket is a planned second-hand marketplace where sellers create listings with optional AI assistance and buyers can express interest. Sellers review and approve everything before publication.

**Status:** The Dropwizard CRUD and development authentication have been tested with local MySQL, Redis, and Keycloak. The marketplace UI, AI integration, and mobile app are not implemented yet.

## Quick start

Requires Docker and Docker Compose 2.20 or later. From the repository root:

```sh
./.docker/server.sh dev list                 # List services
./.docker/server.sh dev status               # Show state and memory usage
./.docker/server.sh dev logs backend redis   # Follow logs from selected services
./.docker/server.sh dev start mysql redis     # Start selected services
./.docker/server.sh dev stop mysql redis      # Stop selected services
./.docker/server.sh dev start all             # Start every service
./.docker/server.sh dev stop all              # Stop every service
```

The first argument selects the environment. `dev` is configured; `qat`, `uat`, and `prd` are reserved and fail without touching development resources until their own configurations are added. After the environment, service names and the action can appear in any order. Starting builds images and may download them; stopping preserves containers and volumes. The marketplace UI is not yet available.

Use `dev logs all` to follow every service. Log lines carry aligned service tags; press `Ctrl+C` to stop following them.

To start the initial backend and explore its API:

```sh
./.docker/server.sh dev start backend
```

With the default ports, the hello endpoint is `http://localhost:8080/api/hello` and Swagger UI is `http://localhost:8080/api/docs/`. Starting the backend also starts MySQL, Redis and Keycloak; MySQL and Redis must pass their health checks. Database connection settings come from `.env-dev`. The backend builds with Maven inside its container on startup; a first build needs dependency downloads. The administrative connector is private to the container and includes a database health check.

The development identity realm includes these demo accounts. They are public test credentials and must only be used in an isolated development environment.

| Username | Password | Role |
|---|---|---|
| `darth.vader` | `dark-side-1977` | Admin |
| `luke.skywalker` | `force-1977` | User |

In development, Liquibase restores the local accounts at startup; the backend periodically recreates or enables the corresponding Keycloak users and resets their demo passwords. Keycloak's initial realm import alone only runs when the realm does not already exist.

Request a development session and use its `accessToken` as an opaque bearer token. Sessions expire after 30 minutes, are checked against Redis on each authenticated request, and become invalid when the local account changes or is deleted:

```sh
curl -X POST http://localhost:8080/api/sessions \
  -H 'Content-Type: application/json' \
  -d '{"username":"darth.vader","password":"dark-side-1977"}'

curl http://localhost:8080/api/persons -H 'Authorization: Bearer <accessToken>'
curl -X DELETE http://localhost:8080/api/sessions -H 'Authorization: Bearer <accessToken>'
```

The API defines CRUD endpoints for persons, users, listings, interests, photos, and generation attempts. Access is restricted by role or ownership. Photo upload accepts JPEG or PNG and stores image files in the `zartis_photo-data` volume; generation records do not yet call an AI provider. API contracts and example request schemas are available in Swagger UI.

With the backend running and its development dependencies available, run the backend checks, including the opt-in HTTP integration test, inside its container. The integration test creates development records and soft-deletes them; it also exercises the demo accounts and sessions:

```sh
docker compose --project-directory "$PWD" --env-file .env-dev -f .docker/docker-compose.yaml \
  --profile apps --profile mobile exec backend sh -c \
  'LOOPMARKET_E2E=true LOOPMARKET_E2E_URL=http://backend:$BACKEND_PORT/api mvn -B -ntp -pl app -am test'
```

## Services

Each service has its own Dockerfile and Compose definition. All containers share the `zartis` network.

```text
LoopMarket
├── Backend
│   └── Backend application     (Java and Dropwizard)
├── Frontend
│   └── Web application         (Next.js, React and TypeScript, proposed)
├── Mobile
│   └── Mobile application      (Flutter and Dart, planned)
├── AI
│   ├── AI gateway              (model routing and provider integrations, planned)
│   └── Ollama                  (local model runtime)
└── Infrastructure
    ├── NGINX                   (application ingress)
    ├── MySQL                   (marketplace data)
    ├── Redis                   (user session cache)
    ├── Qdrant                  (AI vector database)
    ├── Keycloak                (identity provider)
    ├── SonarQube               (code quality)
    ├── PostgreSQL              (SonarQube database)
    ├── Elasticsearch           (observability data)
    ├── Kibana                  (observability interface)
    └── Elastic Agent           (log and metrics collection)
```

## Configuration

- `.env-dev` contains local ports and credentials; it is created from `.env-dev.example` and is ignored by Git.
- The root Compose file delegates to `.docker/docker-compose.yaml` and the individual service definitions.
- `make check` validates Compose and tests resource preparation with a simulated Docker daemon.
- Development credentials and disabled Elasticsearch authentication are for local use only.
- Image builds and service integrations have not yet been verified. Ollama does not include downloaded models.

Code comments should be professional, short, and clear, with friendly, subtle Sith or Star Wars references.

## Planned architecture

```mermaid
flowchart LR
    User[Seller / Buyer] --> Browser[Web browser]
    Browser --> Nginx[NGINX]
    Nginx --> Web[Web frontend]
    Nginx --> API[Dropwizard backend]
    Browser -. Sign in .-> Identity[Keycloak]
    API -. Verify identity .-> Identity
    Mobile[Flutter app] -. Future .-> API

    API --> DB[(MySQL)]
    API --> Cache[(Redis)]
    API --> Photos[(Photo storage)]
    API --> Gateway[AI gateway]
    Gateway --> Ollama[Ollama]
    Gateway --> Vector[(Qdrant)]
    Gateway -.-> Providers[External AI providers]

    API -. Logs .-> Agent[Elastic Agent]
    Gateway -. Logs .-> Agent
    Agent --> Elastic[(Elasticsearch)]
    Kibana[Kibana] --> Elastic
    Code[Source code] -. Analysis .-> Sonar[SonarQube]
    Sonar --> SonarDB[(PostgreSQL)]
```

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

A listing may have at most one **active** photo; older soft-deleted records can remain.

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
