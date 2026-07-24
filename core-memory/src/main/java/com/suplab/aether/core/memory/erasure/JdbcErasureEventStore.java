package com.suplab.aether.core.memory.erasure;

import com.suplab.aether.core.domain.ErasureEvent;
import com.suplab.aether.core.domain.ErasureScope;
import com.suplab.aether.core.ports.ErasureEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * JDBC implementation of {@link ErasureEventStore} backed by the append-only {@code erasure_events}
 * table.
 *
 * <p>Write-once: only {@code INSERT} and scoped {@code SELECT} — no update or delete path, so the
 * audit trail outlives the data it describes. Explicit column lists and named parameters throughout.</p>
 */
public class JdbcErasureEventStore implements ErasureEventStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcErasureEventStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcErasureEventStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(ErasureEvent event) {
        var sql = """
                INSERT INTO erasure_events
                    (id, user_id, scope, memories_erased, sessions_erased, preferences_erased,
                     requested_by, erased_at)
                VALUES
                    (:id, :userId, :scope, :memoriesErased, :sessionsErased, :preferencesErased,
                     :requestedBy, :erasedAt)
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", event.id())
                .addValue("userId", event.userId())
                .addValue("scope", event.scope().name())
                .addValue("memoriesErased", event.memoriesErased())
                .addValue("sessionsErased", event.sessionsErased())
                .addValue("preferencesErased", event.preferencesErased())
                .addValue("requestedBy", event.requestedBy())
                .addValue("erasedAt", Timestamp.from(event.erasedAt()));
        jdbc.update(sql, params);
        log.info("Recorded erasure event id={} userId={} scope={} total={}",
                event.id(), event.userId(), event.scope(), event.totalErased());
    }

    @Override
    public List<ErasureEvent> findByUser(String userId, int limit) {
        var sql = """
                SELECT id, user_id, scope, memories_erased, sessions_erased, preferences_erased,
                       requested_by, erased_at
                FROM erasure_events
                WHERE user_id = :userId
                ORDER BY erased_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    private ErasureEvent mapRow(ResultSet rs, int row) throws SQLException {
        return new ErasureEvent(
                UUID.fromString(rs.getString("id")),
                rs.getString("user_id"),
                ErasureScope.valueOf(rs.getString("scope")),
                rs.getInt("memories_erased"),
                rs.getInt("sessions_erased"),
                rs.getInt("preferences_erased"),
                rs.getString("requested_by"),
                rs.getTimestamp("erased_at").toInstant()
        );
    }
}
