# Aether Core — Development Roadmap

> **Scope:** This roadmap covers Aether Core only.
> For Aether Grid roadmap, see [suplab/aether-grid/docs/roadmap.md](https://github.com/suplab/aether-grid/blob/main/docs/roadmap.md).

---

## Phase 0 — Scaffold ✅

**Goal:** Standalone project bootstrapped. Independent Maven multi-module, Spring Boot 3.3.5, all golden rules enforced, sister repo relationship established.

| Deliverable | Status |
|---|---|
| Independent parent POM (not child of Grid) | ✅ |
| 4 Maven modules: core-domain, core-memory, core-api, core-infra | ✅ |
| Domain model: PersonalMemory, MemoryType, CognitiveSession, PersonalContext | ✅ |
| Port interfaces: PersonalMemoryStore, PersonalContextProvider | ✅ |
| PGVectorPersonalMemoryStore adapter | ✅ |
| PersonalEmbeddingService (Ollama all-MiniLM-L6-v2, 384-dim) | ✅ |
| PersonalContextController (`GET /api/v1/personal-context/{tenantId}/{userId}`) | ✅ |
| PersonalMemoryController (POST, GET count, DELETE) | ✅ |
| Flyway migrations V001 + V002 (pgvector schema) | ✅ |
| Docker Compose (postgres-core + aether-core) | ✅ |
| GitHub Actions CI + quality-gate | ✅ |
| CLAUDE.md + .claude/memory/ (7 files) + .claude/agents/ (19 agents) | ✅ |
| Docs: README, index.html, architecture.md, roadmap.md, progress.md | ✅ |

---

## Phase 1 — Personal Memory Engine ✅

**Goal:** Memory store fully operational with reinforcement-on-read, integration tests, and a working `PersonalContextProvider` implementation.

| Deliverable | Status |
|---|---|
| Reinforce-on-read in `PGVectorPersonalMemoryStore` | ✅ |
| `PersonalContextProvider` implementation in core-memory | ✅ |
| Testcontainers integration test: save + findSimilar round-trip | ✅ |
| `PersonalContextController` uses `PersonalContextProvider` port | ✅ |
| `@ConditionalOnProperty` for embedding (skip when Ollama unavailable) | ✅ |
| Unit tests for PersonalMemory domain logic | ✅ |
| JaCoCo 80% line coverage gate | ✅ |

---

## Phase 2 — Cognitive Session Management ✅

**Goal:** Multi-turn reasoning sessions persisted and retrievable. Session context included in `PersonalContext` response.

| Deliverable | Status |
|---|---|
| `cognitive_sessions` Flyway migration (V003) | ✅ |
| `CognitiveSessionStore` port interface | ✅ |
| `JdbcCognitiveSessionStore` adapter | ✅ |
| `CognitiveSessionController` (POST create, GET by userId, PATCH add turn, POST close) | ✅ |
| `PersonalContext` enriched with active session's turn summaries | ✅ |
| User preferences table (V004) + `UserPreferenceStore` + GET/PUT endpoint | ✅ |

---

## Phase 3 — GDPR + Privacy Controls

> **Note:** Phases 4 and 5 were delivered before Phase 3 by explicit prioritisation.
> This phase takes migration **V006** (V005 was consumed by the Phase 5 archive table).

**Goal:** Full right-to-erasure, multi-jurisdiction retention holds, data retention configuration, and memory export.

> **Multi-jurisdiction note:** the erasure primitive (delete on request + immutable audit) is
> jurisdiction-neutral — it satisfies the "delete" right of GDPR (Art. 17), CCPA/CPRA, the ~20 US
> state privacy laws, LGPD, PIPL, DPDP, and others alike. The mechanic that differs is **statutory
> retention exceptions** (GDPR Art. 17(3), CCPA §1798.105(d), HIPAA/GLBA mandates), which the
> `legal_holds` seam below handles uniformly.

| Deliverable | Status |
|---|---|
| `DELETE /api/v1/users/{userId}/memories` — erase all memories (active + archived) | ✅ |
| `DELETE /api/v1/users/{userId}` — full account erasure (memories + sessions + preferences) | ✅ |
| Audit log for erasure events (append-only) + `GET /api/v1/users/{userId}/erasures` | ✅ |
| V006 migration: `erasure_events` audit table | ✅ |
| Legal / statutory **retention holds** — erasure retains held categories, records them in the audit (`heldCategories`) | ✅ |
| `legal_holds` store + `PUT/DELETE/GET /api/v1/users/{userId}/legal-holds/{category}` | ✅ |
| V007 migration: `legal_holds` table + `held_categories` column | ✅ |
| Memory export: `GET /api/v1/users/{userId}/export` (JSON, portability — Art. 20 / CCPA right-to-know) | ⏳ (follow-up) |
| Requester identity verification on erasure (CCPA verifiable request) | ⏳ (follow-up) |
| `data_retention_days` per user configurable + retention purge (`user_privacy_settings`, V008) | ⏳ (follow-up) |

---

## Phase 4 — Grid Feedback Loop (Kafka) ✅

**Goal:** Aether Core consumes Grid decision feedback to improve personal context relevance.

| Deliverable | Status |
|---|---|
| Kafka consumer for `aether.core.feedback` topic | ✅ |
| `AgentDecisionFeedbackProcessor`: maps Grid outcomes to memory reinforcement/creation | ✅ |
| `PROCEDURAL` memory auto-created from correct Grid decisions | ✅ |
| `EMOTIONAL` memory updated from engagement signals | ✅ |
| Docker Compose Kafka service added | ✅ |

---

## Phase 5 — Memory Decay + Reinforcement Scheduler ✅

**Goal:** Memory strength evolves over time — unused memories decay, accessed memories reinforce.

| Deliverable | Status |
|---|---|
| `@Scheduled` decay job: `strength -= 0.01 * days_since_access` for memories older than 7 days | ✅ |
| Configurable decay rate (`aether.core.memory.decay-rate`) | ✅ |
| Memories below `strength < 0.1` archived (not deleted) | ✅ |
| Memory archive table (V005 migration) | ✅ |
| Micrometer metrics: `aether.core.memories.total`, `.decayed`, `.archived` | ✅ |

---

## Phase 6 — Kubernetes + Helm

**Goal:** Production-ready deployment for Core on Kubernetes (vanilla, AWS EKS, OpenShift).

| Deliverable | Status |
|---|---|
| `core-api/Dockerfile` (multi-stage, Temurin 21 JRE, non-root uid 1000) | ⏳ |
| Helm chart: `core-infra/helm/aether-core/` | ⏳ |
| `values.yaml`, `values-aws.yaml`, `values-openshift.yaml` | ⏳ |
| GitHub Actions Docker build + Helm release workflows | ⏳ |
| HPA (min 2, max 4 replicas) | ⏳ |

---

> **Aether Grid Roadmap:** [suplab/aether-grid/docs/roadmap.md](https://github.com/suplab/aether-grid/blob/main/docs/roadmap.md)
