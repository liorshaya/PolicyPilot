package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.service.chat.AnswerWithheldException;
import com.liorshaya.policypilot.ai.service.chat.ChatCitation;
import com.liorshaya.policypilot.ai.service.chat.ChatEvents;
import com.liorshaya.policypilot.ai.service.chat.FixedAnswer;
import com.liorshaya.policypilot.ai.service.chat.ToolCallReport;
import com.liorshaya.policypilot.ai.chat.ChatService;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorEnvelope;
import com.liorshaya.policypilot.web.request.ChatMessageRequest;
import com.liorshaya.policypilot.web.request.OpenChatRequest;
import com.liorshaya.policypilot.web.response.ChatConversationResponse;
import com.liorshaya.policypilot.web.response.ChatEventPayloads;
import com.liorshaya.policypilot.web.response.ChatSessionResponse;
import com.liorshaya.policypilot.web.response.ChatSessionsResponse;
import com.liorshaya.policypilot.web.security.SandboxSession;
import com.liorshaya.policypilot.web.security.StreamRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The chat routes of Document 2, API Surface: {@code POST /api/v1/chat/sessions} opens a session bound to a version of
 * the caller's sandbox, and {@code POST /api/v1/chat/sessions/{id}/messages} answers a question as an event stream, a
 * {@code tool} event for each tool call as it ends, {@code token} events, then {@code citations}, {@code usage} and
 * {@code done} with the fixed sentence the answer is, if it is one, or {@code error} in their place. A chat stream is
 * not resumable; the web app offers a retry. {@code GET /api/v1/chat/sessions} lists the sandbox's conversations and
 * {@code GET /api/v1/chat/sessions/{id}} reads one back as it was shown (added 2026-09-29).
 */
@RestController
public class ChatController {

    private static final Logger LOG = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chat;
    private final StreamRegistry streams;
    private final ExecutorService generations;
    private final Clock clock;

    public ChatController(ChatService chat, StreamRegistry streams, ExecutorService generations, Clock clock) {
        this.chat = chat;
        this.streams = streams;
        this.generations = generations;
        this.clock = clock;
    }

    @Operation(summary = "Open a chat session bound to a published version")
    @ApiResponse(responseCode = "201", description = "The session, its version and its language",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ChatSessionResponse.class)))
    @ApiResponse(responseCode = "400", description = "The body names no rule set or no version number",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "404", description = "No such rule set version in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The version is not published",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(value = ApiPaths.CHAT_SESSIONS, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ChatSessionResponse open(@RequestBody OpenChatRequest body,
            @AuthenticationPrincipal SandboxSession session) {
        try {
            return chat.open(body.requiredRulesetId(), body.requiredVersionNo(), session.sandboxId())
                    .map(opened -> ChatSessionResponse.of(opened, chat.languageOf(opened)))
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        } catch (VersionStatusException e) {
            throw new ApiException(ErrorCode.VERSION_STATUS_CONFLICT);
        }
    }

    @Operation(summary = "The conversations of this sandbox, newest first")
    @ApiResponse(responseCode = "200",
            description = "Each session that holds at least one turn, named by its first question",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ChatSessionsResponse.class)))
    @GetMapping(value = ApiPaths.CHAT_SESSIONS, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatSessionsResponse conversations(@AuthenticationPrincipal SandboxSession session) {
        return ChatSessionsResponse.of(chat.conversations(session.sandboxId()));
    }

    @Operation(summary = "A conversation as it was shown: its version, its language and its turns")
    @ApiResponse(responseCode = "200", description = "The session, its version and its turns, oldest first",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ChatConversationResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such chat session in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @GetMapping(value = ApiPaths.CHAT_SESSION, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatConversationResponse conversation(@PathVariable UUID id,
            @AuthenticationPrincipal SandboxSession session) {
        return chat.conversation(id, session.sandboxId()).map(ChatConversationResponse::of)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    @Operation(summary = "Ask a question in a session, answered as a stream of events")
    @ApiResponse(responseCode = "200",
            description = "An event stream: tool events, token events, then citations, usage and done")
    @ApiResponse(responseCode = "400", description = "The question is empty, too long or has a control character",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "404", description = "No such chat session in this sandbox",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @ApiResponse(responseCode = "409", description = "The session's version is not embedded yet",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorEnvelope.class)))
    @PostMapping(value = ApiPaths.CHAT_MESSAGES, consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@PathVariable UUID id, @RequestBody ChatMessageRequest body,
            @AuthenticationPrincipal SandboxSession session) {
        String question = body.normalizedQuestion();
        ChatService.Prepared prepared;
        try {
            prepared = chat.prepare(id, session.sandboxId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        } catch (VersionStatusException e) {
            throw new ApiException(ErrorCode.VERSION_STATUS_CONFLICT);
        }
        StreamRegistry.Lease lease = streams.open(session.sandboxId(), clock.instant())
                .orElseThrow(() -> new ApiException(ErrorCode.RATE_LIMITED));
        SseEmitter emitter = new SseEmitter(StreamRegistry.MAX_LIFETIME.toMillis());
        emitter.onCompletion(lease::close);
        emitter.onTimeout(() -> {
            lease.close();
            emitter.complete();
        });
        generations.execute(() -> run(emitter, lease, prepared, question));
        return emitter;
    }

    private void run(SseEmitter emitter, StreamRegistry.Lease lease, ChatService.Prepared prepared, String question) {
        Events events = new Events(emitter, lease, prepared.session().versionNo());
        try {
            chat.answer(prepared, question, events);
            emitter.complete();
        } catch (ClientGone e) {
            // the client hung up; nothing more to say, and nothing half-answered was stored
            emitter.complete();
        } catch (AnswerWithheldException e) {
            fail(events, emitter, ErrorCode.ANSWER_WITHHELD, e);
        } catch (LlmUnavailableException e) {
            fail(events, emitter, ErrorCode.unavailable(e), e);
        } catch (RuntimeException e) {
            fail(events, emitter, ErrorCode.INTERNAL_ERROR, e);
        } finally {
            lease.close();
        }
    }

    private static void fail(Events events, SseEmitter emitter, ErrorCode code, RuntimeException cause) {
        LOG.atWarn().setMessage("chat.failed").addKeyValue("code", code.name()).setCause(cause).log();
        try {
            events.send("error", new ChatEventPayloads.Failed(code.name()));
        } catch (ClientGone ignored) {
            // nobody is listening for the error either
        }
        emitter.complete();
    }

    /** The answer's events as SSE, each one keeping the stream's lease alive. */
    private final class Events implements ChatEvents {

        private final SseEmitter emitter;
        private final StreamRegistry.Lease lease;
        private final int versionNo;

        Events(SseEmitter emitter, StreamRegistry.Lease lease, int versionNo) {
            this.emitter = emitter;
            this.lease = lease;
            this.versionNo = versionNo;
        }

        @Override
        public void tool(ToolCallReport call) {
            send("tool", ChatEventPayloads.Tool.of(call, versionNo));
        }

        @Override
        public void token(String text) {
            send("token", new ChatEventPayloads.Token(text));
        }

        @Override
        public void citations(List<ChatCitation> citations) {
            send("citations", ChatEventPayloads.Citations.of(citations));
        }

        @Override
        public void usage(TokenUsage usage, int toolCalls) {
            send("usage", new ChatEventPayloads.Usage(usage.inputTokens(), usage.outputTokens(), toolCalls));
        }

        @Override
        public void done(UUID messageId, @Nullable FixedAnswer fixed) {
            send("done", new ChatEventPayloads.Done(messageId, fixed == null ? null : fixed.json()));
        }

        void send(String event, Object data) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
                lease.touch(clock.instant());
            } catch (IOException e) {
                throw new ClientGone(e);
            }
        }
    }

    /** The client stopped listening; the stream ends quietly. */
    static final class ClientGone extends RuntimeException {
        ClientGone(Throwable cause) {
            super(cause);
        }
    }
}
