-- V1: the pgvector extension (Document 2, Deployment Topology; Work Plan day 1, Docker Compose task).
-- The chunk table and its vector column arrive with the rag package on day 8; the policy and rule set
-- tables arrive on days 4 and 5. Published rule set versions and the audit log get their immutability
-- trigger and append-only grants in the migration that creates them.
CREATE EXTENSION IF NOT EXISTS vector;
