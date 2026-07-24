package com.suplab.aether.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An append-only audit record of a completed right-to-erasure operation.
 *
 * <p>GDPR erasure destroys the user's personal data, but a controller must be able to
 * <em>demonstrate</em> that an erasure happened (accountability, Article 5(2)). This record is that
 * proof: it carries only the subject's own {@code userId}, the {@link ErasureScope}, per-store row
 * counts, who requested it, and when — <strong>never any memory content</strong>. It is written once
 * and never updated or deleted.</p>
 *
 * @param id                 stable identifier
 * @param userId             the data subject whose data was erased
 * @param scope              how much was erased ({@code MEMORIES} vs. {@code ACCOUNT})
 * @param memoriesErased     personal-memory rows deleted (active + archived)
 * @param sessionsErased     cognitive-session rows deleted
 * @param preferencesErased  user-preference rows deleted (0 or 1)
 * @param requestedBy        who requested the erasure (the subject, or an operator on their behalf)
 * @param erasedAt           when the erasure completed
 */
public record ErasureEvent(
        UUID id,
        String userId,
        ErasureScope scope,
        int memoriesErased,
        int sessionsErased,
        int preferencesErased,
        String requestedBy,
        Instant erasedAt
) {
    public ErasureEvent {
        if (id == null) id = UUID.randomUUID();
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (scope == null) throw new IllegalArgumentException("scope required");
        if (memoriesErased < 0 || sessionsErased < 0 || preferencesErased < 0)
            throw new IllegalArgumentException("erased counts must be >= 0");
        if (requestedBy == null || requestedBy.isBlank()) requestedBy = "data-subject";
        if (erasedAt == null) erasedAt = Instant.now();
    }

    /**
     * Factory for a freshly completed erasure event: random ID, {@code erasedAt} now.
     *
     * @param userId            the data subject
     * @param scope             the erasure scope
     * @param memoriesErased    personal-memory rows deleted
     * @param sessionsErased    cognitive-session rows deleted
     * @param preferencesErased user-preference rows deleted
     * @param requestedBy       who requested the erasure (defaulted to {@code data-subject} when blank)
     */
    public static ErasureEvent of(String userId, ErasureScope scope, int memoriesErased,
                                  int sessionsErased, int preferencesErased, String requestedBy) {
        return new ErasureEvent(UUID.randomUUID(), userId, scope, memoriesErased, sessionsErased,
                preferencesErased, requestedBy, Instant.now());
    }

    /**
     * @return the total number of records erased across all stores.
     */
    public int totalErased() {
        return memoriesErased + sessionsErased + preferencesErased;
    }
}
