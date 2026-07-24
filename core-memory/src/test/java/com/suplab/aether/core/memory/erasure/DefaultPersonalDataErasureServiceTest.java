package com.suplab.aether.core.memory.erasure;

import com.suplab.aether.core.domain.CognitiveSession;
import com.suplab.aether.core.domain.DataCategory;
import com.suplab.aether.core.domain.ErasureEvent;
import com.suplab.aether.core.domain.ErasureScope;
import com.suplab.aether.core.domain.LegalHold;
import com.suplab.aether.core.domain.MemoryType;
import com.suplab.aether.core.domain.PersonalMemory;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import com.suplab.aether.core.ports.ErasureEventStore;
import com.suplab.aether.core.ports.LegalHoldStore;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import com.suplab.aether.core.ports.UserPreferenceStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPersonalDataErasureServiceTest {

    private static final class FakeMemoryStore implements PersonalMemoryStore {
        int deleteReturn;
        boolean deleteCalled;
        @Override public void save(PersonalMemory m, float[] e) { }
        @Override public List<PersonalMemory> findSimilar(String u, float[] q, int l) { return List.of(); }
        @Override public List<PersonalMemory> findByType(String u, MemoryType t, int l) { return List.of(); }
        @Override public void delete(UUID id, String u) { }
        @Override public long countByUser(String u) { return 0; }
        @Override public int deleteAllByUser(String userId) { deleteCalled = true; return deleteReturn; }
    }

    private static final class FakeSessionStore implements CognitiveSessionStore {
        int deleteReturn;
        boolean deleteCalled;
        @Override public void save(CognitiveSession s) { }
        @Override public Optional<CognitiveSession> findById(UUID id, String u) { return Optional.empty(); }
        @Override public Optional<CognitiveSession> findActive(String t, String u) { return Optional.empty(); }
        @Override public List<CognitiveSession> findByUser(String t, String u, int l) { return List.of(); }
        @Override public int deleteAllByUser(String userId) { deleteCalled = true; return deleteReturn; }
    }

    private static final class FakePreferenceStore implements UserPreferenceStore {
        int deleteReturn;
        boolean deleteCalled;
        @Override public Map<String, Object> find(String u) { return Map.of(); }
        @Override public void save(String u, Map<String, Object> p) { }
        @Override public int deleteByUser(String userId) { deleteCalled = true; return deleteReturn; }
    }

    private static final class RecordingEventStore implements ErasureEventStore {
        final List<ErasureEvent> recorded = new ArrayList<>();
        @Override public void record(ErasureEvent event) { recorded.add(event); }
        @Override public List<ErasureEvent> findByUser(String u, int l) { return List.copyOf(recorded); }
    }

    private static final class FakeLegalHoldStore implements LegalHoldStore {
        private final Set<DataCategory> held;
        FakeLegalHoldStore(DataCategory... categories) {
            this.held = categories.length == 0 ? Set.of() : EnumSet.copyOf(List.of(categories));
        }
        @Override public void place(LegalHold hold) { }
        @Override public int lift(String u, DataCategory c) { return 0; }
        @Override public Set<DataCategory> heldCategories(String userId) { return held; }
        @Override public List<LegalHold> findByUser(String u) { return List.of(); }
    }

    @Test
    void eraseMemories_deletesOnlyMemoriesAndAuditsThem() {
        var mem = new FakeMemoryStore(); mem.deleteReturn = 5;
        var sess = new FakeSessionStore();
        var pref = new FakePreferenceStore();
        var audit = new RecordingEventStore();
        var service = new DefaultPersonalDataErasureService(mem, sess, pref, audit, new FakeLegalHoldStore());

        var event = service.eraseMemories("u-1", "self");

        assertThat(mem.deleteCalled).isTrue();
        assertThat(sess.deleteCalled).isFalse();   // memories-only leaves sessions
        assertThat(pref.deleteCalled).isFalse();   // and preferences
        assertThat(event.scope()).isEqualTo(ErasureScope.MEMORIES);
        assertThat(event.memoriesErased()).isEqualTo(5);
        assertThat(event.totalErased()).isEqualTo(5);
        assertThat(event.heldCategories()).isEmpty();
        assertThat(audit.recorded).containsExactly(event);
    }

    @Test
    void eraseAccount_deletesEverythingAndAuditsCounts() {
        var mem = new FakeMemoryStore(); mem.deleteReturn = 9;
        var sess = new FakeSessionStore(); sess.deleteReturn = 3;
        var pref = new FakePreferenceStore(); pref.deleteReturn = 1;
        var audit = new RecordingEventStore();
        var service = new DefaultPersonalDataErasureService(mem, sess, pref, audit, new FakeLegalHoldStore());

        var event = service.eraseAccount("u-1", null);

        assertThat(mem.deleteCalled).isTrue();
        assertThat(sess.deleteCalled).isTrue();
        assertThat(pref.deleteCalled).isTrue();
        assertThat(event.scope()).isEqualTo(ErasureScope.ACCOUNT);
        assertThat(event.memoriesErased()).isEqualTo(9);
        assertThat(event.sessionsErased()).isEqualTo(3);
        assertThat(event.preferencesErased()).isEqualTo(1);
        assertThat(event.totalErased()).isEqualTo(13);
        assertThat(event.heldCategories()).isEmpty();
        assertThat(event.requestedBy()).isEqualTo("data-subject"); // null defaulted
        assertThat(audit.recorded).hasSize(1);
    }

    @Test
    void eraseAccount_skipsHeldCategoryAndRecordsIt() {
        var mem = new FakeMemoryStore(); mem.deleteReturn = 9;
        var sess = new FakeSessionStore(); sess.deleteReturn = 3;
        var pref = new FakePreferenceStore(); pref.deleteReturn = 1;
        var audit = new RecordingEventStore();
        // sessions are under a statutory retention hold
        var service = new DefaultPersonalDataErasureService(mem, sess, pref, audit,
                new FakeLegalHoldStore(DataCategory.SESSIONS));

        var event = service.eraseAccount("u-1", "ops@acme");

        assertThat(mem.deleteCalled).isTrue();
        assertThat(sess.deleteCalled).isFalse();   // held → not deleted
        assertThat(pref.deleteCalled).isTrue();
        assertThat(event.memoriesErased()).isEqualTo(9);
        assertThat(event.sessionsErased()).isZero();
        assertThat(event.preferencesErased()).isEqualTo(1);
        assertThat(event.heldCategories()).containsExactly(DataCategory.SESSIONS);
        assertThat(event.hasHolds()).isTrue();
    }

    @Test
    void eraseMemories_skipsMemoriesWhenTheyAreHeld() {
        var mem = new FakeMemoryStore(); mem.deleteReturn = 5;
        var service = new DefaultPersonalDataErasureService(mem, new FakeSessionStore(),
                new FakePreferenceStore(), new RecordingEventStore(),
                new FakeLegalHoldStore(DataCategory.MEMORIES));

        var event = service.eraseMemories("u-1", "self");

        assertThat(mem.deleteCalled).isFalse();    // memories held → nothing deleted
        assertThat(event.memoriesErased()).isZero();
        assertThat(event.heldCategories()).containsExactly(DataCategory.MEMORIES);
    }

    @Test
    void eraseMemories_ignoresHoldsOnOtherCategories() {
        var mem = new FakeMemoryStore(); mem.deleteReturn = 5;
        // a hold on SESSIONS must not affect a memories-only erasure
        var service = new DefaultPersonalDataErasureService(mem, new FakeSessionStore(),
                new FakePreferenceStore(), new RecordingEventStore(),
                new FakeLegalHoldStore(DataCategory.SESSIONS));

        var event = service.eraseMemories("u-1", "self");

        assertThat(mem.deleteCalled).isTrue();
        assertThat(event.memoriesErased()).isEqualTo(5);
        assertThat(event.heldCategories()).isEmpty();
    }

    @Test
    void rejectsBlankUser() {
        var service = new DefaultPersonalDataErasureService(
                new FakeMemoryStore(), new FakeSessionStore(), new FakePreferenceStore(),
                new RecordingEventStore(), new FakeLegalHoldStore());
        assertThatThrownBy(() -> service.eraseAccount(" ", "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userId");
    }
}
