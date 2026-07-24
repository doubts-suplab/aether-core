package com.suplab.aether.core.memory.erasure;

import com.suplab.aether.core.domain.DataCategory;
import com.suplab.aether.core.domain.LegalHold;
import com.suplab.aether.core.ports.LegalHoldStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * JDBC implementation of {@link LegalHoldStore} backed by the {@code legal_holds} table.
 *
 * <p>One row per {@code (user_id, category)}; {@link #place} upserts on that key so a hold is
 * idempotent. Explicit column lists and named parameters throughout, every query scoped by
 * {@code user_id}.</p>
 */
public class JdbcLegalHoldStore implements LegalHoldStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcLegalHoldStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcLegalHoldStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void place(LegalHold hold) {
        var sql = """
                INSERT INTO legal_holds (user_id, category, reason, placed_by, placed_at)
                VALUES (:userId, :category, :reason, :placedBy, :placedAt)
                ON CONFLICT (user_id, category)
                DO UPDATE SET reason = EXCLUDED.reason,
                              placed_by = EXCLUDED.placed_by,
                              placed_at = EXCLUDED.placed_at
                """;
        var params = new MapSqlParameterSource()
                .addValue("userId", hold.userId())
                .addValue("category", hold.category().name())
                .addValue("reason", hold.reason())
                .addValue("placedBy", hold.placedBy())
                .addValue("placedAt", Timestamp.from(hold.placedAt()));
        jdbc.update(sql, params);
        log.info("Placed legal hold userId={} category={} placedBy={}",
                hold.userId(), hold.category(), hold.placedBy());
    }

    @Override
    public int lift(String userId, DataCategory category) {
        var sql = "DELETE FROM legal_holds WHERE user_id = :userId AND category = :category";
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("category", category.name());
        int removed = jdbc.update(sql, params);
        log.info("Lifted legal hold userId={} category={} removed={}", userId, category, removed);
        return removed;
    }

    @Override
    public Set<DataCategory> heldCategories(String userId) {
        var sql = "SELECT category FROM legal_holds WHERE user_id = :userId";
        var params = new MapSqlParameterSource().addValue("userId", userId);
        var categories = jdbc.query(sql, params,
                (rs, row) -> DataCategory.valueOf(rs.getString("category")));
        return categories.isEmpty() ? Set.of() : EnumSet.copyOf(categories);
    }

    @Override
    public List<LegalHold> findByUser(String userId) {
        var sql = """
                SELECT user_id, category, reason, placed_by, placed_at
                FROM legal_holds
                WHERE user_id = :userId
                ORDER BY placed_at DESC
                """;
        var params = new MapSqlParameterSource().addValue("userId", userId);
        return jdbc.query(sql, params, this::mapRow);
    }

    private LegalHold mapRow(ResultSet rs, int row) throws SQLException {
        return new LegalHold(
                rs.getString("user_id"),
                DataCategory.valueOf(rs.getString("category")),
                rs.getString("reason"),
                rs.getString("placed_by"),
                rs.getTimestamp("placed_at").toInstant()
        );
    }
}
