package com.liorshaya.policypilot.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.jspecify.annotations.Nullable;
import org.hibernate.type.SqlTypes;

/**
 * A row of {@code chat_message} (Document 2, Data Model): a question or the answer shown for it, with the answer's
 * citations, tool calls and token usage as JSON. Rows are written once and never changed.
 */
@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private int turn;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citations_json", nullable = false)
    private String citationsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls_json", nullable = false)
    private String toolCallsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_usage_json")
    private String tokenUsageJson;

    @Column(nullable = false)
    private Instant at;

    /** The fixed sentence an answer is, {@code not_covered} or {@code tool_limit}, or null (V13). */
    @Column
    private @Nullable String fixed;

    protected ChatMessageEntity() {}

    public ChatMessageEntity(UUID id, UUID sessionId, int turn, String role, String content, String citationsJson,
            String toolCallsJson, @Nullable String tokenUsageJson, Instant at, @Nullable String fixed) {
        this.id = id;
        this.sessionId = sessionId;
        this.turn = turn;
        this.role = role;
        this.content = content;
        this.citationsJson = citationsJson;
        this.toolCallsJson = toolCallsJson;
        this.tokenUsageJson = tokenUsageJson;
        this.at = at;
        this.fixed = fixed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Instant getAt() {
        return at;
    }

    public @Nullable String getFixed() {
        return fixed;
    }

    public int getTurn() {
        return turn;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getCitationsJson() {
        return citationsJson;
    }

    public String getToolCallsJson() {
        return toolCallsJson;
    }
}
