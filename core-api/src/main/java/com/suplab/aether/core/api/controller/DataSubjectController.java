package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.api.security.DataSubjectVerifier;
import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.domain.ErasureEvent;
import com.suplab.aether.core.domain.PersonalDataExport;
import com.suplab.aether.core.domain.PersonalMemory;
import com.suplab.aether.core.ports.ErasureEventStore;
import com.suplab.aether.core.ports.PersonalDataErasurePort;
import com.suplab.aether.core.ports.PersonalDataExportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * GDPR data-subject rights over a user's personal data: right to erasure (Article 17) and data
 * portability (Article 20 / CCPA right-to-know).
 *
 * <p>Every path is scoped by {@code userId} — the data subject. Erasure is Core-local and permanent;
 * each operation is written to the append-only erasure audit log, which survives the erased data as
 * proof of compliance. {@code requestedBy} identifies who asked (the subject, or an operator acting on
 * their behalf) and defaults to {@code data-subject}. A category under a legal hold (see
 * {@link LegalHoldController}) is retained rather than deleted and is reported in the event's
 * {@code heldCategories}, so a partial erasure is auditable rather than silent. Export is a read-only
 * portable snapshot of everything Core holds for the user — it never reinforces or mutates the data.</p>
 *
 * <p><strong>Requester identity (GDPR Art. 12(6)).</strong> The destructive erasures and the full
 * export are gated by a {@link DataSubjectVerifier}: when verification is enabled, a request must carry
 * a valid {@code X-Subject-Verification} token bound to the target {@code userId} or the endpoint
 * returns {@code 401}. Verification is off by default, so Core still runs open standalone; the
 * read-only erasure history is never gated.</p>
 */
@RestController
@RequestMapping("/api/v1/users/{userId}")
public class DataSubjectController {

    private static final Logger log = LoggerFactory.getLogger(DataSubjectController.class);

    /** Header carrying the signed proof-of-identity token when verification is enabled. */
    public static final String VERIFICATION_HEADER = "X-Subject-Verification";

    private final PersonalDataErasurePort erasurePort;
    private final ErasureEventStore erasureEventStore;
    private final PersonalDataExportPort exportPort;
    private final DataSubjectVerifier verifier;

    public DataSubjectController(PersonalDataErasurePort erasurePort,
                                ErasureEventStore erasureEventStore,
                                PersonalDataExportPort exportPort,
                                DataSubjectVerifier verifier) {
        this.erasurePort = erasurePort;
        this.erasureEventStore = erasureEventStore;
        this.exportPort = exportPort;
        this.verifier = verifier;
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
            @RequestParam(required = false) String requestedBy,
            @RequestHeader(value = VERIFICATION_HEADER, required = false) String verification) {
        if (!verifier.isVerified(userId, verification)) return unverified(userId);
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
            @RequestParam(required = false) String requestedBy,
            @RequestHeader(value = VERIFICATION_HEADER, required = false) String verification) {
        if (!verifier.isVerified(userId, verification)) return unverified(userId);
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

    /**
     * Exports everything Core holds for the user — memories (active + archived), cognitive sessions
     * across every tenant, and preferences — as a portable, read-only JSON snapshot (Article 20).
     * This read never reinforces or mutates the user's data.
     *
     * @return 200 OK with the export view
     */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export(
            @PathVariable String userId,
            @RequestHeader(value = VERIFICATION_HEADER, required = false) String verification) {
        if (!verifier.isVerified(userId, verification)) return unverified(userId);
        var export = exportPort.exportAll(userId);
        log.info("Exported data for userId={} records={}", userId, export.totalRecords());
        return ResponseEntity.ok(toView(export));
    }

    /**
     * 401 for a sensitive data-subject request that failed identity verification. Records the refusal;
     * never reveals whether the user exists or what data is held.
     */
    private ResponseEntity<Map<String, Object>> unverified(String userId) {
        log.warn("Rejected unverified data-subject request for userId={}", userId);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "subject identity verification required or invalid"));
    }

    private static Map<String, Object> toView(PersonalDataExport export) {
        return Map.of(
                "userId", export.userId(),
                "exportedAt", export.exportedAt().toString(),
                "totalRecords", export.totalRecords(),
                "memories", export.memories().stream().map(DataSubjectController::memoryView).toList(),
                "sessions", export.sessions().stream().map(DataSubjectController::sessionView).toList(),
                "preferences", export.preferences());
    }

    private static Map<String, Object> memoryView(PersonalMemory memory) {
        return Map.of(
                "id", memory.id().toString(),
                "type", memory.type().name(),
                "content", memory.content(),
                "strength", memory.strength(),
                "accessCount", memory.accessCount(),
                "createdAt", memory.createdAt().toString(),
                "lastAccessedAt", memory.lastAccessedAt().toString());
    }

    private static Map<String, Object> sessionView(CognitiveSession session) {
        return Map.of(
                "sessionId", session.sessionId().toString(),
                "tenantId", session.tenantId(),
                "status", session.status().name(),
                "turnSummaries", session.turnSummaries(),
                "emotionalState", session.emotionalState(),
                "engagementScore", session.engagementScore(),
                "startedAt", session.startedAt().toString(),
                "lastActiveAt", session.lastActiveAt().toString());
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
                "heldCategories", event.heldCategories().stream().map(Enum::name).sorted().toList(),
                "requestedBy", event.requestedBy(),
                "erasedAt", event.erasedAt().toString());
    }
}
