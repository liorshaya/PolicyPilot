package com.liorshaya.policypilot.audit.service;

import com.liorshaya.policypilot.common.Csv;
import java.util.List;
import java.util.Objects;

/**
 * The audit log as CSV (Document 2, {@code GET /audit/export}; Document 5, CSV and formula injection): one row per
 * entry, the details as their JSON, written by {@link Csv}.
 */
public final class AuditCsv {

    static final List<String> HEADER = List.of("id", "at", "actor", "action", "ruleset_version_id",
            "change_request_id", "details");

    private AuditCsv() {}

    /** The entries as a CSV document, in the order given. */
    public static String of(List<AuditEntry> entries) {
        return Csv.document(HEADER, entries.stream().map(entry -> List.of(
                entry.id().toString(),
                entry.at().toString(),
                entry.actor(),
                entry.action().name(),
                entry.rulesetVersionId().toString(),
                Objects.toString(entry.changeRequestId(), ""),
                entry.details().toString())).toList());
    }
}
