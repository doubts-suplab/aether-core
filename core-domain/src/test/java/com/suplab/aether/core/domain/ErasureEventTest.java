package com.suplab.aether.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErasureEventTest {

    @Test
    void ofBuildsAnEventWithIdAndTimestamp() {
        var event = ErasureEvent.of("u-1", ErasureScope.ACCOUNT, 7, 2, 1, "ops@acme");

        assertThat(event.id()).isNotNull();
        assertThat(event.userId()).isEqualTo("u-1");
        assertThat(event.scope()).isEqualTo(ErasureScope.ACCOUNT);
        assertThat(event.memoriesErased()).isEqualTo(7);
        assertThat(event.sessionsErased()).isEqualTo(2);
        assertThat(event.preferencesErased()).isEqualTo(1);
        assertThat(event.requestedBy()).isEqualTo("ops@acme");
        assertThat(event.erasedAt()).isNotNull();
        assertThat(event.totalErased()).isEqualTo(10);
    }

    @Test
    void requestedByDefaultsToDataSubjectWhenBlank() {
        assertThat(ErasureEvent.of("u-1", ErasureScope.MEMORIES, 3, 0, 0, " ").requestedBy())
                .isEqualTo("data-subject");
    }

    @Test
    void rejectsBlankUserAndNegativeCounts() {
        assertThatThrownBy(() -> ErasureEvent.of(" ", ErasureScope.MEMORIES, 0, 0, 0, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userId");
        assertThatThrownBy(() -> ErasureEvent.of("u", ErasureScope.MEMORIES, -1, 0, 0, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("counts");
    }

    @Test
    void rejectsNullScope() {
        assertThatThrownBy(() -> ErasureEvent.of("u", null, 0, 0, 0, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scope");
    }

    @Test
    void noHoldsByDefaultAndHeldCategoriesAreRecorded() {
        var plain = ErasureEvent.of("u-1", ErasureScope.ACCOUNT, 5, 2, 1, "self");
        assertThat(plain.heldCategories()).isEmpty();
        assertThat(plain.hasHolds()).isFalse();

        var partial = ErasureEvent.of("u-1", ErasureScope.ACCOUNT, 5, 0, 1,
                java.util.Set.of(DataCategory.SESSIONS), "self");
        assertThat(partial.heldCategories()).containsExactly(DataCategory.SESSIONS);
        assertThat(partial.hasHolds()).isTrue();
    }

    @Test
    void heldCategoriesSetIsImmutable() {
        var event = ErasureEvent.of("u-1", ErasureScope.MEMORIES, 0, 0, 0,
                java.util.Set.of(DataCategory.MEMORIES), "self");
        assertThatThrownBy(() -> event.heldCategories().add(DataCategory.SESSIONS))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
