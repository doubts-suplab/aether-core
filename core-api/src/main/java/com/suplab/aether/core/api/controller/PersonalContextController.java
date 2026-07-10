package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.memory.embedding.PersonalEmbeddingService;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serves the personal context snapshot consumed by Aether Grid.
 *
 * <p>The key endpoint is {@code GET /api/v1/personal-context/{tenantId}/{userId}}.
 * Aether Grid's {@code AetherCoreHttpAdapter} calls this before agent decisions so that
 * {@code AetherCoreBridgeAgent} can enrich {@code AgentInput.context} with personal
 * memories, emotional state, and engagement score.</p>
 *
 * <p>The response is intentionally a flat {@code Map<String, Object>} rather than a typed
 * DTO, matching the format Aether Grid's HTTP adapter deserialises via Jackson. This
 * avoids a shared DTO module between the two repositories.</p>
 */
@RestController
@RequestMapping("/api/v1/personal-context")
public class PersonalContextController {

    private static final Logger log = LoggerFactory.getLogger(PersonalContextController.class);

    private final PersonalMemoryStore memoryStore;
    private final PersonalEmbeddingService embeddingService;

    public PersonalContextController(PersonalMemoryStore memoryStore,
                                     PersonalEmbeddingService embeddingService) {
        this.memoryStore = memoryStore;
        this.embeddingService = embeddingService;
    }

    /**
     * Returns the personal context snapshot for a user within a tenant.
     *
     * <p>Assembles recent episodic and semantic memories, derives emotional state from
     * emotional memories, and computes an engagement score from episodic memory strengths.</p>
     *
     * @param tenantId    the tenant scope (required for multi-tenant isolation)
     * @param userId      the user whose context to assemble
     * @param memoryLimit maximum number of memories per type (default 5)
     * @return personal context as a JSON map, always HTTP 200
     */
    @GetMapping("/{tenantId}/{userId}")
    public ResponseEntity<Map<String, Object>> getContext(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @RequestParam(defaultValue = "5") int memoryLimit) {

        log.debug("Fetching personal context tenantId={} userId={} limit={}", tenantId, userId, memoryLimit);

        var episodic = memoryStore.findByType(userId, MemoryType.EPISODIC, memoryLimit);
        var emotional = memoryStore.findByType(userId, MemoryType.EMOTIONAL, 2);
        var semantic = memoryStore.findByType(userId, MemoryType.SEMANTIC, memoryLimit);

        List<String> summaries = new ArrayList<>();
        episodic.forEach(m -> summaries.add(m.content()));
        semantic.forEach(m -> summaries.add(m.content()));

        var emotionalState = emotional.isEmpty() ? "NEUTRAL"
                : emotional.getFirst().content().toUpperCase();

        var engagementScore = episodic.isEmpty() ? 0.5
                : Math.min(1.0, episodic.stream().mapToDouble(m -> m.strength()).average().orElse(0.5));

        var body = Map.<String, Object>of(
                "userId", userId,
                "tenantId", tenantId,
                "recentMemorySummaries", summaries,
                "preferences", Map.of(),
                "emotionalState", emotionalState,
                "engagementScore", engagementScore,
                "fetchedAt", Instant.now().toString()
        );

        log.debug("Personal context assembled userId={} memorySummaries={} emotionalState={}",
                userId, summaries.size(), emotionalState);
        return ResponseEntity.ok(body);
    }
}
