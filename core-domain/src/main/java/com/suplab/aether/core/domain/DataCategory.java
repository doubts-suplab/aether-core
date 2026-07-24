package com.suplab.aether.core.domain;

/**
 * A category of personal data Aether Core holds for a user — the granularity at which a legal /
 * statutory retention hold can block a right-to-erasure request.
 *
 * <p>Each value maps to one backing store: {@code MEMORIES} to personal memories (active + archived),
 * {@code SESSIONS} to cognitive sessions, {@code PREFERENCES} to user preferences. A hold on a
 * category means that data must be <em>retained</em> despite an erasure request — the legal basis for
 * this is jurisdiction-specific (e.g. GDPR Article 17(3), CCPA §1798.105(d), HIPAA/GLBA retention
 * obligations, or an active legal claim).</p>
 */
public enum DataCategory {
    MEMORIES,
    SESSIONS,
    PREFERENCES
}
