package com.suplab.aether.core.memory.erasure;

import com.suplab.aether.core.domain.DataCategory;
import com.suplab.aether.core.domain.LegalHold;
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
class JdbcLegalHoldStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("aether_core_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcLegalHoldStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcLegalHoldStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void place_thenHeldCategories_andFindByUser() {
        var userId = "user-" + UUID.randomUUID();
        store.place(LegalHold.of(userId, DataCategory.SESSIONS, "active litigation", "legal@acme"));
        store.place(LegalHold.of(userId, DataCategory.MEMORIES, "regulatory retention", "legal@acme"));

        assertThat(store.heldCategories(userId))
                .containsExactlyInAnyOrder(DataCategory.SESSIONS, DataCategory.MEMORIES);
        assertThat(store.findByUser(userId)).hasSize(2);
    }

    @Test
    void place_isIdempotentPerCategory() {
        var userId = "user-" + UUID.randomUUID();
        store.place(LegalHold.of(userId, DataCategory.MEMORIES, "first reason", "legal@acme"));
        store.place(LegalHold.of(userId, DataCategory.MEMORIES, "updated reason", "compliance@acme"));

        var holds = store.findByUser(userId);
        assertThat(holds).hasSize(1);
        assertThat(holds.getFirst().reason()).isEqualTo("updated reason");
        assertThat(holds.getFirst().placedBy()).isEqualTo("compliance@acme");
    }

    @Test
    void lift_removesHold() {
        var userId = "user-" + UUID.randomUUID();
        store.place(LegalHold.of(userId, DataCategory.PREFERENCES, "reason", "legal@acme"));

        assertThat(store.lift(userId, DataCategory.PREFERENCES)).isEqualTo(1);
        assertThat(store.heldCategories(userId)).isEmpty();
        assertThat(store.lift(userId, DataCategory.PREFERENCES)).isZero(); // already gone
    }

    @Test
    void heldCategories_isScopedToTheUser() {
        var userA = "user-" + UUID.randomUUID();
        store.place(LegalHold.of(userA, DataCategory.SESSIONS, "reason", "legal@acme"));

        assertThat(store.heldCategories("user-" + UUID.randomUUID())).isEmpty();
    }
}
