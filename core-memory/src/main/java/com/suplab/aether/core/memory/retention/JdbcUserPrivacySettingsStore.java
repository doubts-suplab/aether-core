package com.suplab.aether.core.memory.retention;

import com.suplab.aether.core.domain.UserPrivacySettings;
import com.suplab.aether.core.ports.UserPrivacySettingsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link UserPrivacySettingsStore} backed by the {@code user_privacy_settings}
 * table. Explicit column lists and named parameters throughout; {@code save} is an upsert by
 * {@code user_id}.
 */
public class JdbcUserPrivacySettingsStore implements UserPrivacySettingsStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcUserPrivacySettingsStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcUserPrivacySettingsStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserPrivacySettings> find(String userId) {
        var sql = """
                SELECT user_id, data_retention_days, updated_at
                FROM user_privacy_settings
                WHERE user_id = :userId
                """;
        return jdbc.query(sql, new MapSqlParameterSource("userId", userId), this::mapRow)
                .stream().findFirst();
    }

    @Override
    public void save(UserPrivacySettings settings) {
        var sql = """
                INSERT INTO user_privacy_settings (user_id, data_retention_days, updated_at)
                VALUES (:userId, :days, :updatedAt)
                ON CONFLICT (user_id) DO UPDATE SET
                    data_retention_days = EXCLUDED.data_retention_days,
                    updated_at = EXCLUDED.updated_at
                """;
        var params = new MapSqlParameterSource()
                .addValue("userId", settings.userId())
                .addValue("days", settings.dataRetentionDays())
                .addValue("updatedAt", Timestamp.from(settings.updatedAt()));
        jdbc.update(sql, params);
        log.info("Saved privacy settings for userId={} dataRetentionDays={}",
                settings.userId(), settings.dataRetentionDays());
    }

    @Override
    public List<UserPrivacySettings> findAllWithRetention() {
        var sql = """
                SELECT user_id, data_retention_days, updated_at
                FROM user_privacy_settings
                WHERE data_retention_days > 0
                ORDER BY user_id
                """;
        return jdbc.query(sql, this::mapRow);
    }

    private UserPrivacySettings mapRow(ResultSet rs, int row) throws SQLException {
        return new UserPrivacySettings(
                rs.getString("user_id"),
                rs.getInt("data_retention_days"),
                rs.getTimestamp("updated_at").toInstant());
    }
}
