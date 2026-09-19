-- V5: the case fixtures and the decisions made from them (Document 2, Data Model; Work Plan day 5). The 200
-- synthetic applicants are seeded as protected rows of the fixture set cases-200, readable by every sandbox and
-- writable by none; a decision belongs to the sandbox that asked for it and names the version that produced it.

CREATE TABLE case_fixture (
    id               uuid    PRIMARY KEY,
    sandbox_id       uuid,
    protected        boolean NOT NULL DEFAULT false,
    fixture_set      text,
    case_no          integer,
    name             text    NOT NULL,
    fields_json      jsonb   NOT NULL,
    expected_outcome text    CHECK (expected_outcome IN ('approve', 'reject', 'refer')),
    tags             jsonb   NOT NULL DEFAULT '[]',
    CONSTRAINT case_fixture_protected_has_no_sandbox CHECK (protected = (sandbox_id IS NULL)),
    CONSTRAINT case_fixture_number_unique_in_set UNIQUE (fixture_set, case_no)
);
CREATE INDEX case_fixture_sandbox_idx ON case_fixture (sandbox_id);

-- Input is snapshotted so a decision can be replayed even if the fixture changes; trace_json holds the decision
-- object of Document 3, whose trace is the ordered steps. Simulations are never written here (Document 3).
CREATE TABLE decision (
    id                 uuid        PRIMARY KEY,
    sandbox_id         uuid        NOT NULL,
    ruleset_version_id uuid        NOT NULL REFERENCES ruleset_version (id),
    case_id            uuid        REFERENCES case_fixture (id),
    input_json         jsonb       NOT NULL,
    status             text        NOT NULL CHECK (status IN ('OK', 'ERROR')),
    outcome            text        CHECK (outcome IN ('approve', 'reject', 'refer')),
    deciding_rule_id   text,
    error_code         text,
    trace_json         jsonb       NOT NULL,
    decided_at         timestamptz NOT NULL,
    duration_micros    bigint      NOT NULL,
    CONSTRAINT decision_outcome_or_error CHECK ((status = 'OK') = (outcome IS NOT NULL))
);
CREATE INDEX decision_version_idx ON decision (sandbox_id, ruleset_version_id, decided_at);
CREATE INDEX decision_case_idx ON decision (sandbox_id, ruleset_version_id, case_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON case_fixture, decision TO policypilot_app;
