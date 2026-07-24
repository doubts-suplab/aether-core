-- V006 — Create erasure_events audit table (GDPR right-to-erasure, Article 17 + accountability 5(2))
-- Append-only proof that a user's personal data was erased. Holds only the subject's own userId and
-- operation metadata (scope, per-store counts, requester, timestamp) — never any memory content — so
-- it may be retained to demonstrate compliance after the data itself is gone.
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE erasure_events;

CREATE TABLE IF NOT EXISTS erasure_events (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            TEXT         NOT NULL,
    scope              TEXT         NOT NULL
                                    CHECK (scope IN ('MEMORIES', 'ACCOUNT')),
    memories_erased    INTEGER      NOT NULL DEFAULT 0 CHECK (memories_erased >= 0),
    sessions_erased    INTEGER      NOT NULL DEFAULT 0 CHECK (sessions_erased >= 0),
    preferences_erased INTEGER      NOT NULL DEFAULT 0 CHECK (preferences_erased >= 0),
    requested_by       TEXT         NOT NULL DEFAULT 'data-subject',
    erased_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- A user's erasure history, most recent first.
CREATE INDEX IF NOT EXISTS idx_erasure_events_user
    ON erasure_events (user_id, erased_at DESC);
