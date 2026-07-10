# Constraints — Aether Core

## Hard Technical Constraints

- **Embedding dimension is 384** — all-MiniLM-L6-v2 via Ollama. Changing this requires a full re-embedding migration and pgvector index rebuild.
- **`jakarta.*` only** — Spring Boot 3.x; `javax.*` is a compile error.
- **Constructor injection only** — no `@Autowired`, no `@Inject`. All fields `final`.
- **No `SELECT *`** — every SQL query must name columns explicitly.
- **`NamedParameterJdbcTemplate` only** — no string-concatenated SQL ever.
- **No hardcoded secrets** — `POSTGRES_PASSWORD`, `OLLAMA_API_KEY` etc. must come from environment variables.
- **No `System.out.println()`** — SLF4J with parameterized messages only.
- **`--enable-preview`** required — both compiler flag and JVM ENTRYPOINT flag.

## Privacy / GDPR Constraints

- **All memory queries must be scoped to `userId`** — never retrieve memories across users.
- **Never store PII without consent** — memory content is assumed consented but must not include passwords, payment data, or government IDs.
- **Right to erasure** — `DELETE /api/v1/users/{userId}/memories` must delete all memories for a user (planned Phase 3).
- **Retention limits** — `data_retention_days` per user is configurable (planned Phase 3).

## Integration Constraints

- **Core must run standalone** — Grid integration is optional. Core must start and serve requests even when Grid is not configured.
- **Grid calls Core, not the reverse** — Core never initiates calls to Grid. Decoupling is maintained via the Kafka feedback topic.
- **API contract with Grid is fixed** — `GET /api/v1/personal-context/{tenantId}/{userId}` response shape must not break without Grid-side changes.

## Golden Rules (mirrored from eeik-bootstrap)

1. Constructor injection exclusively
2. No hardcoded secrets
3. SLF4J parameterized messages
4. SOLID design principles
5. DDD bounded contexts — port interfaces for cross-module calls
6. Explicit column lists in SQL
7. Parameterized queries only
8. Conventional Commits (`type(scope): description`)
9. No `// TODO` in committed code
10. `jakarta.*` exclusively
