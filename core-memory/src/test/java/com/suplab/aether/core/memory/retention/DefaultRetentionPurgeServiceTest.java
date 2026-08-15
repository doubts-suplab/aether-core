package com.suplab.aether.core.memory.retention;

import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.domain.DataCategory;
import com.suplab.aether.core.domain.ErasureEvent;
import com.suplab.aether.core.domain.ErasureScope;
import com.suplab.aether.core.domain.LegalHold;
import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalMemory;
import com.suplab.aether.core.domain.UserPrivacySettings;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import com.suplab.aether.core.ports.ErasureEventStore;
import com.suplab.aether.core.ports.LegalHoldStore;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import com.suplab.aether.core.ports.UserPrivacySettingsStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRetentionPurgeServiceTest {

    private static final class FakeMemoryStore implements PersonalMemoryStore {
        int deleteReturn;
        Instant lastCutoff;
        boolean called;
        @Override public void save(PersonalMemory m, float[] e) { }
        @Override public List<PersonalMemory> findSimilar(String u, float[] q, int l) { return List.of(); }
        @Override public List<PersonalMemory> findByType(String u, MemoryType t, int l) { return List.of(); }
        @Override public void delete(UUID id, String u) { }
        @Override public int deleteAllByUser(String u) { return 0; }
        @Override public long countByUser(String u) { return 0; }
        @Override public int deleteOlderThan(String u, Instant c) { called = true; lastCutoff = c; return deleteReturn; }
        @Override public List<PersonalMemory> findAllByUser(String u, int l) { return List.of(); }
    }

    private static final class FakeSessionStore implements CognitiveSessionStore {
        int deleteReturn;
        boolean called;
        @Override public void save(CognitiveSession s) { }
        @Override public Optional<CognitiveSession> findById(UUID id, String u) { return Optional.empty(); }
        @Override public Optional<CognitiveSession> findActive(String t, String u) { return Optional.empty(); }
        @Override public List<CognitiveSession> findByUser(String t, String u, int l) { return List.of(); }
        @Override public int deleteAllByUser(String u) { return 0; }
        @Override public int deleteOlderThan(String u, Instant c) { called = true; return deleteReturn; }
        @Override public List<CognitiveSession> findAllByUser(String u, int l) { return List.of(); }
    }

    private static final class FakeSettingsStore implements UserPrivacySettingsStore {
        final Map<String, UserPrivacySettings> byUser = new HashMap<>();
        @Override public Optional<UserPrivacySettings> find(String u) { return Optional.ofNullable(byUser.get(u)); }
        @Override public void save(UserPrivacySettings s) { byUser.put(s.userId(), s); }
        @Override public List<UserPrivacySettings> findAllWithRetention() {
            return byUser.values().stream().filter(UserPrivacySettings::hasRetentionLimit).toList();
        }
    }

    private static final class FakeLegalHoldStore implements LegalHoldStore {
        private final Set<DataCategory> held;
        FakeLegalHoldStore(DataCategory... c) {
            this.held = c.length == 0 ? Set.of() : EnumSet.copyOf(List.of(c));
        }
        @Override public void place(LegalHold hold) { }
        @Override public int lift(String u, DataCategory c) { return 0; }
        @Override public Set<DataCategory> heldCategories(String u) { return held; }
        @Override public List<LegalHold> findByUser(String u) { return List.of(); }
    }

    private static final class RecordingEventStore implements ErasureEventStore {
        final List<ErasureEvent> recorded = new ArrayList<>();
        @Override public void record(ErasureEvent e) { recorded.add(e); }
        @Override public List<ErasureEvent> findByUser(String u, int l) { return List.copyOf(recorded); }
    }

    @Test
    void purgeUser_deletesAgedMemoriesAndSessionsAndAudits() {
        var mem = new FakeMemoryStore(); mem.deleteReturn = 4;
        var sess = new FakeSessionStore(); sess.deleteReturn = 2;
        var settings = new FakeSettingsStore();
        settings.save(UserPrivacySettings.of("u-1", 30));
        var audit = new RecordingEventStore();
        var service = new DefaultRetentionPurgeService(mem, sess, settings, new FakeLegalHoldStore(), audit);

        var before = Instant.now().minusSeconds(31L * 86400);
        var result = service.purgeUser("u-1");

        assertThat(mem.called).isTrue();
        assertThat(sess.called).isTrue();
        assertThat(mem.lastCutoff).isBefore(before.plusSeconds(2 * 86400)); // ~30 days ago
        assertThat(result.usersPurged()).isEqualTo(1);
        assertThat(result.memoriesPurged()).isEqualTo(4);
        assertThat(result.sessionsPurged()).isEqualTo(2);
        assertThat(audit.recorded).hasSize(1);
        assertThat(audit.recorded.get(0).scope()).isEqualTo(ErasureScope.RETENTION);
        assertThat(audit.recorded.get(0).requestedBy()).isEqualTo("retention-policy");
    }

    @Test
    void purgeUser_skipsHeldCategoryAndRecordsIt() {
        var mem = new FakeMemoryStore(); mem.deleteReturn = 4;
        var sess = new FakeSessionStore(); sess.deleteReturn = 2;
        var settings = new FakeSettingsStore();
        settings.save(UserPrivacySettings.of("u-1", 30));
        var audit = new RecordingEventStore();
        // memories under a statutory hold — must not be purged
        var service = new DefaultRetentionPurgeService(mem, sess, settings, new FakeLegalHoldStore(DataCategory.MEMORIES), audit);

        var result = service.purgeUser("u-1");

        assertThat(mem.called).isFalse();       // held → not purged
        assertThat(sess.called).isTrue();
        assertThat(result.memoriesPurged()).isZero();
        assertThat(result.sessionsPurged()).isEqualTo(2);
        assertThat(audit.recorded.get(0).heldCategories()).containsExactly(DataCategory.MEMORIES);
    }

    @Test
    void purgeUser_noSettings_isNoOp() {
        var mem = new FakeMemoryStore();
        var sess = new FakeSessionStore();
        var audit = new RecordingEventStore();
        var service = new DefaultRetentionPurgeService(mem, sess, new FakeSettingsStore(),
                new FakeLegalHoldStore(), audit);

        var result = service.purgeUser("u-1");

        assertThat(result.totalPurged()).isZero();
        assertThat(mem.called).isFalse();
        assertThat(audit.recorded).isEmpty();
    }

    @Test
    void purgeUser_zeroWindow_isNoOp() {
        var settings = new FakeSettingsStore();
        settings.save(UserPrivacySettings.of("u-1", 0)); // keep indefinitely
        var mem = new FakeMemoryStore();
        var service = new DefaultRetentionPurgeService(mem, new FakeSessionStore(), settings,
                new FakeLegalHoldStore(), new RecordingEventStore());

        assertThat(service.purgeUser("u-1").totalPurged()).isZero();
        assertThat(mem.called).isFalse();
    }

    @Test
    void purgeAll_aggregatesAcrossUsers() {
        var mem = new FakeMemoryStore(); mem.deleteReturn = 3;
        var sess = new FakeSessionStore(); sess.deleteReturn = 1;
        var settings = new FakeSettingsStore();
        settings.save(UserPrivacySettings.of("u-1", 30));
        settings.save(UserPrivacySettings.of("u-2", 90));
        settings.save(UserPrivacySettings.of("u-3", 0)); // no window → excluded
        var service = new DefaultRetentionPurgeService(mem, sess, settings,
                new FakeLegalHoldStore(), new RecordingEventStore());

        var result = service.purgeAll();

        assertThat(result.usersPurged()).isEqualTo(2);   // u-1, u-2 only
        assertThat(result.memoriesPurged()).isEqualTo(6); // 3 + 3
        assertThat(result.sessionsPurged()).isEqualTo(2); // 1 + 1
    }

    @Test
    void purgeUser_nothingAgedOut_recordsNoEvent() {
        var settings = new FakeSettingsStore();
        settings.save(UserPrivacySettings.of("u-1", 30));
        var audit = new RecordingEventStore();
        var service = new DefaultRetentionPurgeService(new FakeMemoryStore(), new FakeSessionStore(),
                settings, new FakeLegalHoldStore(), audit);

        var result = service.purgeUser("u-1"); // deleteReturn defaults to 0

        assertThat(result.usersPurged()).isEqualTo(1);
        assertThat(result.totalPurged()).isZero();
        assertThat(audit.recorded).isEmpty(); // no audit noise for an empty pass
    }
}
