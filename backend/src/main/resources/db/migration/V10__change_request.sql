-- V10: change requests (Document 2, Data Model: change_request; Work Plan day 12). A proposal is stored only once it
-- validates (Document 3, Patch validation), with the stored request's id in every pending provenance of its patches;
-- a proposal that fails or is refused is never a row. Its status leaves PROPOSED for APPROVED or REJECTED on day 13,
-- and the full proposal is kept even when rejected, so the API's role may insert and update a row but never delete
-- one. The audit log's change_request_id gets the foreign key V4 left for this table.
--
-- Additive: the v1.0.0 image never reads this table and writes no change_request_id, so it still runs here.
CREATE TABLE change_request (
    id                uuid        PRIMARY KEY,
    sandbox_id        uuid        NOT NULL,
    base_version_id   uuid        NOT NULL REFERENCES ruleset_version (id),
    request_text      text        NOT NULL,
    status            text        NOT NULL CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED')),
    patches_json      jsonb       NOT NULL,
    rationale_json    jsonb       NOT NULL,
    regression_json   jsonb,
    result_version_id uuid        REFERENCES ruleset_version (id),
    created_at        timestamptz NOT NULL,
    decided_at        timestamptz,
    actor             text        NOT NULL,
    CONSTRAINT change_request_decided_is_stamped CHECK ((status = 'PROPOSED') = (decided_at IS NULL))
);
CREATE INDEX change_request_sandbox_idx ON change_request (sandbox_id, created_at);

ALTER TABLE audit_entry
    ADD CONSTRAINT audit_entry_change_request_fk FOREIGN KEY (change_request_id) REFERENCES change_request (id);

GRANT SELECT, INSERT, UPDATE ON change_request TO policypilot_app;
