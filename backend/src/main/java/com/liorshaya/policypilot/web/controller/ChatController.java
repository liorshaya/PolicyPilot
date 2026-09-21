package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.service.chat.AnswerWithheldException;
import com.liorshaya.policypilot.ai.service.chat.ChatCitation;
import com.liorshaya.policypilot.ai.service.chat.ChatEvents;
import com.liorshaya.policypilot.ai.chat.ChatService;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorEnvelope;
import com.liorshaya.policypilot.web.request.ChatMessageRequest;
import com.liorshaya.policypilot.web.request.OpenChatRequest;
import com.liorshaya.policypilot.web.response.ChatEventPayloads;
import com.liorshaya.policypilot.web.response.ChatSessionResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The chat routes of Document 2, API Surface: {@code POST /api/v1/chat/sessions} opens a session bound to a version of
 * the caller's sandbox, and {@code POST /api/v1/chat/sessions/{id}/messages} answers a question as an event stream,
 * {@code token} events then {@code citations}, {@code usage} and {@code done}, or {@code error} in their place. A chat
 * stream is not resumable; the web app offers a retry.
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

    @Operation(summary = "Ask a question in a session, answered as a stream of events")
    @ApiResponse(responseCode = "200", description = "An event stream: token events, then citations, usage and done")
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
        Events events = new Events(emitter, lease);
        try {
            chat.answer(prepared, question, events);
            emitter.complete();
        } catch (ClientGone e) {
            // the client hung up; nothing more to say, and nothing half-answered was stored
            emitter.complete();
        } catch (AnswerWithheldException e) {
            fail(events, emitter, ErrorCode.ANSWER_WITHHELD, e);
        } catch (LlmUnavailableException e) {
            fail(events, emitter, ErrorCode.PROVIDER_UNAVAILABLE, e);
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

        Events(SseEmitter emitter, StreamRegistry.Lease lease) {
            this.emitter = emitter;
            this.lease = lease;
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
        public void done(UUID messageId) {
            send("done", new ChatEventPayloads.Done(messageId));
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
