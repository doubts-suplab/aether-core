package com.suplab.aether.core.ports;

import com.suplab.aether.core.domain.PersonalDataExport;

/**
 * Port for GDPR data portability (Article 20) / CCPA right-to-know over a user's personal data.
 *
 * <p>Assembles a read-only {@link PersonalDataExport} of everything Core holds for a user — memories
 * (active + archived), cognitive sessions across every tenant, and preferences. Like erasure, export
 * is <em>Core-local</em> and user-scoped; unlike cognitive recall, it must not reinforce or mutate the
 * data it reads. Implementations live in {@code core-memory}.</p>
 */
public interface PersonalDataExportPort {

    /**
     * Assembles a portable export of all data Core holds for a user.
     *
     * @param userId the data subject
     * @return the assembled export (never {@code null}; empty collections when the user has no data)
     */
    PersonalDataExport exportAll(String userId);
}
