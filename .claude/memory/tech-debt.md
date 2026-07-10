# Technical Debt — Aether Core

## Known Debt

| Item | Module | Priority | Notes |
|---|---|---|---|
| No Kafka consumer for `aether.core.feedback` | core-api | High | Grid sends decision feedback; Core can't learn from it yet. Phase 4. |
| No memory decay scheduler | core-memory | Medium | Memories never weaken over time. Needs `@Scheduled` job. Phase 5. |
| No auth on API endpoints | core-api | High | Assumes network-level security (VPC/K8s NetworkPolicy). Must add API key or OAuth2 before production. Phase 3. |
| No GDPR right-to-erasure endpoint | core-api | High | `DELETE /api/v1/users/{userId}/memories` (delete all) not implemented. Phase 3. |
| No reinforce-on-read | core-memory | Medium | `findSimilar`/`findByType` should call `memory.reinforce()` and persist. Phase 1. |
| No CognitiveSession persistence | core-api | Medium | `CognitiveSession` domain type exists but no controller or store. Phase 2. |
| Preferences always empty | core-api | Low | `PersonalContext.preferences` is always `Map.of()` — no preference storage yet. Phase 2. |
| No Helm chart | core-infra | Low | K8s deployment manifests and Helm chart needed for Phase 6. |
| No integration tests | core-api | Medium | No Testcontainers integration tests. Phase 1 task. |
| No memory export | core-api | Low | No way to export all memories for a user (portability). Phase 3 (GDPR). |
