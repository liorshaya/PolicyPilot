package com.liorshaya.policypilot.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A row of {@code model_response_cache} (Document 2, Security and Demo Protections): one model answer, keyed by
 * the hash of the prompt version, the model and the rendered input, so the scripted demo steps cost nothing the
 * second time and a new prompt version misses on purpose.
 */
@Entity
@Immutable
@Table(name = "model_response_cache")
public class CachedResponseEntity {

    @Id
    @Column(name = "key", nullable = false)
    private String key;

    @Column(name = "prompt_name", nullable = false)
    private String promptName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", nullable = false)
    private String response;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CachedResponseEntity() {}

    public CachedResponseEntity(String key, String promptName, String response, Instant createdAt) {
        this.key = key;
        this.promptName = promptName;
        this.response = response;
        this.createdAt = createdAt;
    }

    public String key() {
        return key;
    }

    public String promptName() {
        return promptName;
    }

    public String response() {
        return response;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
