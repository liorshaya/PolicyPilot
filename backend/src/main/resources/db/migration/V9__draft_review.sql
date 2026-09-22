-- V9: the review stored with its draft (Document 2, Data Model: ruleset_version.review_json; Flow 1, "The review is
-- part of the draft"; Work Plan day 10).
--
-- Null until the draft is reviewed; then the review's status (DONE, FAILED or STALE), its prompt version, its
-- findings with their ids and acknowledgements, and the coverage map. Only a DRAFT changes it: the V7 trigger already
-- freezes every column of a published version but embedding_status. The column is additive, so the v1.0.0 image
-- still runs on this database.
ALTER TABLE ruleset_version ADD COLUMN review_json jsonb;
