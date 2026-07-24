package com.suplab.aether.core.domain;

import java.time.Instant;

/**
 * A legal / statutory retention hold placed on one {@link DataCategory} of a user's personal data.
 *
 * <p>While a hold is in place, a right-to-erasure request must <em>not</em> delete that category —
 * the controller is legally required (or permitted) to retain it. This is how Aether Core honours
 * retention exceptions that exist across privacy regimes: GDPR Article 17(3) (legal claims, legal
 * obligation), CCPA §1798.105(d) (transaction completion, security, legal compliance), and the
 * retention mandates of sectoral US law (HIPAA, GLBA). Erasure of every <em>unheld</em> category
 * still proceeds; the audit event records which categories were held back.</p>
 *
 * @param userId    the data subject the hold protects
 * @param category  the category of data that must be retained
 * @param reason    the legal basis for the hold (e.g. "active litigation", "HIPAA 6-year retention")
 * @param placedBy  who placed the hold (legal / compliance operator)
 * @param placedAt  when the hold was placed
 */
public record LegalHold(
        String userId,
        DataCategory category,
        String reason,
        String placedBy,
        Instant placedAt
) {
    public LegalHold {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (category == null) throw new IllegalArgumentException("category required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason required");
        if (placedBy == null || placedBy.isBlank()) throw new IllegalArgumentException("placedBy required");
        if (placedAt == null) placedAt = Instant.now();
    }

    /**
     * Factory for a freshly placed hold, timestamped now.
     *
     * @param userId   the data subject
     * @param category the category to retain
     * @param reason   the legal basis
     * @param placedBy who placed the hold
     */
    public static LegalHold of(String userId, DataCategory category, String reason, String placedBy) {
        return new LegalHold(userId, category, reason, placedBy, Instant.now());
    }
}
