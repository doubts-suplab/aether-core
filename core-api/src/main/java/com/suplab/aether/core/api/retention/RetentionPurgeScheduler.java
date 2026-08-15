package com.suplab.aether.core.api.retention;

import com.suplab.aether.core.ports.RetentionPurgePort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Scheduled retention purge — enforces each user's storage-limitation window (GDPR Art. 5(1)(e)).
 *
 * <p>Default schedule is 02:30 daily ({@code aether.core.retention.purge-cron}). Delegates to
 * {@link RetentionPurgePort#purgeAll()} (which honours legal holds and audits each purge). Exposed
 * metrics:</p>
 * <ul>
 *   <li>{@code aether.core.retention.memories-purged} — counter, aged-out memories deleted across runs</li>
 *   <li>{@code aether.core.retention.sessions-purged} — counter, aged-out sessions deleted across runs</li>
 * </ul>
 */
public class RetentionPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeScheduler.class);

    private final RetentionPurgePort retentionPurge;
    private final Counter memoriesPurged;
    private final Counter sessionsPurged;

    public RetentionPurgeScheduler(RetentionPurgePort retentionPurge, MeterRegistry meterRegistry) {
        this.retentionPurge = retentionPurge;
        this.memoriesPurged = Counter.builder("aether.core.retention.memories-purged")
                .description("Memories deleted by the retention purge (aged past the retention window)")
                .register(meterRegistry);
        this.sessionsPurged = Counter.builder("aether.core.retention.sessions-purged")
                .description("Cognitive sessions deleted by the retention purge")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${aether.core.retention.purge-cron:0 30 2 * * *}")
    public void runRetentionPurge() {
        log.debug("Starting scheduled retention purge");
        var result = retentionPurge.purgeAll();
        memoriesPurged.increment(result.memoriesPurged());
        sessionsPurged.increment(result.sessionsPurged());
        log.info("Scheduled retention purge: users={} memories={} sessions={}",
                result.usersPurged(), result.memoriesPurged(), result.sessionsPurged());
    }
}
