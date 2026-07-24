package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.DataCategory;
import com.suplab.aether.core.domain.LegalHold;

import java.util.List;
import java.util.Set;

/**
 * Port for the legal / statutory retention holds that gate right-to-erasure.
 *
 * <p>The erasure service consults {@link #heldCategories(String)} before deleting each category and
 * skips any that are held, so retention exceptions (GDPR Art. 17(3), CCPA §1798.105(d), HIPAA/GLBA
 * mandates, active litigation) are honoured uniformly regardless of jurisdiction. Holds are placed
 * and lifted out-of-band by legal / compliance operators. Core runs standalone: with no holds the
 * store returns an empty set and erasure behaves exactly as before. Implementations live in
 * {@code core-memory}.</p>
 */
public interface LegalHoldStore {

    /**
     * Places (or replaces) a hold on a category for a user. Idempotent per {@code (userId, category)}:
     * placing a hold on an already-held category refreshes its reason, placer, and timestamp.
     *
     * @param hold the hold to place
     */
    void place(LegalHold hold);

    /**
     * Lifts a hold, allowing the category to be erased again.
     *
     * @param userId   the data subject
     * @param category the category to release
     * @return the number of holds removed (0 or 1)
     */
    int lift(String userId, DataCategory category);

    /**
     * Returns the categories currently held for a user — the categories a right-to-erasure request
     * must retain.
     *
     * @param userId the data subject
     * @return the held categories (empty when nothing is held)
     */
    Set<DataCategory> heldCategories(String userId);

    /**
     * Returns all holds currently placed for a user, most recent first.
     *
     * @param userId the data subject
     * @return the user's active holds (may be empty)
     */
    List<LegalHold> findByUser(String userId);
}
