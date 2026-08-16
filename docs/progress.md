# Aether Core — Progress Tracker

> **Scope:** This tracker covers **Aether Core** (`suplab/aether-core`) only.
> For Aether Grid progress, see [suplab/aether-grid](https://github.com/suplab/aether-grid).

---

**Active Phase:** Phase 3 — GDPR + Right to Erasure ✅ core complete (erasure + audit + multi-jurisdiction retention holds + data portability export + storage-limitation retention purge + requester-identity verification)
> Phases 4 and 5 were prioritised ahead of Phase 3 (GDPR) by explicit decision.

| Phase | Name | Status | Sessions |
|---|---|---|---|
| 0 | Scaffold | ✅ Complete | 1 |
| 1 | Personal Memory Engine | ✅ Complete | 2 |
| 2 | Cognitive Session Management | ✅ Complete | 2 |
| 3 | GDPR + Right to Erasure | ✅ Core complete (erasure + audit + holds + export + retention purge + identity verification) | 8 |
| 4 | Grid Feedback Loop (Kafka) | ✅ Complete | 3 |
| 5 | Memory Decay + Reinforcement Scheduler | ✅ Complete | 3 |
| 6 | Kubernetes + Helm | ⏳ Planned | — |

---

## Phase 0 — Scaffold ✅

**Commit:** `feat(core): scaffold Aether Core — personal cognitive engine sister project`

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
- 19 agent definitions
- 7 memory files seeded with Core context
- `CLAUDE.md` project brief

**Docs:**
- `README.md`, `docs/index.html`, `docs/architecture.md`, `docs/roadmap.md`, `docs/progress.md`
- GitHub Actions: `ci.yml`, `quality-gate.yml`

### Files created: 57

---

## Phase 1 — Personal Memory Engine ✅

**Commit:** `feat(core): Phase 1 — personal memory engine with reinforce-on-read and context provider`

### What was done

**Reinforce-on-read in `PGVectorPersonalMemoryStore`:**
- `findSimilar()` and `findByType()` now call `memory.reinforce()` on each returned result
- Reinforced state (strength +0.1 capped at 1.0, accessCount +1, lastAccessedAt = now) persisted immediately via UPDATE
- Extracted `mapRow()` helper to eliminate duplication
- `reinforceAndPersist()` private method handles the UPDATE without re-embedding

**`DefaultPersonalContextProvider` — new class in `core-memory`:**
- `com.suplab.aether.core.memory.context.DefaultPersonalContextProvider`
- Implements `PersonalContextProvider` port from `core-domain`
- Fetches EPISODIC + SEMANTIC memories for summaries, EMOTIONAL memories for state
- Returns `Optional.empty()` when user has zero memories across all types
- Engagement score = average of episodic memory strengths (default 0.5 when no episodic memories)

**`PersonalContextController` — refactored to use `PersonalContextProvider` port:**
- Removed direct `PersonalMemoryStore` and `PersonalEmbeddingService` dependencies from controller
- Now injects only `PersonalContextProvider` — single responsibility
- Falls back to `emptyContext()` (NEUTRAL, 0.5) when provider returns empty
- Always HTTP 200 — Grid callers always receive a usable response

**`CoreApiConfig` — updated:**
- `PersonalContextProvider` bean wired: `DefaultPersonalContextProvider(memoryStore, defaultMemoryLimit)`
- `@ConditionalOnProperty(name = "aether.core.embedding.enabled", havingValue = "true", matchIfMissing = true)` on embedding bean
- `aether.core.context.memory-limit` config property (default 5)

**`PersonalMemoryController` — optional embedding:**
- `Optional<PersonalEmbeddingService>` via constructor injection
- When embedding disabled (`aether.core.embedding.enabled=false`), stores zero-vector — other endpoints remain functional

**Unit tests — 18 tests, all green:**
- `PersonalMemoryTest` (12 tests): `create()`, `reinforce()`, validation, all MemoryType values
- `DefaultPersonalContextProviderTest` (6 tests): empty/non-empty contexts, emotional state derivation, engagement score calculation, user/tenant isolation

**Testcontainers integration test — `PGVectorPersonalMemoryStoreIT`:**
- `pgvector/pgvector:pg16` container
- Flyway migrations run in-test
- Tests: save+findByType round-trip, reinforce-on-read (strength progression), findSimilar returns reinforced, countByUser, cross-user isolation, upsert semantics
- Runs in CI (Docker unavailable in local scaffold env)

**JaCoCo 80% line coverage gate:**
- Added to parent `pom.xml` pluginManagement
- `prepare-agent` → `report` → `check` at `verify` phase
- `argLine` property defaulted to empty to prevent `@{argLine}` resolution failure

**`application.yml` additions:**
- `aether.core.embedding.enabled: ${EMBEDDING_ENABLED:true}`
- `aether.core.context.memory-limit: ${CONTEXT_MEMORY_LIMIT:5}`

### Files changed: 9 | Tests added: 18 + 9 IT scenarios

---

## Phase 2 — Cognitive Session Management ✅

**Commit:** `feat(core): Phase 2 — cognitive sessions, user preferences, session-enriched context`

### What was done

**Domain (`core-domain`):**
- `SessionStatus` enum: ACTIVE | CLOSED
- `CognitiveSession` extended with `status` field and behaviour:
  - `start(tenantId, userId)` factory — new ACTIVE session, no turns
  - `withTurn(summary, emotionalState, engagementScore)` — appends turn, updates state; throws `IllegalStateException` on closed sessions
  - `close()` — idempotent transition to CLOSED
- `CognitiveSessionStore` port: save, findById, findActive, findByUser
- `UserPreferenceStore` port: find, save (replace semantics)

**Migrations:**
- `V003__create_cognitive_sessions.sql` — JSONB turn_summaries, partial UNIQUE index enforcing one ACTIVE session per (tenant, user)
- `V004__create_user_preferences.sql` — one JSONB document per user

**Adapters (`core-memory`):**
- `JdbcCognitiveSessionStore` — JSONB serialisation via Jackson; saving an ACTIVE session closes the user's previous active session in the tenant
- `JdbcUserPreferenceStore` — JSONB upsert, replace-on-save
- `DefaultPersonalContextProvider` enriched: active session's emotionalState/engagementScore override memory-derived values; session turns prepended to summaries; preferences populated from store

**API (`core-api`):**
- `CognitiveSessionController` — POST create (closes prior active), GET list, GET by id, PATCH add turn (409 on closed session), POST close
- `UserPreferenceController` — GET / PUT `/api/v1/users/{userId}/preferences`
- `CoreApiConfig` — CognitiveSessionStore, UserPreferenceStore beans; context provider rewired with all three stores

**Tests — 31 unit tests green:**
- `CognitiveSessionTest` (9): start/withTurn/close semantics, closed-session guard, validation
- `PersonalMemoryTest` (12): unchanged from Phase 1
- `DefaultPersonalContextProviderTest` (10): session override, turn ordering, preferences, empty-context rules
- ITs (CI, Testcontainers): `JdbcCognitiveSessionStoreIT` (7 scenarios — one-active enforcement, tenant coexistence, upsert, user scoping), `JdbcUserPreferenceStoreIT` (4 scenarios)

### Files changed: 18

---

## Phase 4 — Grid Feedback Loop (Kafka) ✅

**Commit:** `feat(core): Phase 4 — Kafka feedback loop, Grid decisions become personal memories`

### What was done

**Domain (`core-domain`):**
- `DecisionOutcome` enum: CORRECT | INCORRECT | OVERRIDDEN
- `AgentDecisionFeedback` record: tenantId, userId, agentType, decisionSummary, outcome, confidence, engagementSignal (negative = absent), occurredAt; `hasEngagementSignal()` helper

**Processor (`core-memory`):**
- `AgentDecisionFeedbackProcessor` — the learning half of the Grid ↔ Core loop:
  - CORRECT → PROCEDURAL memory at full strength ("what worked for this user")
  - INCORRECT/OVERRIDDEN → PROCEDURAL memory at 0.6 strength (fades unless pattern repeats)
  - Engagement signal → EMOTIONAL memory: ENGAGED (≥0.66) / NEUTRAL (≥0.33) / DISENGAGED
  - Embedding service optional — zero vectors when Ollama disabled

**Kafka consumer (`core-api`):**
- `GridFeedbackListener` — `@KafkaListener` on `aether.core.feedback` topic (configurable topic + group-id); flat-JSON contract, field-by-field parsing; malformed messages logged and skipped (never wedges the consumer group)
- `GridFeedbackConfig` — `@ConditionalOnProperty(aether.core.feedback.enabled)`, **disabled by default** (Core runs standalone without Kafka)
- `spring-kafka` dependency; `spring.kafka.*` consumer config in application.yml

**Infrastructure:**
- Docker Compose: `kafka-core` service (apache/kafka 3.8, KRaft single node, healthcheck); aether-core wired with `FEEDBACK_ENABLED=true` + `KAFKA_BOOTSTRAP_SERVERS`

**Tests — 11 new, all green (42 total):**
- `AgentDecisionFeedbackProcessorTest` (6): outcome→memory mapping, strength rules, engagement banding
- `GridFeedbackListenerTest` (5): contract parsing, defaults, malformed/missing-field/unknown-outcome skip behaviour

### Files changed: 10

---

## Phase 5 — Memory Decay + Reinforcement Scheduler ✅

**Commit:** `feat(core): Phase 5 — memory decay scheduler with archive and lifecycle metrics`

### What was done

**Port (`core-domain`):**
- `MemoryLifecyclePort` — `runLifecycle()` returning `LifecycleResult(decayedCount, archivedCount, totalRemaining)`

**Migration:**
- `V005__create_personal_memories_archive.sql` — archive keeps all columns including the 384-dim embedding (restore-friendly), plus `archived_at`; indexed on `(user_id, archived_at DESC)`

**Service (`core-memory`):**
- `JdbcMemoryLifecycleService` — fully set-based, two SQL statements per run:
  - Decay: `strength = GREATEST(0, strength - decay_rate × days_since_access)` for memories outside the grace period (`make_interval(days => :decayAfterDays)`)
  - Archive: data-modifying CTE (`WITH moved AS (DELETE … RETURNING …) INSERT …`) — atomic move, a memory can never be deleted without landing in the archive

**Scheduler (`core-api`):**
- `MemoryDecayScheduler` — `@Scheduled` (default cron 03:00 daily), Micrometer metrics:
  - `aether.core.memories.decayed` / `.archived` counters (accumulate across runs)
  - `aether.core.memories.total` gauge (active memories after last run)
- `MemoryLifecycleConfig` — `@EnableScheduling`, enabled by default, `aether.core.memory.decay-enabled=false` to opt out
- Config keys: `decay-rate` (0.01/day), `decay-after-days` (7), `archive-threshold` (0.1), `decay-cron`

**Tests — 2 new unit + 6 IT scenarios (44 unit total green):**
- `MemoryDecaySchedulerTest` (2): delegation + metric recording, counter accumulation vs gauge latest-value
- `JdbcMemoryLifecycleServiceIT` (6, Testcontainers/CI): decay math, grace period, archive move, decay-then-archive same run, strength floor at 0, totalRemaining accuracy

### Files changed: 9

---

## Phase 3 — GDPR + Right to Erasure 🔄 (session 4 — erasure + audit)

**Commit:** `feat(core): GDPR right-to-erasure with append-only audit log (V006)`

Aether Core holds a user's personal data across four tables — `personal_memories` (+ its in-row
embedding), `personal_memories_archive`, `cognitive_sessions`, and `user_preferences`. Phase 3 adds
GDPR Article 17 right-to-erasure over all of them, with an audit trail that survives the erased data.

### What was done

**Erasure (Core-local, permanent):**
- `PersonalDataErasurePort` + `DefaultPersonalDataErasureService` (core-memory): `eraseMemories`
  (memories only, active + archived) and `eraseAccount` (memories + sessions + preferences). Erasure
  is Core-local — Grid reads personal context live, so no cross-service propagation is needed.
- Store deletions: `PersonalMemoryStore.deleteAllByUser` (active **and** archive; embeddings are
  in-row so they go with the rows), `CognitiveSessionStore.deleteAllByUser` (across every tenant —
  erasure is a property of the person), `UserPreferenceStore.deleteByUser`.
- `DataSubjectController` (core-api): `DELETE /api/v1/users/{userId}/memories`,
  `DELETE /api/v1/users/{userId}`, `GET /api/v1/users/{userId}/erasures` (audit history);
  `requestedBy` defaults to `data-subject`.

**Audit (accountability, Article 5(2)):**
- `ErasureEvent` domain record + `ErasureEventStore` port + `JdbcErasureEventStore` — **append-only**
  (`record` + scoped `findByUser`; no update/delete). Records only the subject's own `userId`, scope,
  per-store counts, requester, and timestamp — **never any memory content** — so it may be retained to
  demonstrate compliance after the data is gone.
- **Migration V006** `erasure_events` (api + core-memory test + core-infra copies).

**Testcontainers green in CI:**
- `maven-failsafe-plugin` wired in the parent and activated in `core-memory`, so the `*IT` tests
  (`PGVectorPersonalMemoryStoreIT`, `JdbcCognitiveSessionStoreIT`, `JdbcUserPreferenceStoreIT`,
  `JdbcMemoryLifecycleServiceIT`, and the new `JdbcErasureEventStoreIT`) now run at `verify`.
  Previously no failsafe plugin existed, so surefire never ran `*IT`.

**Tests — 54 unit tests green (was ~44):**
- Domain `ErasureEventTest` (4); engine `DefaultPersonalDataErasureServiceTest` (3, fake stores —
  memories-only vs. full account, audit recorded); api `DataSubjectControllerTest` (3).
- Store ITs gain `deleteAllByUser` / `deleteByUser` cases (active+archive, cross-tenant sessions,
  preferences), plus a new `JdbcErasureEventStoreIT`.
- `mvn -DskipITs verify` passes the JaCoCo 80% gate; ITs run under failsafe in CI.

---

## Phase 3 — GDPR + Right to Erasure 🔄 (session 5 — multi-jurisdiction retention holds)

**Commit:** `feat(core): legal/statutory retention holds gate erasure (V007)`

Session 4 built the erasure primitive for the EU (GDPR). Session 5 makes it **multi-jurisdiction**.
The primitive — delete on request + immutable audit — already satisfies the "delete" right of GDPR,
CCPA/CPRA, the ~20 US state privacy laws, LGPD, PIPL, DPDP and others alike; what differs across
regimes is **statutory retention exceptions** (GDPR Art. 17(3), CCPA §1798.105(d), HIPAA/GLBA
retention mandates, active litigation). This session adds the seam that honours them uniformly.

### What was done

**Legal / statutory retention holds:**
- `DataCategory` enum (`MEMORIES` | `SESSIONS` | `PREFERENCES`), `LegalHold` record, and
  `LegalHoldStore` port (place / lift / heldCategories / findByUser) in `core-domain`.
- `JdbcLegalHoldStore` (core-memory) backed by the `legal_holds` table — one row per
  `(user_id, category)`, `place` upserts.
- `DefaultPersonalDataErasureService` now consults `heldCategories(userId)` and **skips every held
  category**; unheld data is still erased. Both `eraseMemories` and `eraseAccount` are hold-aware.
- `LegalHoldController` (core-api): `GET/PUT/DELETE /api/v1/users/{userId}/legal-holds[/{category}]`.

**Truthful partial-erasure audit:**
- `ErasureEvent` gains `heldCategories` (immutable set) + `hasHolds()`; the erasure view and audit
  log now report which categories a hold retained, so a partial erasure is auditable, not silent.
- **Migration V007** — `legal_holds` table + `held_categories` column on `erasure_events` (api +
  core-memory test + core-infra copies).

**Tests — 65 unit tests green (was 54):**
- Domain `LegalHoldTest` (2) + `ErasureEventTest` held/immutability cases (2).
- Engine `DefaultPersonalDataErasureServiceTest` hold-skip cases (3): held category skipped and
  recorded, memories-only ignores out-of-scope holds.
- Api `LegalHoldControllerTest` (4). New `JdbcLegalHoldStoreIT` + `held_categories` round-trip in
  `JdbcErasureEventStoreIT` under failsafe.
- `mvn -DskipITs verify` passes the JaCoCo 80% gate.

### Remaining Phase 3 (follow-up)
- **Requester identity verification** on erasure — ✅ delivered in session 8 above.
- **`data_retention_days`** per user + a retention purge sweep — ✅ delivered in session 7 below.

---

## Phase 3 — GDPR + Right to Erasure ✅ (session 8 — requester identity verification)

**Commit:** `feat(core): requester identity verification on erasure + export (GDPR Art. 12(6))`

Erasure and export trusted the caller. GDPR Article 12(6) lets a controller demand proof of identity
before acting on a data-subject request; this session adds that gate — config-gated and fail-closed so
Core still runs open standalone.

### What was done

**Signed, time-boxed verification token (domain):**
- `SubjectVerificationToken` (core-domain) — a pure HMAC-SHA256 utility: `mint(userId, secret,
  expiresAt)` → `<expiryEpoch>.<base64url(HMAC(secret, userId + ":" + expiry))>`, and
  `verify(userId, token, secret, now)` (constant-time compare, expiry-aware). A token minted for one
  user never validates for another; a tampered or expired token is rejected, never throws.

**Config-gated verifier + gate (api):**
- `DataSubjectVerifier` (core-api) — off by default (`aether.core.data-subject.require-verification`);
  when enabled a request must carry a valid `X-Subject-Verification` token bound to the target user or
  the endpoint returns `401`. Fail-closed: enabling it with a blank
  `aether.core.data-subject.verification-secret` fails bean construction. Injected `Clock` for testable
  expiry.
- `DataSubjectController` now gates **erase memories**, **full-account erase**, and **export** on the
  verifier; the read-only **erasure history** is never gated. A rejected request touches no data and
  reveals nothing about the user.
- Core does not mint tokens to the public — a trusted front door that has actually verified the subject
  (email/OTP) signs them with the shared secret, mirroring Memory's federation bearer-token seam. No
  migration — verification is a request-time concern.

**Tests — 71 unit tests green (was 65):**
- `SubjectVerificationTokenTest` (7): round-trip, wrong-user, expiry boundary, wrong-secret, tamper,
  malformed-not-thrown, blank-input rejection. `DataSubjectVerifierTest` (4): disabled-allows,
  fail-closed construction, valid-token-only, clock-based expiry. `DataSubjectControllerTest` (+4):
  401 on missing/bad/other-user token with data untouched, 200 on valid token, history never gated.
- `mvn -DskipITs verify` passes the JaCoCo 80% gate.

---

## Phase 3 — GDPR + Right to Erasure 🔄 (session 6 — data portability / export)

**Commit:** `feat(core): personal data export (GDPR Art. 20 portability)`

Where erasure proves data was destroyed, **export hands it back**. This session adds the
data-portability half of Phase 3 — the GDPR Article 20 / CCPA right-to-know read.

### What was done

**Portable, read-only export:**
- `PersonalDataExport` record (core-domain) — a snapshot of a user's personal memories
  (active **and** archived), cognitive sessions across **every** tenant, and preferences,
  with `totalRecords()`, defensive copies, and a `now()`-stamped `of(...)` factory.
- `PersonalDataExportPort` (core-domain) + `DefaultPersonalDataExportService` (core-memory)
  composing the three stores, bounded by `MAX_EXPORT = 10_000` per collection.
- Two **non-reinforcing** read methods added to the stores — `PersonalMemoryStore.findAllByUser`
  (a `UNION ALL` over active + archive, plain read, **no** `reinforce()`) and
  `CognitiveSessionStore.findAllByUser` (cross-tenant, most-recently-active first). Export is
  an administrative read: it never perturbs memory strengths the way recall does.
- `GET /api/v1/users/{userId}/export` on `DataSubjectController` — returns a portable JSON view
  (memories, sessions, preferences + `totalRecords`, `exportedAt`).

**Tests — unit suite green (75):**
- Domain `PersonalDataExportTest` (6): assembly, `totalRecords`, defensive copy, null-collection
  defaults, blank-user rejection.
- Engine `DefaultPersonalDataExportServiceTest` (3): composition, `MAX_EXPORT` bound, blank-user.
- Api `DataSubjectControllerTest` export case; new failsafe IT cases —
  `findAllByUser` active+archived union + no-reinforcement in `PGVectorPersonalMemoryStoreIT`,
  cross-tenant + per-user isolation in `JdbcCognitiveSessionStoreIT`.
- No new migration — export is read-only over existing tables.

---

## Phase 3 — GDPR + Right to Erasure 🔄 (session 7 — storage-limitation retention purge)

**Commit:** `feat(core): scheduled retention purge (GDPR storage limitation, V008)`

Erasure deletes on request; export hands data back. This session adds the **automated** half of the
privacy story — GDPR storage limitation (Art. 5(1)(e)): data that has outlived its usefulness is
deleted on a schedule, not left to accumulate.

### What was done

**Per-user retention window + hold-aware purge:**
- `UserPrivacySettings` record (core-domain) — `data_retention_days` (`0` = keep indefinitely) +
  `hasRetentionLimit()`; `UserPrivacySettingsStore` port + `JdbcUserPrivacySettingsStore` (upsert).
- Age-based `deleteOlderThan(userId, cutoff)` added to `PersonalMemoryStore` (active + archive) and
  `CognitiveSessionStore` (cross-tenant) — newer records are kept.
- `RetentionPurgePort` + `DefaultRetentionPurgeService`: for each user with a window, delete memories
  and sessions older than `now − days`, **skipping any category under a legal hold** exactly as
  on-request erasure does, and recording a new **`ErasureScope.RETENTION`** `ErasureEvent`
  (`requestedBy = retention-policy`) — the same audit log, so a purge is as accountable as an erasure.
  Preferences (current config, not history) are out of scope.
- `RetentionPurgeScheduler` (`@Scheduled`, default 02:30, opt-out via `aether.core.retention.purge-enabled`)
  + Micrometer counters `aether.core.retention.{memories,sessions}-purged`.
- `GET/PUT /api/v1/users/{userId}/privacy-settings` to read/set the window.
- **Migration V008** — `user_privacy_settings` table + relaxes the `erasure_events` scope CHECK to
  admit `RETENTION` (api + core-memory test + core-infra copies).

**Tests — 89 unit tests green (was 75):**
- Domain `UserPrivacySettingsTest` (5); engine `DefaultRetentionPurgeServiceTest` (6): age-based purge +
  audit, hold-skip, no-op when no window / zero window, sweep aggregation, no audit noise on an empty
  pass; api `UserPrivacySettingsControllerTest` (3).
- New failsafe ITs: `deleteOlderThan` age cases in `PGVectorPersonalMemoryStoreIT` +
  `JdbcCognitiveSessionStoreIT`, and `JdbcUserPrivacySettingsStoreIT`.
- `mvn -DskipITs verify` passes the JaCoCo 80% gate.

### Remaining Phase 3 (follow-up)
- **Requester identity verification** on erasure/export — ✅ delivered (session 8, GDPR Art. 12(6)).
