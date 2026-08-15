package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.CognitiveSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for persisting multi-turn cognitive sessions.
 *
 * <p>All lookups are scoped by userId (and tenantId where applicable) — implementations
 * must never return another user's session.</p>
 */
public interface CognitiveSessionStore {

    /**
     * Inserts or updates a session. Saving an ACTIVE session closes any other ACTIVE
     * session the user has in the same tenant — a user holds at most one active session
     * per tenant.
     */
    void save(CognitiveSession session);

    /**
     * Finds a session by its ID, scoped to the owning user.
     */
    Optional<CognitiveSession> findById(UUID sessionId, String userId);

    /**
     * Finds the user's currently ACTIVE session in the given tenant, if any.
     */
    Optional<CognitiveSession> findActive(String tenantId, String userId);

    /**
     * Returns the user's sessions in the tenant, most recently active first.
     */
    List<CognitiveSession> findByUser(String tenantId, String userId, int limit);

    /**
     * Hard-deletes <strong>all</strong> of a user's cognitive sessions across every tenant, for GDPR
     * right-to-erasure. Erasure is a property of the person, not a single tenant relationship.
     *
     * @param userId the user whose sessions to erase
     * @return the number of session rows deleted
     */
    int deleteAllByUser(String userId);

    /**
     * Hard-deletes a user's cognitive sessions — across every tenant — whose last activity is strictly
     * before {@code cutoff}, for the retention purge (GDPR storage-limitation, Art. 5(1)(e)). More
     * recently active sessions are retained.
     *
     * @param userId the user whose aged-out sessions to purge
     * @param cutoff sessions with {@code last_active_at < cutoff} are deleted
     * @return the number of session rows deleted
     */
    int deleteOlderThan(String userId, Instant cutoff);

    /**
     * Returns <strong>all</strong> of a user's cognitive sessions across every tenant, for data
     * portability (Article 20) — most recently active first. Like erasure, export is a property of the
     * person, not a single tenant relationship.
     *
     * @param userId the user whose sessions to export
     * @param limit  maximum number of sessions to return
     * @return the user's sessions across all tenants (may be empty)
     */
    List<CognitiveSession> findAllByUser(String userId, int limit);
}
