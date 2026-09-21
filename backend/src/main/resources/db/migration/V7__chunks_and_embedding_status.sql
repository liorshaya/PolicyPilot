-- V7: the retrieval corpus and the embedding status of a version (Document 2, Data Model: chunk and
-- ruleset_version.embedding_status; RAG pipeline; Work Plan day 8).

-- Null on a DRAFT; PENDING once published, then EMBEDDING, then READY or FAILED (Document 2, RAG pipeline, Embedding).
ALTER TABLE ruleset_version ADD COLUMN embedding_status text
    CONSTRAINT ruleset_version_embedding_status_check
        CHECK (embedding_status IN ('PENDING', 'EMBEDDING', 'READY', 'FAILED'));

-- Published versions stay immutable at the database, except for embedding_status (Document 2, ruleset_version): the
-- row minus that one column must come out of the update exactly as it went in.
CREATE OR REPLACE FUNCTION ruleset_version_is_immutable_once_published() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status <> 'DRAFT' AND to_jsonb(NEW) - 'embedding_status' IS DISTINCT FROM to_jsonb(OLD) - 'embedding_status' THEN
        RAISE EXCEPTION 'ruleset_version % is % and cannot change', OLD.id, OLD.status
            USING ERRCODE = 'object_not_in_prerequisite_state';
    END IF;
    RETURN NEW;
END
$$;

-- Versions published before this migration have never been embedded, so they start PENDING and the embedding job
-- takes them at the next start. This runs after the trigger above, which is what lets it touch a published row.
UPDATE ruleset_version SET embedding_status = 'PENDING' WHERE status <> 'DRAFT';

-- One chunk per policy paragraph (ref_id is its index) and one per rule (ref_id is its rule id) of a published
-- version (Document 4, Retrieval Pipeline, Corpus). The vector dimension is the active profile's, 1536 for OpenAI
-- and 1024 for bge-m3, and the application checks it against the embedding gateway at startup. tsv holds the text
-- after the lexical normalization of Document 4, which the application applies before it binds the text here.
CREATE TABLE chunk (
    id                 uuid        PRIMARY KEY,
    ruleset_version_id uuid        NOT NULL REFERENCES ruleset_version (id),
    kind               text        NOT NULL CONSTRAINT chunk_kind_check CHECK (kind IN ('PARAGRAPH', 'RULE')),
    ref_id             text        NOT NULL,
    text               text        NOT NULL,
    embedding          vector(${embedding-dimension}) NOT NULL,
    tsv                tsvector    NOT NULL,
    created_at         timestamptz NOT NULL,
    -- also the B-tree every query uses: it leads with the version
    CONSTRAINT chunk_ref_unique_in_version UNIQUE (ruleset_version_id, kind, ref_id)
);
CREATE INDEX chunk_embedding_idx ON chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX chunk_tsv_idx ON chunk USING gin (tsv);

-- The embedding job replaces a version's chunks, so the API role deletes them too; nothing else about chunks is
-- ever updated in place.
GRANT SELECT, INSERT, DELETE ON chunk TO policypilot_app;
