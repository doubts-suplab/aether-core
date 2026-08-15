package com.suplab.aether.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPrivacySettingsTest {

    @Test
    void of_setsWindowAndTimestampsNow() {
        var before = Instant.now();
        var settings = UserPrivacySettings.of("u-1", 30);

        assertThat(settings.userId()).isEqualTo("u-1");
        assertThat(settings.dataRetentionDays()).isEqualTo(30);
        assertThat(settings.updatedAt()).isAfterOrEqualTo(before);
        assertThat(settings.hasRetentionLimit()).isTrue();
    }

    @Test
    void zeroWindowMeansKeepIndefinitely() {
        assertThat(UserPrivacySettings.of("u-1", 0).hasRetentionLimit()).isFalse();
    }

    @Test
    void rejectsBlankUser() {
        assertThatThrownBy(() -> UserPrivacySettings.of(" ", 30))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("userId");
    }

    @Test
    void rejectsNegativeWindow() {
        assertThatThrownBy(() -> UserPrivacySettings.of("u-1", -1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dataRetentionDays");
    }

    @Test
    void nullTimestampDefaultsToNow() {
        var settings = new UserPrivacySettings("u-1", 30, null);
        assertThat(settings.updatedAt()).isNotNull();
    }
}
