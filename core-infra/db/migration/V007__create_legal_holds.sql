-- V007 — Legal / statutory retention holds + held_categories on the erasure audit log
-- Multi-jurisdiction right-to-erasure: a hold marks a category of a user's data that must be RETAINED
-- despite an erasure request (GDPR Art. 17(3), CCPA §1798.105(d), HIPAA/GLBA retention). Erasure of
-- every unheld category still proceeds; the categories held back are recorded on the erasure event.
-- Lock risk: LOW (new table + additive column with a default)
-- Rollback: DROP TABLE legal_holds; ALTER TABLE erasure_events DROP COLUMN held_categories;

CREATE TABLE IF NOT EXISTS legal_holds (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    TEXT         NOT NULL,
    category   TEXT         NOT NULL
                            CHECK (category IN ('MEMORIES', 'SESSIONS', 'PREFERENCES')),
    reason     TEXT         NOT NULL,
    placed_by  TEXT         NOT NULL,
    placed_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_legal_holds_user_category UNIQUE (user_id, category)
);

-- All holds for a user, most recent first / membership checks by user.
CREATE INDEX IF NOT EXISTS idx_legal_holds_user
    ON legal_holds (user_id, placed_at DESC);

-- Which categories a legal hold retained during an erasure (sorted CSV of DataCategory names; '' = none).
ALTER TABLE erasure_events
    ADD COLUMN IF NOT EXISTS held_categories TEXT NOT NULL DEFAULT '';
