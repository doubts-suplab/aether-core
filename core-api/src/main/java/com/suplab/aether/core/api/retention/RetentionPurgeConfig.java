package com.suplab.aether.core.api.retention;

import com.suplab.aether.core.ports.RetentionPurgePort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the scheduled retention purge.
 *
 * <p>Enabled by default; set {@code aether.core.retention.purge-enabled=false}
 * (env {@code RETENTION_PURGE_ENABLED}) to disable — e.g. in test environments.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "aether.core.retention.purge-enabled", havingValue = "true", matchIfMissing = true)
public class RetentionPurgeConfig {

    @Bean
    public RetentionPurgeScheduler retentionPurgeScheduler(RetentionPurgePort retentionPurge,
                                                           MeterRegistry meterRegistry) {
        return new RetentionPurgeScheduler(retentionPurge, meterRegistry);
    }
}
