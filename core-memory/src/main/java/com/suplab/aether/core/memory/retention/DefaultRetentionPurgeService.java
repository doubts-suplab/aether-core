package com.suplab.aether.core.memory.retention;

import com.suplab.aether.core.domain.DataCategory;
import com.suplab.aether.core.domain.ErasureEvent;
import com.suplab.aether.core.domain.ErasureScope;
import com.suplab.aether.core.domain.RetentionPurgeResult;
import com.suplab.aether.core.domain.UserPrivacySettings;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import com.suplab.aether.core.ports.ErasureEventStore;
import com.suplab.aether.core.ports.LegalHoldStore;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import com.suplab.aether.core.ports.RetentionPurgePort;
import com.suplab.aether.core.ports.UserPrivacySettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Default {@link RetentionPurgePort} — GDPR storage-limitation (Art. 5(1)(e)).
 *
 * <p>For a user with a configured {@code data_retention_days} window, deletes memories and cognitive
 * sessions older than {@code now - days}, <em>skipping any category under a legal hold</em> exactly as
 * on-request erasure does, and appending a {@code RETENTION}-scope {@link ErasureEvent} for
 * accountability. A user with no window (or {@code 0}) is a no-op. Preferences are current
 * configuration, not history, so they are out of retention scope.</p>
 *
 * <p>The purge is a system action: the recorded event's {@code requestedBy} is {@code retention-policy}.
 * With no holds in place it behaves as a straightforward age-based delete.</p>
 */
public class DefaultRetentionPurgeService implements RetentionPurgePort {

    private static final Logger log = LoggerFactory.getLogger(DefaultRetentionPurgeService.class);

    /** Recorded as the requester on the retention audit event (system-initiated, not a data subject). */
    public static final String REQUESTED_BY = "retention-policy";

    private final PersonalMemoryStore memoryStore;
    private final CognitiveSessionStore sessionStore;
    private final UserPrivacySettingsStore settingsStore;
    private final LegalHoldStore legalHoldStore;
    private final ErasureEventStore erasureEventStore;

    public DefaultRetentionPurgeService(PersonalMemoryStore memoryStore,
                                        CognitiveSessionStore sessionStore,
                                        UserPrivacySettingsStore settingsStore,
                                        LegalHoldStore legalHoldStore,
                                        ErasureEventStore erasureEventStore) {
        this.memoryStore = memoryStore;
        this.sessionStore = sessionStore;
        this.settingsStore = settingsStore;
        this.legalHoldStore = legalHoldStore;
        this.erasureEventStore = erasureEventStore;
    }

    @Override
    public RetentionPurgeResult purgeUser(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        var settings = settingsStore.find(userId).orElse(null);
        if (settings == null || !settings.hasRetentionLimit()) {
            return RetentionPurgeResult.empty();
        }
        return purge(settings);
    }

    @Override
    public RetentionPurgeResult purgeAll() {
        var result = RetentionPurgeResult.empty();
        for (UserPrivacySettings settings : settingsStore.findAllWithRetention()) {
            result = result.plus(purge(settings));
        }
        log.info("Retention purge sweep complete: users={} memories={} sessions={}",
                result.usersPurged(), result.memoriesPurged(), result.sessionsPurged());
        return result;
    }

    private RetentionPurgeResult purge(UserPrivacySettings settings) {
        var userId = settings.userId();
        var cutoff = Instant.now().minus(Duration.ofDays(settings.dataRetentionDays()));
        Set<DataCategory> held = legalHoldStore.heldCategories(userId);

        int memories = held.contains(DataCategory.MEMORIES) ? 0 : memoryStore.deleteOlderThan(userId, cutoff);
        int sessions = held.contains(DataCategory.SESSIONS) ? 0 : sessionStore.deleteOlderThan(userId, cutoff);
        Set<DataCategory> heldInScope = intersection(held, EnumSet.of(DataCategory.MEMORIES, DataCategory.SESSIONS));

        // Record the purge only when it actually deleted something or retained a category under hold —
        // an empty pass over a user with nothing aged out leaves no audit noise.
        if (memories > 0 || sessions > 0 || !heldInScope.isEmpty()) {
            var event = ErasureEvent.of(userId, ErasureScope.RETENTION, memories, sessions, 0,
                    heldInScope, REQUESTED_BY);
            erasureEventStore.record(event);
            log.info("Retention-purged userId={} olderThan={} memories={} sessions={} held={}",
                    userId, cutoff, memories, sessions, heldInScope);
        }
        return new RetentionPurgeResult(1, memories, sessions);
    }

    private static Set<DataCategory> intersection(Set<DataCategory> held, Set<DataCategory> inScope) {
        if (held.isEmpty()) return Set.of();
        var result = EnumSet.copyOf(inScope);
        result.retainAll(held);
        return result;
    }
}
