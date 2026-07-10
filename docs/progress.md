# Aether Core — Progress Tracker

> **Scope:** This tracker covers **Aether Core** (`suplab/aether-core`) only.
> For Aether Grid progress, see [suplab/aether-grid](https://github.com/suplab/aether-grid).

---

**Active Phase:** Phase 1 — Personal Memory Engine

| Phase | Name | Status | Sessions |
|---|---|---|---|
| 0 | Scaffold | ✅ Complete | 1 |
| 1 | Personal Memory Engine | 🔄 In Progress | — |
| 2 | Cognitive Session Management | ⏳ Planned | — |
| 3 | GDPR + Right to Erasure | ⏳ Planned | — |
| 4 | Grid Feedback Loop (Kafka) | ⏳ Planned | — |
| 5 | Memory Decay + Reinforcement Scheduler | ⏳ Planned | — |
| 6 | Kubernetes + Helm | ⏳ Planned | — |

---

## Phase 0 — Scaffold ✅

**Commit:** `chore(core): initial scaffold — independent Maven multi-module project`

### What was done

**Maven project:**
- `pom.xml` — independent parent POM (`aether-core-parent`), Spring Boot 3.3.5 BOM, Java 21, `--enable-preview`, `-parameters` flags
- 4 modules: `core-domain`, `core-memory`, `core-api`, `core-infra`

**`core-domain` — pure domain (no Spring):**
- `PersonalMemory` record: id, userId, MemoryType, content, strength (0–1), accessCount, timestamps; `create()` factory; `reinforce()` returns new instance with strength+0.1
- `MemoryType` enum: EPISODIC, SEMANTIC, PROCEDURAL, EMOTIONAL
- `CognitiveSession` record: sessionId, userId, tenantId, turnSummaries, emotionalState, engagementScore, timestamps
- `PersonalContext` record: userId, tenantId, recentMemorySummaries, preferences, emotionalState, engagementScore, fetchedAt
- `PersonalMemoryStore` port interface: save, findSimilar, findByType, delete, countByUser
- `PersonalContextProvider` port interface: buildContext

**`core-memory` — pgvector adapter + embedding:**
- `PGVectorPersonalMemoryStore`: cosine similarity search (`<=> :query::vector`), explicit column lists, `NamedParameterJdbcTemplate`, `ON CONFLICT` upsert
- `PersonalEmbeddingService`: Ollama REST client (`/api/embeddings`), 384-dim, graceful fallback to zero vector on error

**`core-api` — Spring Boot application:**
- `AetherCoreApplication`: port 8082, `scanBasePackages = "com.suplab.aether.core"`
- `PersonalContextController`: `GET /api/v1/personal-context/{tenantId}/{userId}` — key endpoint consumed by Aether Grid
- `PersonalMemoryController`: `POST /api/v1/users/{userId}/memories`, `GET .../count`, `DELETE .../{memoryId}`
- `CoreApiConfig`: wires `PGVectorPersonalMemoryStore` and `PersonalEmbeddingService` as `@Bean`
- `application.yml`: port 8082, Flyway enabled, Ollama base-url configurable, actuator probes

**`core-infra` — infrastructure:**
- `V001__create_personal_memories.sql`: personal_memories table with indexes
- `V002__pgvector_personal_embeddings.sql`: pgvector extension, vector(384) column, ivfflat index
- `docker/docker-compose.yml`: postgres-core (port 5433) + aether-core (port 8082)

**`.claude/` setup:**
- 19 agent definitions (copied from eeik-bootstrap / aether-grid)
- 7 memory files seeded with Core context
- `CLAUDE.md` project brief

**Docs:**
- `README.md`, `docs/index.html`, `docs/architecture.md`, `docs/roadmap.md`, `docs/progress.md`
- GitHub Actions: `ci.yml`, `quality-gate.yml`

### Files created: 48
