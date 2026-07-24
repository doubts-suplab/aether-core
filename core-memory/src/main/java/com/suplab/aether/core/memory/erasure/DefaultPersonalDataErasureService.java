package com.suplab.aether.core.memory.erasure;

import com.suplab.aether.core.domain.DataCategory;
import com.suplab.aether.core.domain.ErasureEvent;
import com.suplab.aether.core.domain.ErasureScope;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import com.suplab.aether.core.ports.ErasureEventStore;
import com.suplab.aether.core.ports.LegalHoldStore;
import com.suplab.aether.core.ports.PersonalDataErasurePort;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import com.suplab.aether.core.ports.UserPreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Set;

/**
 * Default {@link PersonalDataErasurePort} — GDPR right-to-erasure across a user's personal data,
 * honouring legal / statutory retention holds.
 *
 * <p>Composes the memory, session, and preference stores plus the {@link LegalHoldStore}. Before
 * deleting any category the service consults {@link LegalHoldStore#heldCategories(String)} and skips
 * every held category; only <em>unheld</em> data is erased, and the recorded {@link ErasureEvent}
 * names the categories that were retained. This is how a single erasure path satisfies the retention
 * exceptions that exist across privacy regimes — GDPR Article 17(3), CCPA §1798.105(d), and the
 * retention mandates of sectoral US law (HIPAA, GLBA) — rather than being EU-only.</p>
 *
 * <p>Each operation appends its {@link ErasureEvent} to the audit log <em>after</em> deletion, so the
 * record proves what was erased (and what was held) even though the data itself is gone. Erasure is
 * Core-local — no downstream notification is needed because Grid reads personal context live. With no
 * holds in place the service behaves exactly as an unconditional erasure.</p>
 */
public class DefaultPersonalDataErasureService implements PersonalDataErasurePort {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersonalDataErasureService.class);

    private final PersonalMemoryStore memoryStore;
    private final CognitiveSessionStore sessionStore;
    private final UserPreferenceStore preferenceStore;
    private final ErasureEventStore erasureEventStore;
    private final LegalHoldStore legalHoldStore;

    public DefaultPersonalDataErasureService(PersonalMemoryStore memoryStore,
                                             CognitiveSessionStore sessionStore,
                                             UserPreferenceStore preferenceStore,
                                             ErasureEventStore erasureEventStore,
                                             LegalHoldStore legalHoldStore) {
        this.memoryStore = memoryStore;
        this.sessionStore = sessionStore;
        this.preferenceStore = preferenceStore;
        this.erasureEventStore = erasureEventStore;
        this.legalHoldStore = legalHoldStore;
    }

    @Override
    public ErasureEvent eraseMemories(String userId, String requestedBy) {
        requireUser(userId);
        Set<DataCategory> held = legalHoldStore.heldCategories(userId);

        int memories = held.contains(DataCategory.MEMORIES) ? 0 : memoryStore.deleteAllByUser(userId);
        Set<DataCategory> heldInScope = intersection(held, EnumSet.of(DataCategory.MEMORIES));

        var event = ErasureEvent.of(userId, ErasureScope.MEMORIES, memories, 0, 0, heldInScope, requestedBy);
        erasureEventStore.record(event);
        log.info("Erased memories for userId={} requestedBy={} memories={} held={}",
                userId, event.requestedBy(), memories, heldInScope);
        return event;
    }

    @Override
    public ErasureEvent eraseAccount(String userId, String requestedBy) {
        requireUser(userId);
        Set<DataCategory> held = legalHoldStore.heldCategories(userId);

        int memories = held.contains(DataCategory.MEMORIES) ? 0 : memoryStore.deleteAllByUser(userId);
        int sessions = held.contains(DataCategory.SESSIONS) ? 0 : sessionStore.deleteAllByUser(userId);
        int preferences = held.contains(DataCategory.PREFERENCES) ? 0 : preferenceStore.deleteByUser(userId);
        Set<DataCategory> heldInScope = intersection(held, EnumSet.allOf(DataCategory.class));

        var event = ErasureEvent.of(userId, ErasureScope.ACCOUNT, memories, sessions, preferences,
                heldInScope, requestedBy);
        erasureEventStore.record(event);
        log.info("Erased account for userId={} requestedBy={} memories={} sessions={} preferences={} held={}",
                userId, event.requestedBy(), memories, sessions, preferences, heldInScope);
        return event;
    }

    private static Set<DataCategory> intersection(Set<DataCategory> held, Set<DataCategory> inScope) {
        if (held.isEmpty()) return Set.of();
        var result = EnumSet.copyOf(inScope);
        result.retainAll(held);
        return result;
    }

    private static void requireUser(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
    }
}
