package com.suplab.aether.core.memory.erasure;

import com.suplab.aether.core.domain.ErasureEvent;
import com.suplab.aether.core.domain.ErasureScope;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import com.suplab.aether.core.ports.ErasureEventStore;
import com.suplab.aether.core.ports.PersonalDataErasurePort;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import com.suplab.aether.core.ports.UserPreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link PersonalDataErasurePort} — GDPR right-to-erasure across a user's personal data.
 *
 * <p>Composes the memory, session, and preference stores. {@link #eraseMemories} destroys only the
 * user's memories (active + archived); {@link #eraseAccount} destroys everything Core holds for the
 * user. Each operation appends an {@link ErasureEvent} to the audit log <em>after</em> deletion, so
 * the record proves what was erased even though the data itself is gone. Erasure is Core-local — no
 * downstream notification is needed because Grid reads personal context live.</p>
 */
public class DefaultPersonalDataErasureService implements PersonalDataErasurePort {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersonalDataErasureService.class);

    private final PersonalMemoryStore memoryStore;
    private final CognitiveSessionStore sessionStore;
    private final UserPreferenceStore preferenceStore;
    private final ErasureEventStore erasureEventStore;

    public DefaultPersonalDataErasureService(PersonalMemoryStore memoryStore,
                                             CognitiveSessionStore sessionStore,
                                             UserPreferenceStore preferenceStore,
                                             ErasureEventStore erasureEventStore) {
        this.memoryStore = memoryStore;
        this.sessionStore = sessionStore;
        this.preferenceStore = preferenceStore;
        this.erasureEventStore = erasureEventStore;
    }

    @Override
    public ErasureEvent eraseMemories(String userId, String requestedBy) {
        requireUser(userId);
        int memories = memoryStore.deleteAllByUser(userId);
        var event = ErasureEvent.of(userId, ErasureScope.MEMORIES, memories, 0, 0, requestedBy);
        erasureEventStore.record(event);
        log.info("Erased memories for userId={} requestedBy={} memories={}",
                userId, event.requestedBy(), memories);
        return event;
    }

    @Override
    public ErasureEvent eraseAccount(String userId, String requestedBy) {
        requireUser(userId);
        int memories = memoryStore.deleteAllByUser(userId);
        int sessions = sessionStore.deleteAllByUser(userId);
        int preferences = preferenceStore.deleteByUser(userId);
        var event = ErasureEvent.of(userId, ErasureScope.ACCOUNT, memories, sessions, preferences, requestedBy);
        erasureEventStore.record(event);
        log.info("Erased account for userId={} requestedBy={} memories={} sessions={} preferences={}",
                userId, event.requestedBy(), memories, sessions, preferences);
        return event;
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
    }
}
