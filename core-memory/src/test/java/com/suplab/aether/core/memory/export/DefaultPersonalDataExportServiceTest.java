package com.suplab.aether.core.memory.export;

import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalMemory;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import com.suplab.aether.core.ports.UserPreferenceStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPersonalDataExportServiceTest {

    private static final class FakeMemoryStore implements PersonalMemoryStore {
        Integer lastLimit;
        @Override public void save(PersonalMemory m, float[] e) { }
        @Override public List<PersonalMemory> findSimilar(String u, float[] q, int l) { return List.of(); }
        @Override public List<PersonalMemory> findByType(String u, MemoryType t, int l) { return List.of(); }
        @Override public void delete(UUID id, String u) { }
        @Override public long countByUser(String u) { return 0; }
        @Override public int deleteAllByUser(String userId) { return 0; }
        @Override public List<PersonalMemory> findAllByUser(String u, int l) {
            lastLimit = l;
            return List.of(PersonalMemory.create(u, MemoryType.SEMANTIC, "fact one"),
                    PersonalMemory.create(u, MemoryType.EPISODIC, "event two"));
        }
    }

    private static final class FakeSessionStore implements CognitiveSessionStore {
        @Override public void save(CognitiveSession s) { }
        @Override public Optional<CognitiveSession> findById(UUID id, String u) { return Optional.empty(); }
        @Override public Optional<CognitiveSession> findActive(String t, String u) { return Optional.empty(); }
        @Override public List<CognitiveSession> findByUser(String t, String u, int l) { return List.of(); }
        @Override public int deleteAllByUser(String userId) { return 0; }
        @Override public List<CognitiveSession> findAllByUser(String u, int l) {
            return List.of(CognitiveSession.start("t-1", u));
        }
    }

    private static final class FakePreferenceStore implements UserPreferenceStore {
        @Override public Map<String, Object> find(String u) { return Map.of("theme", "dark"); }
        @Override public void save(String u, Map<String, Object> p) { }
        @Override public int deleteByUser(String userId) { return 0; }
    }

    @Test
    void exportAll_composesMemoriesSessionsAndPreferences() {
        var service = new DefaultPersonalDataExportService(
                new FakeMemoryStore(), new FakeSessionStore(), new FakePreferenceStore());

        var export = service.exportAll("u-1");

        assertThat(export.userId()).isEqualTo("u-1");
        assertThat(export.memories()).hasSize(2);
        assertThat(export.sessions()).hasSize(1);
        assertThat(export.preferences()).containsEntry("theme", "dark");
        assertThat(export.totalRecords()).isEqualTo(4); // 2 + 1 + 1
    }

    @Test
    void exportAll_boundsReadsToMaxExport() {
        var memoryStore = new FakeMemoryStore();
        var service = new DefaultPersonalDataExportService(
                memoryStore, new FakeSessionStore(), new FakePreferenceStore());

        service.exportAll("u-1");

        assertThat(memoryStore.lastLimit).isEqualTo(DefaultPersonalDataExportService.MAX_EXPORT);
    }

    @Test
    void rejectsBlankUser() {
        var service = new DefaultPersonalDataExportService(
                new FakeMemoryStore(), new FakeSessionStore(), new FakePreferenceStore());

        assertThatThrownBy(() -> service.exportAll(" "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userId");
    }
}
