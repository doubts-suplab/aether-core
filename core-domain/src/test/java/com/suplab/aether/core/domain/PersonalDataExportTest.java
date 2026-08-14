package com.suplab.aether.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalDataExportTest {

    private PersonalMemory memory(String userId) {
        return PersonalMemory.create(userId, MemoryType.SEMANTIC, "the sky is blue");
    }

    private CognitiveSession session(String userId) {
        return CognitiveSession.start("t-1", userId);
    }

    @Test
    void of_assemblesSnapshotAndTimestampsNow() {
        var before = Instant.now();
        var export = PersonalDataExport.of("u-1", List.of(memory("u-1")),
                List.of(session("u-1")), Map.of("theme", "dark"));

        assertThat(export.userId()).isEqualTo("u-1");
        assertThat(export.exportedAt()).isAfterOrEqualTo(before);
        assertThat(export.memories()).hasSize(1);
        assertThat(export.sessions()).hasSize(1);
        assertThat(export.preferences()).containsEntry("theme", "dark");
    }

    @Test
    void totalRecords_sumsMemoriesSessionsAndPreferenceKeys() {
        var export = PersonalDataExport.of("u-1",
                List.of(memory("u-1"), memory("u-1")),
                List.of(session("u-1")),
                Map.of("theme", "dark", "locale", "en"));

        assertThat(export.totalRecords()).isEqualTo(5); // 2 + 1 + 2
    }

    @Test
    void nullCollectionsDefaultToEmpty() {
        var export = new PersonalDataExport("u-1", Instant.now(), null, null, null);

        assertThat(export.memories()).isEmpty();
        assertThat(export.sessions()).isEmpty();
        assertThat(export.preferences()).isEmpty();
        assertThat(export.totalRecords()).isZero();
    }

    @Test
    void collectionsAreDefensivelyCopied() {
        var memories = new java.util.ArrayList<>(List.of(memory("u-1")));
        var export = PersonalDataExport.of("u-1", memories, List.of(), Map.of());
        memories.clear();

        assertThat(export.memories()).hasSize(1); // unaffected by external mutation
    }

    @Test
    void nullExportedAtDefaultsToNow() {
        var export = new PersonalDataExport("u-1", null, List.of(), List.of(), Map.of());

        assertThat(export.exportedAt()).isNotNull();
    }

    @Test
    void rejectsBlankUser() {
        assertThatThrownBy(() -> PersonalDataExport.of(" ", List.of(), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userId");
    }
}
