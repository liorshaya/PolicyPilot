-- V3: fork on write (Document 2, Data Model: forked_from_id; Document 5, Authorization: a write against a protected
-- row is refused and forks a sandbox copy instead). One copy per sandbox and origin; a protected row is no copy.
ALTER TABLE policy_document ADD COLUMN forked_from_id uuid REFERENCES policy_document (id);
ALTER TABLE policy_document ADD CONSTRAINT policy_document_one_fork_per_sandbox UNIQUE (sandbox_id, forked_from_id);
ALTER TABLE policy_document ADD CONSTRAINT policy_document_protected_is_no_fork
    CHECK (NOT (protected AND forked_from_id IS NOT NULL));
