-- V4: rule sets and their versions, the audit log, and the role the API runs as (Document 2, Data Model; Work Plan
-- day 5). Migrations run as the database owner; every API connection switches to policypilot_app (SET ROLE in the
-- connection pool), which holds only the grants below (Document 5, principle 1: the API role cannot update or delete
-- audit entries or published versions).

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'policypilot_app') THEN
        CREATE ROLE policypilot_app NOLOGIN;
    END IF;
END
$$;
-- The login role may switch to it (a superuser may anyway; a plain owner needs the membership).
GRANT policypilot_app TO CURRENT_USER;

GRANT USAGE ON SCHEMA public TO policypilot_app;
GRANT SELECT ON flyway_schema_history TO policypilot_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON policy_document, policy_version, policy_paragraph TO policypilot_app;

-- The logical rule set. A protected row is the seeded demo rule set: it belongs to no sandbox and no session may
-- modify it; a sandbox's copy names its origin, one copy per sandbox (Document 5, Authorization (sandbox)).
-- domain is the DSL document's id, shared by every version (Document 3, Document Structure).
CREATE TABLE ruleset (
    id              uuid        PRIMARY KEY,
    sandbox_id      uuid,
    protected       boolean     NOT NULL DEFAULT false,
    forked_from_id  uuid        REFERENCES ruleset (id),
    name            text        NOT NULL,
    domain          text        NOT NULL,
    default_outcome text        NOT NULL CHECK (default_outcome IN ('approve', 'reject', 'refer')),
    created_at      timestamptz NOT NULL,
    CONSTRAINT ruleset_protected_has_no_sandbox CHECK (protected = (sandbox_id IS NULL)),
    CONSTRAINT ruleset_protected_is_no_fork CHECK (NOT (protected AND forked_from_id IS NOT NULL)),
    CONSTRAINT ruleset_one_fork_per_sandbox UNIQUE (sandbox_id, forked_from_id)
);
CREATE INDEX ruleset_sandbox_idx ON ruleset (sandbox_id);

-- One version of a rule set: rules_json is the whole DSL document, field_schema_json its fields. A version is
-- published at most once and never changes after (the trigger below); embedding_status arrives with rag on day 8.
CREATE TABLE ruleset_version (
    id                uuid        PRIMARY KEY,
    ruleset_id        uuid        NOT NULL REFERENCES ruleset (id),
    version_no        integer     NOT NULL CHECK (version_no >= 1),
    status            text        NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
    policy_version_id uuid        NOT NULL REFERENCES policy_version (id),
    rules_json        jsonb       NOT NULL,
    field_schema_json jsonb       NOT NULL,
    retired_ids       jsonb       NOT NULL DEFAULT '[]',
    published_at      timestamptz,
    published_by      text,
    parent_version_id uuid        REFERENCES ruleset_version (id),
    CONSTRAINT ruleset_version_number_unique UNIQUE (ruleset_id, version_no),
    CONSTRAINT ruleset_version_published_is_stamped
        CHECK ((status = 'DRAFT') = (published_at IS NULL AND published_by IS NULL))
);

-- Published versions are immutable at the database (Document 2: "a DB trigger forbids updates once status =
-- PUBLISHED"; Brief FR-7). A superseded version was published, so it is frozen too. The trigger holds for every role.
CREATE FUNCTION ruleset_version_is_immutable_once_published() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'DRAFT' THEN
        RAISE EXCEPTION 'ruleset_version % is % and cannot change', OLD.id, OLD.status
            USING ERRCODE = 'object_not_in_prerequisite_state';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER ruleset_version_immutable BEFORE UPDATE ON ruleset_version
    FOR EACH ROW EXECUTE FUNCTION ruleset_version_is_immutable_once_published();

-- One row per rule of a published version, denormalized from rules_json for querying, provenance joins and
-- embedding (Document 2, Data Model).
CREATE TABLE rule (
    id                 uuid    PRIMARY KEY,
    ruleset_version_id uuid    NOT NULL REFERENCES ruleset_version (id),
    rule_id            text    NOT NULL,
    priority           integer NOT NULL,
    label              text    NOT NULL,
    provenance_kind    text    NOT NULL CHECK (provenance_kind IN ('quoted', 'analyst', 'pending')),
    paragraph_id       uuid    REFERENCES policy_paragraph (id),
    source_quote       text,
    rule_json          jsonb   NOT NULL,
    CONSTRAINT rule_id_unique_in_version UNIQUE (ruleset_version_id, rule_id)
);

-- The append-only audit log (Document 2: "no update or delete grants on this table"; NFR-3). change_request_id gets
-- its foreign key with the change_request table.
CREATE TABLE audit_entry (
    id                 uuid        PRIMARY KEY,
    at                 timestamptz NOT NULL,
    actor              text        NOT NULL,
    action             text        NOT NULL CHECK (action IN ('PUBLISH', 'CHANGE_PROPOSED', 'CHANGE_APPROVED',
                                                              'CHANGE_REJECTED', 'GAP_ACKNOWLEDGED', 'RESET')),
    ruleset_version_id uuid        REFERENCES ruleset_version (id),
    change_request_id  uuid,
    details_json       jsonb       NOT NULL
);
CREATE INDEX audit_entry_version_idx ON audit_entry (ruleset_version_id);

GRANT SELECT, INSERT, UPDATE ON ruleset, ruleset_version TO policypilot_app;
GRANT SELECT, INSERT ON rule, audit_entry TO policypilot_app;
