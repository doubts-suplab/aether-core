package com.suplab.aether.core.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.core.memory.context.DefaultPersonalContextProvider;
import com.suplab.aether.core.memory.embedding.PersonalEmbeddingService;
import com.suplab.aether.core.memory.erasure.DefaultPersonalDataErasureService;
import com.suplab.aether.core.memory.erasure.JdbcErasureEventStore;
import com.suplab.aether.core.memory.erasure.JdbcLegalHoldStore;
import com.suplab.aether.core.memory.export.DefaultPersonalDataExportService;
import com.suplab.aether.core.memory.preference.JdbcUserPreferenceStore;
import com.suplab.aether.core.memory.session.JdbcCognitiveSessionStore;
import com.suplab.aether.core.memory.store.PGVectorPersonalMemoryStore;
import com.suplab.aether.core.ports.CognitiveSessionStore;
import com.suplab.aether.core.ports.ErasureEventStore;
import com.suplab.aether.core.ports.LegalHoldStore;
import com.suplab.aether.core.ports.PersonalContextProvider;
import com.suplab.aether.core.ports.PersonalDataErasurePort;
import com.suplab.aether.core.ports.PersonalDataExportPort;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import com.suplab.aether.core.ports.UserPreferenceStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Spring configuration for Aether Core API beans.
 *
 * <p>Wires the pgvector memory store, Ollama embedding service, and the personal context
 * provider using constructor injection. All beans are declared here — never via field
 * {@code @Autowired}.</p>
 */
@Configuration
public class CoreApiConfig {

    /**
     * Creates the personal memory store backed by pgvector.
     */
    @Bean
    public PersonalMemoryStore personalMemoryStore(NamedParameterJdbcTemplate jdbc) {
        return new PGVectorPersonalMemoryStore(jdbc);
    }

    /**
     * Creates the cognitive session store backed by the {@code cognitive_sessions} table.
     */
    @Bean
    public CognitiveSessionStore cognitiveSessionStore(NamedParameterJdbcTemplate jdbc,
                                                       ObjectMapper objectMapper) {
        return new JdbcCognitiveSessionStore(jdbc, objectMapper);
    }

    /**
     * Creates the user preference store backed by the {@code user_preferences} table.
     */
    @Bean
    public UserPreferenceStore userPreferenceStore(NamedParameterJdbcTemplate jdbc,
                                                   ObjectMapper objectMapper) {
        return new JdbcUserPreferenceStore(jdbc, objectMapper);
    }

    /**
     * Creates the append-only erasure audit-log store ({@code erasure_events} table).
     */
    @Bean
    public ErasureEventStore erasureEventStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcErasureEventStore(jdbc);
    }

    /**
     * Creates the legal / statutory retention-hold store ({@code legal_holds} table).
     */
    @Bean
    public LegalHoldStore legalHoldStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcLegalHoldStore(jdbc);
    }

    /**
     * Creates the GDPR right-to-erasure service, composing the memory, session, and preference
     * stores, the erasure audit log, and the legal-hold store (so retention holds gate deletion).
     */
    @Bean
    public PersonalDataErasurePort personalDataErasurePort(PersonalMemoryStore memoryStore,
                                                           CognitiveSessionStore sessionStore,
                                                           UserPreferenceStore preferenceStore,
                                                           ErasureEventStore erasureEventStore,
                                                           LegalHoldStore legalHoldStore) {
        return new DefaultPersonalDataErasureService(memoryStore, sessionStore, preferenceStore,
                erasureEventStore, legalHoldStore);
    }

    /**
     * Creates the GDPR data-portability service (Article 20), composing the memory, session, and
     * preference stores into a read-only export.
     */
    @Bean
    public PersonalDataExportPort personalDataExportPort(PersonalMemoryStore memoryStore,
                                                         CognitiveSessionStore sessionStore,
                                                         UserPreferenceStore preferenceStore) {
        return new DefaultPersonalDataExportService(memoryStore, sessionStore, preferenceStore);
    }

    /**
     * Creates the context provider that assembles personal context snapshots for Grid
     * from memories, the active cognitive session, and stored preferences.
     *
     * @param memoryStore        the store to retrieve memories from
     * @param sessionStore       the store to retrieve the active session from
     * @param preferenceStore    the store to retrieve preferences from
     * @param defaultMemoryLimit max memories per type fetched per context request
     */
    @Bean
    public PersonalContextProvider personalContextProvider(
            PersonalMemoryStore memoryStore,
            CognitiveSessionStore sessionStore,
            UserPreferenceStore preferenceStore,
            @Value("${aether.core.context.memory-limit:5}") int defaultMemoryLimit) {
        return new DefaultPersonalContextProvider(memoryStore, sessionStore, preferenceStore, defaultMemoryLimit);
    }

    /**
     * Creates the embedding service that calls Ollama's {@code /api/embeddings} endpoint.
     *
     * <p>Conditional on {@code aether.core.embedding.enabled=true} (default). Set to
     * {@code false} in environments where Ollama is unavailable — memories will be saved
     * with zero vectors and semantic similarity search will be non-functional, but all
     * other endpoints remain operational.</p>
     */
    @Bean
    @ConditionalOnProperty(name = "aether.core.embedding.enabled", havingValue = "true", matchIfMissing = true)
    public PersonalEmbeddingService personalEmbeddingService(
            @Value("${aether.core.ollama.base-url:http://localhost:11434}") String ollamaUrl,
            @Value("${aether.core.embedding.model:all-minilm}") String model) {
        return new PersonalEmbeddingService(ollamaUrl, model);
    }
}
