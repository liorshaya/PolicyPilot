-- V13: the fixed sentence an answer is, stored with it (Document 2, Data Model: chat_message.fixed; API Surface,
-- GET /chat/sessions/{id}; added 2026-09-29 for the assistant's conversation history).
--
-- Null for an answer the model wrote; not_covered or tool_limit for the sentence the system said in its place, as the
-- stream's done event named it, so a conversation read back is marked as it was shown. The column is additive, so the
-- v1.0.0 image still runs on this database.
ALTER TABLE chat_message ADD COLUMN fixed text CHECK (fixed IN ('not_covered', 'tool_limit'));
