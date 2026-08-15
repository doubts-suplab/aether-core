package com.suplab.aether.core.domain;

import java.time.Instant;

/**
 * Per-user privacy configuration governing automated retention.
 *
 * <p>{@code dataRetentionDays} is the storage-limitation window (GDPR Art. 5(1)(e)): a scheduled
 * retention purge deletes the user's memories and cognitive sessions older than this many days. A value
 * of {@code 0} means <strong>no automatic retention limit</strong> — the user's data is kept until an
 * explicit erasure. Legal holds still take precedence: a held category is never purged, exactly as in
 * on-request erasure.</p>
 *
 * @param userId            the data subject
 * @param dataRetentionDays retention window in days ({@code 0} = keep indefinitely)
 * @param updatedAt         when the setting was last changed
 */
public record UserPrivacySettings(String userId, int dataRetentionDays, Instant updatedAt) {

    public UserPrivacySettings {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (dataRetentionDays < 0) throw new IllegalArgumentException("dataRetentionDays must be >= 0");
        if (updatedAt == null) updatedAt = Instant.now();
    }

    /**
     * Factory for a freshly set privacy configuration, timestamped now.
     */
    public static UserPrivacySettings of(String userId, int dataRetentionDays) {
        return new UserPrivacySettings(userId, dataRetentionDays, Instant.now());
    }

    /**
     * @return {@code true} when an automatic retention window is configured (days &gt; 0).
     */
    public boolean hasRetentionLimit() {
        return dataRetentionDays > 0;
    }
}
