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
import com.liorshaya.policypilot.ai.service.chat.ChatCitation;
import com.liorshaya.policypilot.ai.service.chat.ChatCitations;
import com.liorshaya.policypilot.ai.service.chat.ChatEvents;
import com.liorshaya.policypilot.ai.service.chat.ChatHistory;
import com.liorshaya.policypilot.ai.service.chat.ChatPrompt;
import com.liorshaya.policypilot.ai.service.chat.ChatTurn;
import com.liorshaya.policypilot.ai.service.chat.FixedSentences;
import com.liorshaya.policypilot.ai.service.chat.OutputDenylist;
import com.liorshaya.policypilot.ai.service.chat.ScriptedAnswers;
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
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The chat use case (Document 2, Flow 3; Document 4, Prompt 4): a session bound to one published version of the
 * caller's sandbox, and a question answered from what retrieval found, the last 10 turns and what the tools return.
 * A question below the Threshold gets the fixed sentence without a model call; any other is streamed by the
 * {@link AnswerComposer}. A scripted question is served from the {@link AnswerCache} when the answer kept for it still
 * holds in this sandbox, and a live answer to one is kept when it meets its label (Document 4, Serving the scripted
 * questions from the cache). The question and the answer shown are stored as one turn, with the answer's citations,
 * tool calls and usage. This class is the part that reads and writes the database; what can be tested without one
 * lives in {@code ai.service.chat}.
 */
@Service
public class ChatService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
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
            PromptRegistry prompts, SecurityEvents events, MeterRegistry meters, Clock clock,
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
        this.composer = new AnswerComposer(gateway, denylist, events, meters, FixedSentences.toolLimit());
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
        Retrieval found = retrieval.retrieve(session.rulesetId(), session.versionNo(), session.sandboxId(), question)
                .orElseThrow(() -> new VersionStatusException("the session's version is gone"));
        if (!found.covered()) {
            sink.token(found.notCovered());
            return finish(session, turnNo, question, found.notCovered(), List.of(), List.of(), TokenUsage.NONE, sink);
        }
        PromptSpec spec = ChatPrompt.spec(prompts.get(PROMPT), session.versionNo(), session.domain(), language,
                found.chunks(), ChatHistory.of(turns(earlier)), question, notCovered.of(language));
        Optional<ScriptedAnswers.Label> label = scripted.labelOf(question);
        Optional<CachedAnswer> cached = label.isPresent() ? cache.find(spec) : Optional.empty();
        if (cached.isPresent()) {
            ChatTurn turn = turnOf(found);
            Optional<AnswerComposer.Answer> replayed = composer.replay(spec, cached.get(),
                    tools.forTurn(turn, prepared.version(), corpus.ruleSet(), session.sandboxId()), turn, language,
                    notCovered.of(language), sink);
            if (replayed.isPresent()) {
                cache.served(spec);
                return finish(session, turnNo, question, replayed.get(), turn, corpus, sink);
            }
        }
        ChatTurn turn = turnOf(found);
        AnswerComposer.Answer answer = composer.compose(spec,
                tools.forTurn(turn, prepared.version(), corpus.ruleSet(), session.sandboxId()), turn, language,
                notCovered.of(language), sink);
        if (label.isPresent() && !answer.overrun() && label.get().metBy(answer.text(), answer.cited())) {
            cache.keep(spec, new CachedAnswer(answer.text(), answer.steps()));
        }
        return finish(session, turnNo, question, answer, turn, corpus, sink);
    }

    /** A turn that may cite what retrieval found. */
    private static ChatTurn turnOf(Retrieval found) {
        return new ChatTurn(found.chunks().stream().map(RetrievedChunk::id).collect(Collectors.toSet()));
    }

    private UUID finish(ChatSessionView session, int turnNo, String question, AnswerComposer.Answer answer,
            ChatTurn turn, EmbeddingSource corpus, ChatEvents sink) {
        return finish(session, turnNo, question, answer.text(),
                ChatCitations.of(answer.cited(), turn, corpus.ruleSet(), corpus.paragraphs()), turn.calls(),
                answer.usage(), sink);
    }

    private UUID finish(ChatSessionView session, int turnNo, String question, String answer,
            List<ChatCitation> citations, List<ChatTurn.ToolCallRecord> calls, TokenUsage usage, ChatEvents sink) {
        sink.citations(citations);
        sink.usage(usage, calls.size());
        UUID answerId = store(session.id(), turnNo, question, answer, citations, calls, usage);
        sink.done(answerId);
        return answerId;
    }

    /** The question and the answer shown, in one transaction: a turn is stored whole or not at all. */
    private UUID store(UUID sessionId, int turnNo, String question, String answer, List<ChatCitation> citations,
            List<ChatTurn.ToolCallRecord> calls, TokenUsage usage) {
        ChatMessageEntity asked = new ChatMessageEntity(UUID.randomUUID(), sessionId, turnNo, ChatMessageEntity.USER,
                question, "[]", "[]", null, clock.instant());
        ObjectNode usageJson = JSON.createObjectNode().put("inputTokens", usage.inputTokens())
                .put("outputTokens", usage.outputTokens());
        UUID answerId = UUID.randomUUID();
        ChatMessageEntity answered = new ChatMessageEntity(answerId, sessionId, turnNo, ChatMessageEntity.ASSISTANT,
                answer, JSON.writeValueAsString(citations), JSON.writeValueAsString(calls), usageJson.toString(),
                clock.instant());
        messages.saveAll(List.of(asked, answered));
        return answerId;
    }

    /** The session's earlier exchanges, a question and the answer shown for it each, oldest first. */
    private static List<ChatHistory.Turn> turns(List<ChatMessageEntity> earlier) {
        List<ChatHistory.Turn> turns = new ArrayList<>();
        String question = null;
        for (ChatMessageEntity message : earlier) {
            if (ChatMessageEntity.USER.equals(message.getRole())) {
                question = message.getContent();
            } else if (question != null) {
                turns.add(new ChatHistory.Turn(question, message.getContent()));
                question = null;
            }
        }
        return turns;
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
}
