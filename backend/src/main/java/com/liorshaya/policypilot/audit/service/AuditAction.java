package com.liorshaya.policypilot.audit.service;

/** What an audit entry records (Document 2, Data Model: {@code audit_entry.action}). */
public enum AuditAction {
    PUBLISH,
    CHANGE_PROPOSED,
    CHANGE_APPROVED,
    CHANGE_REJECTED,
    GAP_ACKNOWLEDGED,
    RESET
}
