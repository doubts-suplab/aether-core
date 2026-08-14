package com.suplab.aether.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A portable snapshot of everything Aether Core holds for a user — the data-portability projection
 * (GDPR Article 20 / CCPA right-to-know).
 *
 * <p>Where {@link ErasureEvent} proves data was destroyed, this record hands the data <em>back</em>:
 * the user's personal memories (active <em>and</em> archived), cognitive sessions across every tenant,
 * and preferences, assembled read-only. Export is an administrative read — it must never reinforce or
 * mutate the memories it returns (unlike cognitive recall).</p>
 *
 * @param userId      the data subject whose data is exported
 * @param exportedAt  when the export was assembled
 * @param memories    the user's personal memories (active + archived), most recent first
 * @param sessions    the user's cognitive sessions across all tenants, most recently active first
 * @param preferences the user's preference map (empty when none)
 */
public record PersonalDataExport(
        String userId,
        Instant exportedAt,
        List<PersonalMemory> memories,
        List<CognitiveSession> sessions,
        Map<String, Object> preferences
) {
    public PersonalDataExport {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
        if (exportedAt == null) exportedAt = Instant.now();
        memories = memories == null ? List.of() : List.copyOf(memories);
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        preferences = preferences == null ? Map.of() : Map.copyOf(preferences);
    }

    /**
     * Factory for a freshly assembled export, timestamped now.
     */
    public static PersonalDataExport of(String userId, List<PersonalMemory> memories,
                                        List<CognitiveSession> sessions, Map<String, Object> preferences) {
        return new PersonalDataExport(userId, Instant.now(), memories, sessions, preferences);
    }

    /**
     * @return the total number of records in this export (memories + sessions + preference keys).
     */
    public int totalRecords() {
        return memories.size() + sessions.size() + preferences.size();
    }
}
