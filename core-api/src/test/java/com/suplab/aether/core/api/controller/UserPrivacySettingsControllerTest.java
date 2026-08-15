package com.suplab.aether.core.api.controller;

import com.suplab.aether.core.domain.UserPrivacySettings;
import com.suplab.aether.core.ports.UserPrivacySettingsStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserPrivacySettingsControllerTest {

    private static final class FakeStore implements UserPrivacySettingsStore {
        final Map<String, UserPrivacySettings> byUser = new HashMap<>();
        @Override public Optional<UserPrivacySettings> find(String u) { return Optional.ofNullable(byUser.get(u)); }
        @Override public void save(UserPrivacySettings s) { byUser.put(s.userId(), s); }
        @Override public List<UserPrivacySettings> findAllWithRetention() { return List.of(); }
    }

    @Test
    void get_defaultsToKeepIndefinitelyWhenUnset() {
        var res = new UserPrivacySettingsController(new FakeStore()).get("u-1");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("userId")).isEqualTo("u-1");
        assertThat(res.getBody().get("dataRetentionDays")).isEqualTo(0);
    }

    @Test
    void put_setsWindowAndPersists() {
        var store = new FakeStore();
        var controller = new UserPrivacySettingsController(store);

        var res = controller.put("u-1",
                new UserPrivacySettingsController.PrivacySettingsRequest(90));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(store.byUser.get("u-1").dataRetentionDays()).isEqualTo(90);
        // and it reads back
        assertThat(controller.get("u-1").getBody().get("dataRetentionDays")).isEqualTo(90);
    }

    @Test
    void put_negativeWindowIsBadRequest() {
        var res = new UserPrivacySettingsController(new FakeStore())
                .put("u-1", new UserPrivacySettingsController.PrivacySettingsRequest(-5));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
