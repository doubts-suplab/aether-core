package com.suplab.aether.core.memory.retention;

import com.suplab.aether.core.domain.UserPrivacySettings;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcUserPrivacySettingsStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_core_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcUserPrivacySettingsStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcUserPrivacySettingsStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void save_thenFind_roundTrip() {
        var userId = "user-" + UUID.randomUUID();
        store.save(UserPrivacySettings.of(userId, 45));

        var found = store.find(userId);
        assertThat(found).isPresent();
        assertThat(found.get().dataRetentionDays()).isEqualTo(45);
    }

    @Test
    void save_upsertsByUser() {
        var userId = "user-" + UUID.randomUUID();
        store.save(UserPrivacySettings.of(userId, 30));
        store.save(UserPrivacySettings.of(userId, 90));

        assertThat(store.find(userId)).map(UserPrivacySettings::dataRetentionDays).contains(90);
    }

    @Test
    void findAllWithRetention_returnsOnlyPositiveWindows() {
        var withWindow = "user-" + UUID.randomUUID();
        var noWindow = "user-" + UUID.randomUUID();
        store.save(UserPrivacySettings.of(withWindow, 60));
        store.save(UserPrivacySettings.of(noWindow, 0)); // keep indefinitely — excluded

        var all = store.findAllWithRetention();
        assertThat(all).extracting(UserPrivacySettings::userId).contains(withWindow).doesNotContain(noWindow);
    }

    @Test
    void find_returnsEmptyWhenUnset() {
        assertThat(store.find("user-" + UUID.randomUUID())).isEmpty();
    }
}
