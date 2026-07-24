package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.ErasureEvent;

import java.util.List;

/**
 * Port interface for the append-only erasure audit log.
 *
 * <p>Records proof that a right-to-erasure operation completed (Article 5(2) accountability). The
 * log is write-once: {@link #record} appends, and there is deliberately no update or delete method —
 * the audit trail must outlive the data it describes. Implementations live in {@code core-memory}.</p>
 */
public interface ErasureEventStore {

    /**
     * Appends an erasure event to the audit log.
     *
     * @param event the completed erasure to record
     */
    void record(ErasureEvent event);

    /**
     * Returns a user's erasure events, most recent first.
     *
     * @param userId the data subject
     * @param limit  maximum number of events to return
     * @return the user's erasure history (may be empty)
     */
    List<ErasureEvent> findByUser(String userId, int limit);
}
