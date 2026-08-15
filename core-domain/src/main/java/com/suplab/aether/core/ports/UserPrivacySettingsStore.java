package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.UserPrivacySettings;

import java.util.List;
import java.util.Optional;

/**
 * Port for persisting per-user privacy settings (the retention window).
 *
 * <p>Implementations live in {@code core-memory}; the domain depends only on this interface.</p>
 */
public interface UserPrivacySettingsStore {

    /**
     * Returns the user's privacy settings, or empty when none have been configured.
     */
    Optional<UserPrivacySettings> find(String userId);

    /**
     * Inserts or updates the user's privacy settings (upsert by {@code userId}).
     */
    void save(UserPrivacySettings settings);

    /**
     * Returns every user's settings that declare an automatic retention window
     * ({@code data_retention_days > 0}) — the working set for the retention purge sweep.
     */
    List<UserPrivacySettings> findAllWithRetention();
}
