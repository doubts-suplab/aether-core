package com.suplab.aether.core.domain;

/**
 * The outcome of a retention purge — a single user's purge, or the aggregate of a sweep.
 *
 * @param usersPurged     number of users whose data was purged (1 for a single-user purge)
 * @param memoriesPurged  personal-memory rows deleted (active + archived) because they aged out
 * @param sessionsPurged  cognitive-session rows deleted because they aged out
 */
public record RetentionPurgeResult(int usersPurged, int memoriesPurged, int sessionsPurged) {

    public RetentionPurgeResult {
        if (usersPurged < 0 || memoriesPurged < 0 || sessionsPurged < 0)
            throw new IllegalArgumentException("purge counts must be >= 0");
    }

    /** An empty result — nothing purged. */
    public static RetentionPurgeResult empty() {
        return new RetentionPurgeResult(0, 0, 0);
    }

    /**
     * @return the total number of records deleted (memories + sessions).
     */
    public int totalPurged() {
        return memoriesPurged + sessionsPurged;
    }

    /**
     * Adds another result to this one (for aggregating a sweep across users).
     */
    public RetentionPurgeResult plus(RetentionPurgeResult other) {
        return new RetentionPurgeResult(
                usersPurged + other.usersPurged,
                memoriesPurged + other.memoriesPurged,
                sessionsPurged + other.sessionsPurged);
    }
}
