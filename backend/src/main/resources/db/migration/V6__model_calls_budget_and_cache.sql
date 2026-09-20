-- V6: what the AI layer records about itself (Document 2, Data Model; Work Plan day 7). Every model call is
-- logged with its prompt version, tokens and latency; the day's tokens are counted in one row per day so the
-- budget guard can stop spending; a response is cached by the hash of what produced it, so the scripted demo
-- steps cost nothing the second time.

CREATE TABLE model_call (
    id                uuid        PRIMARY KEY,
    at                timestamptz NOT NULL,
    prompt_name       text        NOT NULL,
    prompt_version    text        NOT NULL,
    model             text        NOT NULL,
    provider          text        NOT NULL,
    attempt           integer     NOT NULL CHECK (attempt >= 1),
    input_tokens      integer     NOT NULL CHECK (input_tokens >= 0),
    output_tokens     integer     NOT NULL CHECK (output_tokens >= 0),
    latency_ms        bigint      NOT NULL CHECK (latency_ms >= 0),
    validation_result text        NOT NULL,
    cache_hit         boolean     NOT NULL,
    trace_id          text
);
CREATE INDEX model_call_at_idx ON model_call (at);
CREATE INDEX model_call_prompt_idx ON model_call (prompt_name, prompt_version, at);

-- The key is the hash of the prompt version, the model and the rendered input, so bumping a prompt version
-- misses on purpose (Document 4, Version discipline).
CREATE TABLE model_response_cache (
    key           text        PRIMARY KEY,
    prompt_name   text        NOT NULL,
    response_json jsonb       NOT NULL,
    created_at    timestamptz NOT NULL
);

-- One row per day: the tokens spent and whether the hard stop has been reached (Document 5, Spend caps).
CREATE TABLE token_ledger (
    day         date    PRIMARY KEY,
    tokens_used bigint  NOT NULL DEFAULT 0 CHECK (tokens_used >= 0),
    hard_stop   boolean NOT NULL DEFAULT false
);

GRANT SELECT, INSERT ON model_call TO policypilot_app;
GRANT SELECT, INSERT, DELETE ON model_response_cache TO policypilot_app;
GRANT SELECT, INSERT, UPDATE ON token_ledger TO policypilot_app;
