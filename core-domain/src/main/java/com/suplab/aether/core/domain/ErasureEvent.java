package com.suplab.aether.core.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * An append-only audit record of a completed right-to-erasure operation.
 *
 * <p>GDPR erasure destroys the user's personal data, but a controller must be able to
 * <em>demonstrate</em> that an erasure happened (accountability, Article 5(2)). This record is that
 * proof: it carries only the subject's own {@code userId}, the {@link ErasureScope}, per-store row
 * counts, any categories a legal hold retained, who requested it, and when — <strong>never any memory
 * content</strong>. It is written once and never updated or deleted.</p>
 *
 * <p>{@code heldCategories} makes the audit truthful about partial erasures: when a
 * {@link LegalHold} blocks a category (a retention exception under GDPR Art. 17(3), CCPA
 * §1798.105(d), or sectoral US law such as HIPAA/GLBA), that category is not deleted and is recorded
 * here rather than silently vanishing from the counts.</p>
 *
 * @param id                 stable identifier
 * @param userId             the data subject whose data was erased
 * @param scope              how much was requested ({@code MEMORIES} vs. {@code ACCOUNT})
 * @param memoriesErased     personal-memory rows deleted (active + archived)
 * @param sessionsErased     cognitive-session rows deleted
 * @param preferencesErased  user-preference rows deleted (0 or 1)
 * @param heldCategories     categories retained because a legal hold blocked their erasure (may be empty)
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
        Set<DataCategory> heldCategories,
        String requestedBy,
        Instant erasedAt
) {
    public ErasureEvent {
        if (id == null) id = UUID.randomUUID();
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (scope == null) throw new IllegalArgumentException("scope required");
        if (memoriesErased < 0 || sessionsErased < 0 || preferencesErased < 0)
            throw new IllegalArgumentException("erased counts must be >= 0");
        heldCategories = heldCategories == null || heldCategories.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(heldCategories));
        if (requestedBy == null || requestedBy.isBlank()) requestedBy = "data-subject";
        if (erasedAt == null) erasedAt = Instant.now();
    }

    /**
     * Factory for a freshly completed erasure event with no legal holds: random ID, {@code erasedAt}
     * now, empty {@code heldCategories}.
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
        return of(userId, scope, memoriesErased, sessionsErased, preferencesErased, Set.of(), requestedBy);
    }

    /**
     * Factory for a freshly completed erasure event that may have retained categories under legal
     * hold: random ID, {@code erasedAt} now.
     *
     * @param userId            the data subject
     * @param scope             the erasure scope
     * @param memoriesErased    personal-memory rows deleted
     * @param sessionsErased    cognitive-session rows deleted
     * @param preferencesErased user-preference rows deleted
     * @param heldCategories    categories retained because a legal hold blocked their erasure
     * @param requestedBy       who requested the erasure (defaulted to {@code data-subject} when blank)
     */
    public static ErasureEvent of(String userId, ErasureScope scope, int memoriesErased,
                                  int sessionsErased, int preferencesErased,
                                  Set<DataCategory> heldCategories, String requestedBy) {
        return new ErasureEvent(UUID.randomUUID(), userId, scope, memoriesErased, sessionsErased,
                preferencesErased, heldCategories, requestedBy, Instant.now());
    }

    /**
     * @return the total number of records erased across all stores.
     */
    public int totalErased() {
        return memoriesErased + sessionsErased + preferencesErased;
    }

    /**
     * @return {@code true} when a legal hold retained at least one category, making this a partial
     *         erasure rather than a complete one.
     */
    public boolean hasHolds() {
        return !heldCategories.isEmpty();
    }
}
