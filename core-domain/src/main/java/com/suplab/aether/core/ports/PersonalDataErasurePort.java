package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.ErasureEvent;

/**
 * Port for GDPR right-to-erasure (Article 17) over a user's personal data.
 *
 * <p>Erasure is <em>Core-local</em>: Core is the system of record for personal memory, and Grid pulls
 * personal context live, so once Core deletes a user's data the next context request simply returns
 * nothing — no cross-service propagation is required. Every erasure appends an {@link ErasureEvent} to
 * the audit log so the operation can be demonstrated after the data is gone. Implementations live in
 * {@code core-memory}.</p>
 */
public interface PersonalDataErasurePort {

    /**
     * Erases all of a user's personal memories (active + archived), leaving sessions and preferences
     * intact, and records the erasure.
     *
     * @param userId      the data subject
     * @param requestedBy who requested the erasure (defaulted when blank)
     * @return the recorded erasure event (with per-store counts)
     */
    ErasureEvent eraseMemories(String userId, String requestedBy);

    /**
     * Full account erasure: memories (active + archived), cognitive sessions, and preferences — every
     * record Core holds for the user — and records the erasure.
     *
     * @param userId      the data subject
     * @param requestedBy who requested the erasure (defaulted when blank)
     * @return the recorded erasure event (with per-store counts)
     */
    ErasureEvent eraseAccount(String userId, String requestedBy);
}
