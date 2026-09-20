package com.liorshaya.policypilot.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * A row of {@code model_call} (Document 2, Data Model; Document 4, Logging for every call): what was asked, of
 * which model, how it went and what it cost. Written once per attempt, never changed, and read by the cost view
 * and the evaluation runner.
 */
@Entity
@Immutable
@Table(name = "model_call")
public class ModelCallEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private Instant at;

    @Column(name = "prompt_name", nullable = false)
    private String promptName;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "validation_result", nullable = false)
    private String validationResult;

    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @Column(name = "trace_id")
    private String traceId;

    protected ModelCallEntity() {}

    public ModelCallEntity(
            UUID id,
            Instant at,
            String promptName,
            String promptVersion,
            String model,
            String provider,
            int attempt,
            int inputTokens,
            int outputTokens,
            long latencyMs,
            String validationResult,
            boolean cacheHit,
            String traceId) {
        this.id = id;
        this.at = at;
        this.promptName = promptName;
        this.promptVersion = promptVersion;
        this.model = model;
        this.provider = provider;
        this.attempt = attempt;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.validationResult = validationResult;
        this.cacheHit = cacheHit;
        this.traceId = traceId;
    }

    public UUID id() {
        return id;
    }

    public Instant at() {
        return at;
    }

    public String promptName() {
        return promptName;
    }

    public String promptVersion() {
        return promptVersion;
    }

    public String model() {
        return model;
    }

    public String provider() {
        return provider;
    }

    public int attempt() {
        return attempt;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }

    public long latencyMs() {
        return latencyMs;
    }

    public String validationResult() {
        return validationResult;
    }

    public boolean cacheHit() {
        return cacheHit;
    }

    public String traceId() {
        return traceId;
    }
}
