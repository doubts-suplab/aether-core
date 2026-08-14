package com.suplab.aether.core.memory.export;

import com.suplab.aether.core.domain.PersonalDataExport;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import com.suplab.aether.core.ports.PersonalDataExportPort;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import com.suplab.aether.core.ports.UserPreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link PersonalDataExportPort} — assembles a portable snapshot of a user's Core data.
 *
 * <p>Composes the memory, session, and preference stores into a read-only {@link PersonalDataExport}
 * (Article 20 portability): all memories (active + archived), all cognitive sessions across every
 * tenant, and preferences. The reads are administrative — {@link PersonalMemoryStore#findAllByUser}
 * and {@link CognitiveSessionStore#findAllByUser} do <em>not</em> reinforce, so exporting a user's
 * data never perturbs their memory strengths. A bounded {@link #MAX_EXPORT} caps a single export.</p>
 */
public class DefaultPersonalDataExportService implements PersonalDataExportPort {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersonalDataExportService.class);

    /** Hard ceiling on memories/sessions returned in one export. */
    public static final int MAX_EXPORT = 10_000;

    private final PersonalMemoryStore memoryStore;
    private final CognitiveSessionStore sessionStore;
    private final UserPreferenceStore preferenceStore;

    public DefaultPersonalDataExportService(PersonalMemoryStore memoryStore,
                                            CognitiveSessionStore sessionStore,
                                            UserPreferenceStore preferenceStore) {
        this.memoryStore = memoryStore;
        this.sessionStore = sessionStore;
        this.preferenceStore = preferenceStore;
    }

    @Override
    public PersonalDataExport exportAll(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        var memories = memoryStore.findAllByUser(userId, MAX_EXPORT);
        var sessions = sessionStore.findAllByUser(userId, MAX_EXPORT);
        var preferences = preferenceStore.find(userId);
        var export = PersonalDataExport.of(userId, memories, sessions, preferences);
        log.info("Assembled data export for userId={} memories={} sessions={} preferenceKeys={}",
                userId, memories.size(), sessions.size(), preferences.size());
        return export;
    }
}
