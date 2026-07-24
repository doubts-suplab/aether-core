package com.suplab.aether.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegalHoldTest {

    @Test
    void ofBuildsAHoldWithTimestamp() {
        var hold = LegalHold.of("u-1", DataCategory.SESSIONS, "active litigation", "legal@acme");

        assertThat(hold.userId()).isEqualTo("u-1");
        assertThat(hold.category()).isEqualTo(DataCategory.SESSIONS);
        assertThat(hold.reason()).isEqualTo("active litigation");
        assertThat(hold.placedBy()).isEqualTo("legal@acme");
        assertThat(hold.placedAt()).isNotNull();
    }

    @Test
    void rejectsBlankUserCategoryReasonAndPlacedBy() {
        assertThatThrownBy(() -> LegalHold.of(" ", DataCategory.MEMORIES, "r", "p"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userId");
        assertThatThrownBy(() -> LegalHold.of("u", null, "r", "p"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("category");
        assertThatThrownBy(() -> LegalHold.of("u", DataCategory.MEMORIES, " ", "p"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reason");
        assertThatThrownBy(() -> LegalHold.of("u", DataCategory.MEMORIES, "r", " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("placedBy");
    }
}
