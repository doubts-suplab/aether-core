package com.suplab.aether.core.memory.erasure;

import com.suplab.aether.core.domain.ErasureEvent;
import com.suplab.aether.core.domain.ErasureScope;
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
class JdbcErasureEventStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_core_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcErasureEventStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcErasureEventStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void record_andFindByUser_roundTrip() {
        var userId = "user-" + UUID.randomUUID();
        store.record(ErasureEvent.of(userId, ErasureScope.MEMORIES, 5, 0, 0, "self"));
        store.record(ErasureEvent.of(userId, ErasureScope.ACCOUNT, 9, 3, 1, "ops@acme"));

        var history = store.findByUser(userId, 10);

        assertThat(history).hasSize(2);
        // most recent first — the ACCOUNT erasure was recorded last
        assertThat(history.getFirst().scope()).isEqualTo(ErasureScope.ACCOUNT);
        assertThat(history.getFirst().totalErased()).isEqualTo(13);
        assertThat(history.getFirst().requestedBy()).isEqualTo("ops@acme");
    }

    @Test
    void findByUser_isScopedToTheUser() {
        var userA = "user-" + UUID.randomUUID();
        store.record(ErasureEvent.of(userA, ErasureScope.ACCOUNT, 1, 1, 1, "self"));

        assertThat(store.findByUser("user-" + UUID.randomUUID(), 10)).isEmpty();
    }
}
