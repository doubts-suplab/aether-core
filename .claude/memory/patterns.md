# Patterns — Aether Core

## Hexagonal Architecture

Ports live in `core-domain`, adapters in `core-memory` and `core-api`:

```
core-domain (no Spring)
  └── ports/
      ├── PersonalMemoryStore      ← driven port (persistence)
      └── PersonalContextProvider  ← driven port (context assembly)

core-memory (Spring JDBC adapter)
  └── store/PGVectorPersonalMemoryStore  implements PersonalMemoryStore
  └── embedding/PersonalEmbeddingService

core-api (Spring Boot)
  └── config/CoreApiConfig  ← wires adapters to ports via @Bean
  └── controller/PersonalContextController  ← driving port (HTTP)
  └── controller/PersonalMemoryController   ← driving port (HTTP)
```

## Memory Reinforcement on Read

Every `findSimilar` or `findByType` call should call `memory.reinforce()` on returned memories and persist the updated strength. This is intentional — frequently accessed memories strengthen.

```java
var memories = memoryStore.findByType(userId, type, limit);
memories.forEach(m -> memoryStore.save(m.reinforce(), existingEmbedding));
```

(Phase 1 task: implement reinforce-on-read in the store.)

## Vector Storage Pattern (same as Grid)

- Embeddings stored as `vector(384)` in PostgreSQL via pgvector
- Cosine similarity search: `ORDER BY embedding <=> :query::vector`
- `float[]` ↔ `[x,y,z,...]` string conversion via `toVectorString(float[])` helper
- Index: `ivfflat` with `vector_cosine_ops`, `lists = 100`

## PersonalContext Assembly

`PersonalContext` is assembled on-demand (no caching initially):

1. Fetch EPISODIC memories (top N by strength)
2. Fetch SEMANTIC memories (top N by strength)
3. Fetch EMOTIONAL memories (top 2 — last known emotional state)
4. Derive `emotionalState` from most recent EMOTIONAL memory content
5. Derive `engagementScore` from average strength of EPISODIC memories
6. Return assembled `PersonalContext` with all summaries concatenated

## @Bean Wiring (no @Component in domain/memory)

`core-domain` and `core-memory` classes are NOT annotated with `@Component`/`@Service`. All Spring beans are created in `CoreApiConfig` via `@Bean` factory methods. This keeps domain + memory modules Spring-free and testable without a context.

## Conventional Commits

```
feat(core-memory): implement pgvector similarity search
fix(core-api): handle missing userId in personal context request
docs(core): update roadmap with Phase 2 cognitive sessions
chore(core-infra): add pgvector index to V002 migration
```
