-- V008 — Per-user privacy settings (retention window) + allow RETENTION erasure scope
-- Storage limitation (GDPR Art. 5(1)(e)): a scheduled retention purge deletes a user's memories and
-- cognitive sessions older than data_retention_days, honouring legal holds and recording a
-- RETENTION-scope erasure event. A window of 0 means "keep indefinitely" (no automatic purge).
-- Lock risk: LOW (new table + a CHECK-constraint swap on a small audit table)
-- Rollback: DROP TABLE user_privacy_settings;
--           ALTER TABLE erasure_events DROP CONSTRAINT erasure_events_scope_check,
--             ADD CONSTRAINT erasure_events_scope_check CHECK (scope IN ('MEMORIES','ACCOUNT'));

CREATE TABLE IF NOT EXISTS user_privacy_settings (
    user_id             TEXT         PRIMARY KEY,
    data_retention_days INTEGER      NOT NULL DEFAULT 0
                                     CHECK (data_retention_days >= 0),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- The retention sweep's working set: users who declared an automatic retention window.
CREATE INDEX IF NOT EXISTS idx_user_privacy_settings_retention
    ON user_privacy_settings (data_retention_days)
    WHERE data_retention_days > 0;

-- Allow the RETENTION scope on the erasure audit log (retention purges are recorded there too).
ALTER TABLE erasure_events DROP CONSTRAINT IF EXISTS erasure_events_scope_check;
ALTER TABLE erasure_events
    ADD CONSTRAINT erasure_events_scope_check CHECK (scope IN ('MEMORIES', 'ACCOUNT', 'RETENTION'));
