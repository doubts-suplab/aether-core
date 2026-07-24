package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.ErasureEvent;
import com.suplab.aether.core.ports.ErasureEventStore;
import com.suplab.aether.core.ports.PersonalDataErasurePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * GDPR data-subject rights over a user's personal data (right to erasure, Article 17).
 *
 * <p>Every path is scoped by {@code userId} — the data subject. Erasure is Core-local and permanent;
 * each operation is written to the append-only erasure audit log, which survives the erased data as
 * proof of compliance. {@code requestedBy} identifies who asked (the subject, or an operator acting on
 * their behalf) and defaults to {@code data-subject}.</p>
 */
@RestController
@RequestMapping("/api/v1/users/{userId}")
public class DataSubjectController {

    private static final Logger log = LoggerFactory.getLogger(DataSubjectController.class);

    private final PersonalDataErasurePort erasurePort;
    private final ErasureEventStore erasureEventStore;

    public DataSubjectController(PersonalDataErasurePort erasurePort,
                                ErasureEventStore erasureEventStore) {
        this.erasurePort = erasurePort;
        this.erasureEventStore = erasureEventStore;
    }

    /**
     * Erases all of the user's personal memories (active + archived), leaving sessions and
     * preferences intact.
     *
     * @return 200 OK with the erasure event view
     */
    @DeleteMapping("/memories")
    public ResponseEntity<Map<String, Object>> eraseMemories(
            @PathVariable String userId,
            @RequestParam(required = false) String requestedBy) {
        var event = erasurePort.eraseMemories(userId, requestedBy);
        log.info("Erased memories for userId={} memories={}", userId, event.memoriesErased());
        return ResponseEntity.ok(toView(event));
    }

    /**
     * Full account erasure: memories (active + archived), cognitive sessions, and preferences.
     *
     * @return 200 OK with the erasure event view
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> eraseAccount(
            @PathVariable String userId,
            @RequestParam(required = false) String requestedBy) {
        var event = erasurePort.eraseAccount(userId, requestedBy);
        log.info("Erased account for userId={} total={}", userId, event.totalErased());
        return ResponseEntity.ok(toView(event));
    }

    /**
     * Returns the user's erasure audit history, most recent first.
     *
     * @return 200 OK with the list of erasure event views
     */
    @GetMapping("/erasures")
    public ResponseEntity<Object> erasureHistory(@PathVariable String userId,
                                                 @RequestParam(defaultValue = "20") int limit) {
        var body = erasureEventStore.findByUser(userId, limit).stream()
                .map(DataSubjectController::toView).toList();
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> toView(ErasureEvent event) {
        return Map.of(
                "erasureId", event.id().toString(),
                "userId", event.userId(),
                "scope", event.scope().name(),
                "memoriesErased", event.memoriesErased(),
                "sessionsErased", event.sessionsErased(),
                "preferencesErased", event.preferencesErased(),
                "totalErased", event.totalErased(),
                "requestedBy", event.requestedBy(),
                "erasedAt", event.erasedAt().toString());
    }
}
