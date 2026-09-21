package com.liorshaya.policypilot.ai.chat;

import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.service.chat.ChatCitation;
import com.liorshaya.policypilot.ai.service.chat.ChatEvents;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.ruleset.service.PublishedVersion;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.RulesetView;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * Demo step 3 as the scripted questions ask it (Document 1, Demo script; Document 4, Prompt 4): the seeded lending
 * version, once its chunks are READY, in a sandbox of its own that has decided the 200 fixture cases, so application
 * 17 exists for {@code getDecision} and {@code simulate}; each question in a session of its own, so no history enters
 * the prompt and a recording's hash depends on the question alone. The questions are the labeled set's.
 */
final class ScriptedQuestions {

    /** Q-01 to Q-03 are the three scripted questions; Q-04 is the rate question the documents do not cover. */
    static final List<String> SCRIPTED = List.of("Q-01", "Q-02", "Q-03");

    private final ChatService chat;
    private final RulesetService rulesets;
    private final JdbcClient jdbc;
    private final UUID sandbox = UUID.randomUUID();
    private final RulesetView seeded;

    ScriptedQuestions(ChatService chat, RulesetService rulesets, DecisionService decisions, JdbcClient jdbc) {
        this.chat = chat;
        this.rulesets = rulesets;
        this.jdbc = jdbc;
        this.seeded = rulesets.protectedRulesets().getFirst();
        PublishedVersion version = rulesets.published(seeded.id(), 1, sandbox).orElseThrow();
        awaitReady(version.versionId());
        decisions.decideFixtureSet(version, sandbox, "cases-200");
    }

    /** The labeled question with this id (fixtures/eval/questions.json). */
    static JsonNode labeled(String id) {
        for (JsonNode question : Fixtures.json("eval/questions.json").required("questions")) {
            if (question.required("id").asString().equals(id)) {
                return question;
            }
        }
        throw new IllegalArgumentException("no labeled question " + id);
    }

    /** Asks one question in a new session and returns what the client was sent. */
    Asked ask(String question) {
        ChatSessionView session = chat.open(seeded.id(), 1, sandbox).orElseThrow();
        ChatService.Prepared prepared = chat.prepare(session.id(), sandbox).orElseThrow();
        Asked asked = new Asked();
        UUID messageId = chat.answer(prepared, question, asked);
        asked.toolCalls = jdbc.sql("select tool_calls_json::text from chat_message where id = :id")
                .param("id", messageId).query(String.class).single();
        return asked;
    }

    private void awaitReady(UUID version) {
        long deadline = System.nanoTime() + 60_000_000_000L;
        String status;
        while (!"READY".equals(status = jdbc.sql("select embedding_status from ruleset_version where id = :id")
                .param("id", version).query(String.class).single())) {
            if ("FAILED".equals(status) || System.nanoTime() > deadline) {
                throw new AssertionError("the seeded version is " + status);
            }
            Thread.onSpinWait();
        }
    }

    /** Everything one answer sent: its text as shown, the ids it cited, and the tool calls stored with it. */
    static final class Asked implements ChatEvents {

        private final StringBuilder text = new StringBuilder();
        private final List<String> cited = new ArrayList<>();
        private String toolCalls = "[]";

        String text() {
            return text.toString();
        }

        List<String> cited() {
            return List.copyOf(cited);
        }

        String toolCalls() {
            return toolCalls;
        }

        @Override
        public void token(String piece) {
            text.append(piece);
        }

        @Override
        public void citations(List<ChatCitation> citations) {
            citations.forEach(citation -> cited.add(citation.id()));
        }

        @Override
        public void usage(TokenUsage usage, int toolCalls) {
            // the usage is the ledger's to measure; the answer is what these tests read
        }

        @Override
        public void done(UUID messageId) {
            // the stored message is read back through the database
        }
    }
}
