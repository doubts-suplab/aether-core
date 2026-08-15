package com.suplab.aether.core.domain;

/**
 * The breadth of a GDPR right-to-erasure (Article 17) request against Aether Core.
 *
 * <ul>
 *   <li>{@code MEMORIES} — erase the user's personal memories only (active + archived), leaving
 *       their cognitive sessions and preferences intact.</li>
 *   <li>{@code ACCOUNT} — full account erasure: memories (active + archived), cognitive sessions,
 *       and preferences — every record Core holds for the user.</li>
 *   <li>{@code RETENTION} — a system-initiated retention purge (GDPR storage-limitation, Art. 5(1)(e)):
 *       memories and sessions <em>older than</em> the user's {@code data_retention_days} window are
 *       deleted. Age-based, not a delete-everything request; preferences (current config, not history)
 *       are out of scope.</li>
 * </ul>
 */
public enum ErasureScope {
    MEMORIES,
    ACCOUNT,
    RETENTION
}
