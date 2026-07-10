# Project Context — Aether Core

## Service Identity
- **Name:** Aether Core (`suplab/aether-core`)
- **Purpose:** Personal cognitive engine — individual memory, reasoning, and emotional context
- **Port:** 8082
- **Database:** `aether_core` (PostgreSQL 16 + pgvector, separate from Aether Grid)

## Sister Repository
- **Aether Grid:** `suplab/aether-grid` (enterprise agent mesh)
- Grid calls Core at: `GET /api/v1/personal-context/{tenantId}/{userId}`
- Grid's `AetherCoreHttpAdapter` uses `PersonalContextPort` interface

## Maven Modules
| Module | Artifact ID | Purpose |
|---|---|---|
| `core-domain` | `core-domain` | Domain types + port interfaces (no Spring) |
| `core-memory` | `core-memory` | pgvector store + embedding service |
| `core-api` | `core-api` | Spring Boot app, REST controllers, Flyway |
| `core-infra` | `core-infra` | Docker Compose, standalone migrations |

## Key Packages
- `com.suplab.aether.core.domain` — PersonalMemory, CognitiveSession, PersonalContext, MemoryType
- `com.suplab.aether.core.ports` — PersonalMemoryStore, PersonalContextProvider
- `com.suplab.aether.core.memory.store` — PGVectorPersonalMemoryStore
- `com.suplab.aether.core.memory.embedding` — PersonalEmbeddingService (Ollama)
- `com.suplab.aether.core.api` — AetherCoreApplication, controllers, config

## Environments
- **Local:** Docker Compose at `core-infra/docker/docker-compose.yml` (postgres-core on 5433, app on 8082)
- **CI:** GitHub Actions, pgvector service container
- **Production:** Kubernetes (Helm chart — planned Phase 6)

## Kafka Topics
- Consumed: `aether.core.feedback` — Grid decision feedback for personal learning (Phase 4)

## Current Status
- Phase 0 (scaffold) complete
- Active: Phase 1 — Personal Memory Engine
