package com.liorshaya.policypilot.audit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.common.Csv;
import com.liorshaya.policypilot.support.Requirement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The audit log as CSV (Document 2, {@code GET /audit/export}: one row per entry; Document 5, CSV and formula
 * injection). The cells are written by {@link Csv}, which CsvTest holds to Document 5 cell by cell.
 */
@Requirement("FR-19")
class AuditCsvTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // Expected: the byte order mark and the header, then one row per entry in the order given, an entry about no
    // change request with an empty cell, and the details as their JSON, quoted since JSON holds quotes and commas
    @Test
    void theHeaderThenOneRowPerEntry() {
        UUID version = new UUID(0, 1);
        UUID request = new UUID(0, 2);
        ObjectNode details = JSON.createObjectNode().put("versionNo", 2).put("note", "אושר");
        AuditEntry approved = new AuditEntry(new UUID(0, 3), Instant.parse("2026-09-24T09:05:00Z"), "sandbox-a",
                AuditAction.CHANGE_APPROVED, version, request, details);
        AuditEntry published = new AuditEntry(new UUID(0, 4), Instant.parse("2026-09-24T09:00:00Z"), "demo-analyst",
                AuditAction.PUBLISH, version, null, JSON.createObjectNode());

        List<String> lines = List.of(AuditCsv.of(List.of(approved, published)).split("\r\n"));

        assertThat(lines).containsExactly(
                Csv.BYTE_ORDER_MARK + "id,at,actor,action,ruleset_version_id,change_request_id,details",
                new UUID(0, 3) + ",2026-09-24T09:05:00Z,sandbox-a,CHANGE_APPROVED," + version + "," + request
                        + ",\"{\"\"versionNo\"\":2,\"\"note\"\":\"\"אושר\"\"}\"",
                new UUID(0, 4) + ",2026-09-24T09:00:00Z,demo-analyst,PUBLISH," + version + ",,{}");
    }

    // Document 5: a cell that reads as a formula is prefixed, whichever column it is in. Expected: an actor so
    @Test
    void aCellThatReadsAsAFormulaIsPrefixed() {
        AuditEntry entry = new AuditEntry(new UUID(0, 5), Instant.parse("2026-09-24T09:00:00Z"), "=cmd|' /C calc'!A0",
                AuditAction.PUBLISH, new UUID(0, 1), null, JSON.createObjectNode());

        assertThat(AuditCsv.of(List.of(entry))).contains(",'=cmd|' /C calc'!A0,");
    }
}
