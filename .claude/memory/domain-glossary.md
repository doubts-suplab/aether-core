# Domain Glossary — Aether Core

## Core Concepts

**PersonalMemory** — A single unit of individual memory with a type, content string, strength score (0.0–1.0), access count, and 384-dim pgvector embedding. Memories are reinforced on access (`strength += 0.1`, capped at 1.0) and decay over time (planned Phase 5).

**MemoryType** — Enum classifying memory nature:
- `EPISODIC` — Specific events ("I presented at the conference on Tuesday")
- `SEMANTIC` — Facts and knowledge ("The project deadline is Q3")
- `PROCEDURAL` — How-to skills ("I prefer bullet lists for meeting notes")
- `EMOTIONAL` — Emotional states and associations ("I feel energised after morning stand-up")

**CognitiveSession** — A multi-turn reasoning context for a user. Tracks turn summaries, emotional state, and engagement score across a conversation or work session.

**PersonalContext** — A snapshot assembled on-demand for a given `(tenantId, userId)` pair. Contains recent memory summaries, preferences, emotional state, and engagement score. This is what Aether Grid consumes.

**MemoryStrength** — Float 0.0–1.0. New memories start at 1.0. Reinforcement adds 0.1 (capped). Decay subtracts based on time-since-access (scheduled job, Phase 5).

**EngagementScore** — Float 0.0–1.0. Derived from average strength of recent episodic memories. High score = recently active user.

**EmotionalState** — String constant derived from most recent EMOTIONAL memory content. Default: `"NEUTRAL"`. Values: `NEUTRAL`, `CURIOUS`, `STRESSED`, `MOTIVATED`, `FRUSTRATED`, `CONFIDENT`.

**PersonalMemoryStore** — Port interface for pgvector-backed personal memory persistence. Implemented by `PGVectorPersonalMemoryStore`.

**PersonalContextProvider** — Port interface for assembling a `PersonalContext` from stored memories.

**PersonalEmbeddingService** — Calls Ollama's `/api/embeddings` endpoint using `all-minilm` (all-MiniLM-L6-v2) to generate 384-dim float vectors for memory content.

## Integration Glossary

**Grid → Core call** — `GET /api/v1/personal-context/{tenantId}/{userId}` returns `PersonalContext` JSON consumed by Grid's `AetherCoreBridgeAgent`.

**Core feedback topic** — `aether.core.feedback` Kafka topic. Grid publishes agent decision outcomes; Core consumes them to update personal learning (Phase 4).

**Sister Repository** — `suplab/aether-grid` is the enterprise agent mesh that integrates with Core via `AetherCoreHttpAdapter`.
