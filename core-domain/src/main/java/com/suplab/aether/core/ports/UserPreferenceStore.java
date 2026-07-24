package com.suplab.aether.core.ports;

import java.util.Map;

/**
 * Port interface for per-user preference storage.
 *
 * <p>Preferences are free-form key/value pairs (e.g. {@code communication-style: async},
 * {@code notification-frequency: daily}) surfaced to Aether Grid inside
 * {@code PersonalContext.preferences} so agents can tailor their behaviour.</p>
 */
public interface UserPreferenceStore {

    /**
     * Returns the user's preferences, or an empty map when none are stored.
     */
    Map<String, Object> find(String userId);

    /**
     * Replaces the user's preference map. Passing an empty map clears all preferences.
     */
    void save(String userId, Map<String, Object> preferences);

    /**
     * Hard-deletes the user's preference record, for GDPR right-to-erasure.
     *
     * @param userId the user whose preferences to erase
     * @return the number of preference rows deleted (0 or 1)
     */
    int deleteByUser(String userId);
}
