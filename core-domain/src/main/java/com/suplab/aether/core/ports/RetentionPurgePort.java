package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.RetentionPurgeResult;

/**
 * Driving port for the retention purge — GDPR storage-limitation (Art. 5(1)(e)).
 *
 * <p>Deletes a user's memories and cognitive sessions older than their configured
 * {@code data_retention_days} window, skipping any category under a legal hold and recording a
 * {@code RETENTION}-scope {@link com.suplab.aether.core.domain.ErasureEvent} for accountability. Unlike
 * on-request erasure, this is age-based and system-initiated; preferences (current config, not history)
 * are out of scope.</p>
 */
public interface RetentionPurgePort {

    /**
     * Purges a single user's aged-out data, honouring their retention window and any legal holds.
     * A no-op (empty result) when the user has no retention window configured.
     *
     * @param userId the data subject
     * @return the counts deleted for this user
     */
    RetentionPurgeResult purgeUser(String userId);

    /**
     * Runs the purge for every user with a retention window configured, returning the aggregate.
     */
    RetentionPurgeResult purgeAll();
}
