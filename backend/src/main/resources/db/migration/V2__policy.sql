-- V2: policy documents, their versions and paragraphs (Document 2, Data Model; Work Plan day 4).
-- A protected row is the seeded demo policy: it belongs to no sandbox and no session may modify it (Document 5,
-- Authorization). Every other row carries the sandbox of the session that created it.
CREATE TABLE policy_document (
    id          uuid        PRIMARY KEY,
    sandbox_id  uuid,
    protected   boolean     NOT NULL DEFAULT false,
    title       text        NOT NULL,
    language    text        NOT NULL CHECK (language IN ('he', 'en')),
    created_at  timestamptz NOT NULL,
    CONSTRAINT policy_document_protected_has_no_sandbox CHECK (protected = (sandbox_id IS NULL))
);
CREATE INDEX policy_document_sandbox_idx ON policy_document (sandbox_id);

-- Immutable once a rule set is generated from it (Document 2); raw_text is the normalized text the paragraphs split.
CREATE TABLE policy_version (
    id          uuid        PRIMARY KEY,
    document_id uuid        NOT NULL REFERENCES policy_document (id) ON DELETE CASCADE,
    version_no  integer     NOT NULL CHECK (version_no >= 1),
    raw_text    text        NOT NULL,
    created_at  timestamptz NOT NULL,
    CONSTRAINT policy_version_number_unique UNIQUE (document_id, version_no)
);

-- The provenance unit: index is the paragraph's position in its version, from 1 (Document 3, provenance.paragraph).
CREATE TABLE policy_paragraph (
    id                uuid    PRIMARY KEY,
    policy_version_id uuid    NOT NULL REFERENCES policy_version (id) ON DELETE CASCADE,
    index             integer NOT NULL CHECK (index >= 1),
    text              text    NOT NULL,
    CONSTRAINT policy_paragraph_index_unique UNIQUE (policy_version_id, index)
);
