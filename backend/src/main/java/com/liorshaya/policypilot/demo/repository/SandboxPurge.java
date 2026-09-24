package com.liorshaya.policypilot.demo.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Deletes the sandboxes idle for 24 hours through the database procedure {@code purge_stale_sandboxes} (migration V11;
 * Document 2, Security and Demo Protections, Sandbox isolation). The API role has no delete grant on rule sets,
 * versions, rules, chat or the audit log, so the procedure, run with its owner's rights, is the only way they go; it
 * takes no sandbox id and measures the 24 hours against the earlier of {@code now} and the database's clock. It is
 * called by name with a bound parameter, as JPA calls a stored procedure: no SQL is written outside {@code rag}
 * (Document 5, Verification, Architecture).
 */
@Repository
public class SandboxPurge {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final EntityManager entities;

    public SandboxPurge(EntityManager entities) {
        this.entities = entities;
    }

    /** What one purge deleted: whole sandboxes, and the rows of theirs the RESET entry counts. */
    public record Purged(int sandboxes, int policies, int versions, int decisions, int chatSessions,
            int changeRequests, int auditEntries) {}

    /** Deletes every sandbox whose newest row is 24 hours older than {@code now}, in the caller's transaction. */
    public Purged purge(Instant now) {
        StoredProcedureQuery call = entities.createStoredProcedureQuery("purge_stale_sandboxes")
                .registerStoredProcedureParameter(1, OffsetDateTime.class, ParameterMode.IN)
                .registerStoredProcedureParameter(2, String.class, ParameterMode.OUT)
                .setParameter(1, now.atOffset(ZoneOffset.UTC));
        call.execute();
        JsonNode counts = JSON.readTree((String) call.getOutputParameterValue(2));
        return new Purged(counts.required("sandboxes").asInt(), counts.required("policies").asInt(),
                counts.required("versions").asInt(), counts.required("decisions").asInt(),
                counts.required("chatSessions").asInt(), counts.required("changeRequests").asInt(),
                counts.required("auditEntries").asInt());
    }
}
