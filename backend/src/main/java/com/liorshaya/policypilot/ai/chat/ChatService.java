package com.liorshaya.policypilot.ai.chat;

import com.liorshaya.policypilot.ai.AnswerCache;
import com.liorshaya.policypilot.ai.CachedAnswer;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.entity.ChatMessageEntity;
import com.liorshaya.policypilot.ai.entity.ChatSessionEntity;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.ai.repository.ChatMessageRepository;
import com.liorshaya.policypilot.ai.repository.ChatSessionRepository;
import com.liorshaya.policypilot.ai.service.chat.AnswerComposer;
import com.liorshaya.policypilot.ai.service.chat.CarriedNames;
import com.liorshaya.policypilot.ai.service.chat.ChatCitation;
import com.liorshaya.policypilot.ai.service.chat.ChatCitations;
import com.liorshaya.policypilot.ai.service.chat.ChatEvents;
import com.liorshaya.policypilot.ai.service.chat.ChatHistory;
import com.liorshaya.policypilot.ai.service.chat.ChatPrompt;
import com.liorshaya.policypilot.ai.service.chat.ChatTurn;
import com.liorshaya.policypilot.ai.service.chat.FixedAnswer;
import com.liorshaya.policypilot.ai.service.chat.FixedSentences;
import com.liorshaya.policypilot.ai.service.chat.OutputDenylist;
import com.liorshaya.policypilot.ai.service.chat.ScriptedAnswers;
import com.liorshaya.policypilot.ai.service.chat.ToolCallReport;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.rag.service.NotCoveredSentences;
import com.liorshaya.policypilot.rag.service.Retrieval;
import com.liorshaya.policypilot.rag.service.RetrievalService;
import com.liorshaya.policypilot.rag.service.RetrievedChunk;
import com.liorshaya.policypilot.rules.model.Language;
import com.liorshaya.policypilot.ruleset.service.EmbeddingSource;
import com.liorshaya.policypilot.ruleset.service.PublishedVersion;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The chat use case (Document 2, Flow 3; Document 4, Prompt 4): a session bound to one published version of the
 * caller's sandbox, and a question answered from what retrieval found, the last 10 turns and what the tools return.
 * A question below the Threshold gets the fixed sentence without a model call, unless it follows up on a rule, a
 * field or a decision the turns before it carry ({@link CarriedNames}); any other is streamed by the
 * {@link AnswerComposer}. A scripted question is served from the {@link AnswerCache} when the answer kept for it still
 * holds in this sandbox, and a live answer to one is kept when it meets its label (Document 4, Serving the scripted
 * questions from the cache). The question and the answer shown are stored as one turn, with the answer's citations,
 * tool calls and usage. This class is the part that reads and writes the database; what can be tested without one
 * lives in {@code ai.service.chat}.
 */
@Service
public class ChatService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Reads what earlier rows stored, a field they did not have yet read as absent. */
    private static final JsonMapper STORED = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final TypeReference<List<ChatCitation>> CITATIONS = new TypeReference<>() {};
    private static final TypeReference<List<ToolCallReport>> TOOL_CALLS = new TypeReference<>() {};
    private static final String PROMPT = "answer";

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final RulesetService rulesets;
    private final RetrievalService retrieval;
    private final DecisionTools tools;
    private final PromptRegistry prompts;
    private final Clock clock;
    private final AnswerComposer composer;
    private final AnswerCache cache;
    private final ScriptedAnswers scripted = ScriptedAnswers.load();
    private final NotCoveredSentences notCovered = NotCoveredSentences.load();

    public ChatService(ChatSessionRepository sessions, ChatMessageRepository messages, RulesetService rulesets,
            RetrievalService retrieval, DecisionTools tools, LlmGateway gateway, AnswerCache cache,
            PromptRegistry prompts, SecurityEvents events, Clock clock,
            PolicyPilotProperties properties, @Value("${spring.ai.openai.api-key:}") String providerKey) {
        this.sessions = sessions;
        this.messages = messages;
        this.rulesets = rulesets;
        this.retrieval = retrieval;
        this.tools = tools;
        this.prompts = prompts;
        this.clock = clock;
        this.cache = cache;
        OutputDenylist denylist = new OutputDenylist(List.of(orEmpty(properties.accessCode()),
                orEmpty(properties.adminCode()), orEmpty(properties.cookieSecret()), providerKey));
        this.composer = new AnswerComposer(gateway, denylist, events, FixedSentences.toolLimit());
    }

    /**
     * Opens a session on a published version the sandbox can see; empty when it cannot, and a status conflict for a
     * version that is not published (Document 2, {@code POST /chat/sessions}).
     */
    @Transactional
    public Optional<ChatSessionView> open(UUID rulesetId, int versionNo, UUID sandboxId) {
        return rulesets.published(rulesetId, versionNo, sandboxId).map(version -> {
            ChatSessionEntity session = sessions.save(
                    new ChatSessionEntity(UUID.randomUUID(), sandboxId, version.versionId(), clock.instant()));
            return view(session, version);
        });
    }

    /** The language of the version a session is bound to: its rule set's, which the answers are written in. */
    @Transactional(readOnly = true)
    public String languageOf(ChatSessionView session) {
        return rulesets.publishedById(session.versionId(), session.sandboxId()).orElseThrow()
                .compiled().ruleSet().language().json();
    }

    /**
     * Everything a question needs before its stream opens: the session of this sandbox, its version and the corpus
     * retrieval will search. Empty for another sandbox's session; a status conflict while the version is not READY
     * (Document 4, Embedding).
     */
    @Transactional(readOnly = true)
    public Optional<Prepared> prepare(UUID sessionId, UUID sandboxId) {
        return sessions.findByIdAndSandboxId(sessionId, sandboxId).map(session -> {
            PublishedVersion version = rulesets.publishedById(session.getRulesetVersionId(), sandboxId)
                    .orElseThrow(() -> new VersionStatusException("the session's version is gone"));
            EmbeddingSource corpus = rulesets.corpus(version.rulesetId(), version.versionNo(), sandboxId)
                    .orElseThrow(() -> new VersionStatusException("the session's version is gone"));
            return new Prepared(view(session, version), version, corpus);
        });
    }

    /**
     * Answers one question, sending each event as it happens; returns the stored answer's id.
     *
     * @throws com.liorshaya.policypilot.ai.service.chat.AnswerWithheldException when the denylist scan stopped the
     *     answer; nothing of it is stored
     * @throws com.liorshaya.policypilot.ai.LlmUnavailableException when the provider failed or missed a deadline
     */
    public UUID answer(Prepared prepared, String question, ChatEvents sink) {
        ChatSessionView session = prepared.session();
        EmbeddingSource corpus = prepared.corpus();
        Language language = corpus.ruleSet().language();
        List<ChatMessageEntity> earlier = messages.findBySessionIdOrderByTurnAscRoleDesc(session.id());
        int turnNo = earlier.stream().mapToInt(ChatMessageEntity::getTurn).max().orElse(0) + 1;
        List<Conversation.Turn> before = turnsOf(earlier);
        Retrieval found = retrieval.retrieve(session.rulesetId(), session.versionNo(), session.sandboxId(), question,
                        CarriedNames.of(cited(before), corpus.ruleSet()))
                .orElseThrow(() -> new VersionStatusException("the session's version is gone"));
        if (!found.covered()) {
            sink.token(found.notCovered());
            return finish(session, turnNo, question, found.notCovered(), List.of(), List.of(), TokenUsage.NONE,
                    FixedAnswer.NOT_COVERED, sink);
        }
        PromptSpec spec = ChatPrompt.spec(prompts.get(PROMPT), session.versionNo(), session.domain(), language,
                found.chunks(), ChatHistory.of(exchanges(before)), question, notCovered.of(language));
        Optional<ScriptedAnswers.Label> label = scripted.labelOf(question);
        Optional<CachedAnswer> cached = label.isPresent() ? cache.find(spec) : Optional.empty();
        if (cached.isPresent()) {
            List<ToolCallReport> held = new ArrayList<>();
            List<ToolCallReport> reported = Collections.synchronizedList(new ArrayList<>());
            ChatTurn turn = turnOf(found, report -> {
                reported.add(report);
                held.add(report);
            });
            ChatEvents replaying = new ReportingFirst(held, sink);
            Optional<AnswerComposer.Answer> replayed = composer.replay(spec, cached.get(),
                    tools.forTurn(turn, prepared.version(), corpus.ruleSet(), session.sandboxId()), turn, language,
                    notCovered.of(language), replaying);
            if (replayed.isPresent()) {
                cache.served(spec);
                return finish(session, turnNo, question, replayed.get(), turn, reported, corpus, replaying);
            }
        }
        List<ToolCallReport> reported = Collections.synchronizedList(new ArrayList<>());
        ChatTurn turn = turnOf(found, report -> {
            reported.add(report);
            sink.tool(report);
        });
        AnswerComposer.Answer answer = composer.compose(spec,
                tools.forTurn(turn, prepared.version(), corpus.ruleSet(), session.sandboxId()), turn, language,
                notCovered.of(language), sink);
        if (label.isPresent() && !answer.overrun() && scripted.accepts(label.get(), answer.text(), answer.cited())) {
            cache.keep(spec, new CachedAnswer(answer.text(), answer.steps()));
        }
        return finish(session, turnNo, question, answer, turn, reported, corpus, sink);
    }

    /**
     * The sandbox's conversations, newest first by their last answer (Document 2, {@code GET /chat/sessions}): each
     * session that holds at least one turn, named by its first question. A session opened and never asked is left out.
     */
    @Transactional(readOnly = true)
    public List<ConversationSummary> conversations(UUID sandboxId) {
        List<ChatSessionEntity> opened = sessions.findBySandboxIdOrderByCreatedAtDesc(sandboxId);
        if (opened.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ChatMessageEntity>> bySession = messages
                .findBySessionIdInOrderBySessionIdAscTurnAscRoleDesc(opened.stream().map(ChatSessionEntity::getId).toList())
                .stream().collect(Collectors.groupingBy(ChatMessageEntity::getSessionId));
        return opened.stream()
                .flatMap(session -> summaryOf(session, bySession.getOrDefault(session.getId(), List.of()), sandboxId)
                        .stream())
                .sorted(Comparator.comparing(ConversationSummary::lastAt).reversed())
                .toList();
    }

    /**
     * A conversation as it was shown, its turns oldest first (Document 2, {@code GET /chat/sessions/{id}}); empty for
     * another sandbox's session, which reads as absent (Document 5, no existence oracle).
     */
    @Transactional(readOnly = true)
    public Optional<Conversation> conversation(UUID sessionId, UUID sandboxId) {
        return sessions.findByIdAndSandboxId(sessionId, sandboxId).flatMap(session -> rulesets
                .publishedById(session.getRulesetVersionId(), sandboxId)
                .map(version -> new Conversation(view(session, version),
                        version.compiled().ruleSet().language().json(), session.getCreatedAt(),
                        turnsOf(messages.findBySessionIdOrderByTurnAscRoleDesc(session.getId())))));
    }

    /** A session's summary, or empty for one that holds no turn or whose version the sandbox cannot see. */
    private Optional<ConversationSummary> summaryOf(ChatSessionEntity session, List<ChatMessageEntity> stored,
            UUID sandboxId) {
        List<Conversation.Turn> turns = turnsOf(stored);
        if (turns.isEmpty()) {
            return Optional.empty();
        }
        return rulesets.publishedById(session.getRulesetVersionId(), sandboxId)
                .map(version -> new ConversationSummary(session.getId(), version.rulesetId(), version.versionNo(),
                        turns.getFirst().question(), turns.size(), session.getCreatedAt(),
                        turns.getLast().answeredAt()));
    }

    /** The stored messages as turns, a question and the answer shown for it each; a question with no answer is skipped. */
    private static List<Conversation.Turn> turnsOf(List<ChatMessageEntity> stored) {
        List<Conversation.Turn> turns = new ArrayList<>();
        ChatMessageEntity question = null;
        for (ChatMessageEntity message : stored) {
            if (ChatMessageEntity.USER.equals(message.getRole())) {
                question = message;
            } else if (question != null) {
                String fixed = message.getFixed();
                turns.add(new Conversation.Turn(message.getTurn(), question.getContent(), question.getAt(),
                        message.getContent(), message.getAt(), read(message.getCitationsJson(), CITATIONS),
                        read(message.getToolCallsJson(), TOOL_CALLS),
                        fixed == null ? null : FixedAnswer.valueOf(fixed.toUpperCase(Locale.ROOT))));
                question = null;
            }
        }
        return turns;
    }

    /** A stored JSON list, read leniently: a row written before a field existed reads without it. */
    private static <T> List<T> read(String json, TypeReference<List<T>> type) {
        return STORED.readValue(json, type);
    }

    /** A turn that may cite what retrieval found, whose calls are reported as they end. */
    private static ChatTurn turnOf(Retrieval found, Consumer<ToolCallReport> reports) {
        return new ChatTurn(found.chunks().stream().map(RetrievedChunk::id).collect(Collectors.toSet()), reports);
    }

    private UUID finish(ChatSessionView session, int turnNo, String question, AnswerComposer.Answer answer,
            ChatTurn turn, List<ToolCallReport> calls, EmbeddingSource corpus, ChatEvents sink) {
        return finish(session, turnNo, question, answer.text(),
                ChatCitations.of(answer.cited(), turn, corpus.ruleSet(), corpus.paragraphs()), calls,
                answer.usage(), answer.fixed(), sink);
    }

    private UUID finish(ChatSessionView session, int turnNo, String question, String answer,
            List<ChatCitation> citations, List<ToolCallReport> calls, TokenUsage usage,
            @Nullable FixedAnswer fixed, ChatEvents sink) {
        sink.citations(citations);
        sink.usage(usage, calls.size());
        UUID answerId = store(session.id(), turnNo, question, answer, citations, calls, usage, fixed);
        sink.done(answerId, fixed);
        return answerId;
    }

    /**
     * The question and the answer shown, in one transaction: a turn is stored whole or not at all. The tool calls are
     * stored as the {@code tool} events reported them, arguments and cited id included, so a conversation reads back
     * as it was shown and the audit can still see that a counterfactual came from a simulation.
     */
    private UUID store(UUID sessionId, int turnNo, String question, String answer, List<ChatCitation> citations,
            List<ToolCallReport> calls, TokenUsage usage, @Nullable FixedAnswer fixed) {
        ChatMessageEntity asked = new ChatMessageEntity(UUID.randomUUID(), sessionId, turnNo, ChatMessageEntity.USER,
                question, "[]", "[]", null, clock.instant(), null);
        ObjectNode usageJson = JSON.createObjectNode().put("inputTokens", usage.inputTokens())
                .put("outputTokens", usage.outputTokens());
        UUID answerId = UUID.randomUUID();
        ChatMessageEntity answered = new ChatMessageEntity(answerId, sessionId, turnNo, ChatMessageEntity.ASSISTANT,
                answer, JSON.writeValueAsString(citations), JSON.writeValueAsString(List.copyOf(calls)),
                usageJson.toString(), clock.instant(), fixed == null ? null : fixed.json());
        messages.saveAll(List.of(asked, answered));
        return answerId;
    }

    /** The earlier turns as the answer prompt's history reads them: a question and the answer shown for it each. */
    private static List<ChatHistory.Turn> exchanges(List<Conversation.Turn> before) {
        return before.stream().map(turn -> new ChatHistory.Turn(turn.question(), turn.answer())).toList();
    }

    /** The earlier turns as a follow-up reads them: a question and what the answer shown for it cited each. */
    private static List<CarriedNames.Turn> cited(List<Conversation.Turn> before) {
        return before.stream().map(turn -> new CarriedNames.Turn(turn.question(), turn.citations())).toList();
    }

    private static ChatSessionView view(ChatSessionEntity session, PublishedVersion version) {
        return new ChatSessionView(session.getId(), session.getSandboxId(), version.rulesetId(), version.domain(),
                version.versionNo(), version.versionId());
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    /** A session ready to take a question: the session, its version and the corpus retrieval searches. */
    public record Prepared(ChatSessionView session, PublishedVersion version, EmbeddingSource corpus) {}

    /**
     * The events of a replayed answer: the tool calls the replay ran again are reported only once it holds, before its
     * first event, so an answer that falls back to the model does not report its calls twice.
     */
    private static final class ReportingFirst implements ChatEvents {

        private final List<ToolCallReport> held;
        private final ChatEvents sink;

        ReportingFirst(List<ToolCallReport> held, ChatEvents sink) {
            this.held = held;
            this.sink = sink;
        }

        @Override
        public void tool(ToolCallReport call) {
            release();
            sink.tool(call);
        }

        @Override
        public void token(String text) {
            release();
            sink.token(text);
        }

        @Override
        public void citations(List<ChatCitation> citations) {
            release();
            sink.citations(citations);
        }

        @Override
        public void usage(TokenUsage usage, int toolCalls) {
            release();
            sink.usage(usage, toolCalls);
        }

        @Override
        public void done(UUID messageId, @Nullable FixedAnswer fixed) {
            release();
            sink.done(messageId, fixed);
        }

        private void release() {
            held.forEach(sink::tool);
            held.clear();
        }
    }
}
