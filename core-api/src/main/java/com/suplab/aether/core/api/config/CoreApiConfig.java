package com.suplab.aether.core.api.config;

import com.suplab.aether.core.memory.embedding.PersonalEmbeddingService;
import com.suplab.aether.core.memory.store.PGVectorPersonalMemoryStore;
import com.suplab.aether.core.ports.PersonalMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Spring configuration for Aether Core API beans.
 *
 * <p>Wires the pgvector memory store and Ollama embedding service using constructor
 * injection. All infrastructure beans are created here — never via field {@code @Autowired}.</p>
 */
@Configuration
public class CoreApiConfig {

    /**
     * Creates the personal memory store backed by pgvector.
     *
     * @param jdbc the named-parameter JDBC template (auto-configured from datasource)
     * @return a {@link PersonalMemoryStore} using the {@code personal_memories} table
     */
    @Bean
    public PersonalMemoryStore personalMemoryStore(NamedParameterJdbcTemplate jdbc) {
        return new PGVectorPersonalMemoryStore(jdbc);
    }

    /**
     * Creates the embedding service that calls Ollama's {@code /api/embeddings} endpoint.
     *
     * @param ollamaUrl the Ollama base URL (default: {@code http://localhost:11434})
     * @param model     the embedding model name (default: {@code all-minilm})
     * @return a {@link PersonalEmbeddingService} producing 384-dim vectors
     */
    @Bean
    public PersonalEmbeddingService personalEmbeddingService(
            @Value("${aether.core.ollama.base-url:http://localhost:11434}") String ollamaUrl,
            @Value("${aether.core.embedding.model:all-minilm}") String model) {
        return new PersonalEmbeddingService(ollamaUrl, model);
    }
}
