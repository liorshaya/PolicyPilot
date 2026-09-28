-- V12: a change request's number in its sandbox (Document 2, Data Model: change_request; added 2026-09-28, Register
-- phase 4), from 1, which the web app writes CR-0001. The requests stored before this migration are numbered in the
-- order they were stored, the lower id first of two stored at one instant; from here on the API numbers each request
-- as it stores it, and the unique index takes a number once in a sandbox.
--
-- Additive for the v1.0.0 image, which never reads or writes this table (V10), so it still runs here.
ALTER TABLE change_request ADD COLUMN number integer;

UPDATE change_request SET number = numbered.number
FROM (SELECT id, row_number() OVER (PARTITION BY sandbox_id ORDER BY created_at, id) AS number
      FROM change_request) AS numbered
WHERE change_request.id = numbered.id;

ALTER TABLE change_request ALTER COLUMN number SET NOT NULL;
CREATE UNIQUE INDEX change_request_sandbox_number_idx ON change_request (sandbox_id, number);
