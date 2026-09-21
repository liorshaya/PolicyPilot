-- V8: chat sessions and their messages (Document 2, Data Model: chat_session, chat_message; Work Plan day 9).

-- A session is bound to one published version and belongs to the sandbox that opened it (Document 4, Scoping: the chat
-- never mixes versions or sandboxes).
CREATE TABLE chat_session (
    id                 uuid        PRIMARY KEY,
    sandbox_id         uuid        NOT NULL,
    ruleset_version_id uuid        NOT NULL REFERENCES ruleset_version (id),
    created_at         timestamptz NOT NULL
);
CREATE INDEX chat_session_sandbox_idx ON chat_session (sandbox_id);

-- One row per question and one per answer. turn numbers the exchanges of a session from 1, shared by a question and
-- its answer, because two messages can carry the same timestamp; the memory window reads the last 10 turns
-- (Document 4, Streaming and memory). The tool calls of an answer are stored with it, so the audit can show that a
-- counterfactual came from a simulation.
CREATE TABLE chat_message (
    id               uuid        PRIMARY KEY,
    session_id       uuid        NOT NULL REFERENCES chat_session (id),
    turn             integer     NOT NULL CHECK (turn >= 1),
    role             text        NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content          text        NOT NULL,
    citations_json   jsonb       NOT NULL DEFAULT '[]',
    tool_calls_json  jsonb       NOT NULL DEFAULT '[]',
    token_usage_json jsonb,
    at               timestamptz NOT NULL,
    CONSTRAINT chat_message_one_role_per_turn UNIQUE (session_id, turn, role)
);

GRANT SELECT, INSERT ON chat_session, chat_message TO policypilot_app;
