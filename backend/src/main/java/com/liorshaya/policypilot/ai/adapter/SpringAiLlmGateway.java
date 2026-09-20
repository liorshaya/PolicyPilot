package com.liorshaya.policypilot.ai.adapter;

import com.liorshaya.policypilot.ai.Completion;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.cache.ProposalCache;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The only class that talks to a provider (Document 2, AI Layer Design; NFR-4). One call goes: the response
 * cache, then the daily token budget, then the provider with this prompt's own timeout, and then the ledger —
 * whatever the answer was. A provider that does not answer becomes a named failure, never a made-up rule.
 *
 * <p>The schema a prompt names is sent in the provider's strict structured-output mode, derived from the
 * canonical schema by {@link ProviderSchemaVariant}; the answer is still validated locally, so the provider's
 * mode is a convenience, not the guarantee.
 */
@Component
public class SpringAiLlmGateway implements LlmGateway {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** Document 4, Guardrails: 3 attempts with 1 s, 2 s and 4 s between them. */
    private static final int ATTEMPTS = 3;
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);

    private final ChatModel chat;
    private final PolicyPilotProperties properties;
    private final ProposalCache cache;
    private final TokenBudgetGuard budget;
    private final ModelCallLedger ledger;
    private final CircuitBreaker breaker;
    private final Clock clock;
    private final MeterRegistry meters;

    public SpringAiLlmGateway(ChatModel chat, PolicyPilotProperties properties, ProposalCache cache,
            TokenBudgetGuard budget, ModelCallLedger ledger, Clock clock, MeterRegistry meters) {
        this.chat = chat;
        this.properties = properties;
        this.cache = cache;
        this.budget = budget;
        this.ledger = ledger;
        this.clock = clock;
        this.meters = meters;
        this.breaker = new CircuitBreaker(clock);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Completion<T> complete(PromptSpec spec, Class<T> type) {
        if (type != String.class) {
            throw new IllegalArgumentException("the gateway answers text; the use case parses and validates it");
        }
        String model = modelFor(spec.role());
        String key = ProposalCache.keyOf(spec, model);
        Optional<String> cached = cache.find(key);
        if (cached.isPresent()) {
            ledger.record(spec, model, provider(), TokenUsage.NONE, Duration.ZERO,
                    ModelCallLedger.Results.VALID, true);
            meters.counter("policypilot.ai.cache.hit", "prompt", spec.promptName()).increment();
            return (Completion<T>) Completion.fromCache(cached.get());
        }

        budget.requireBudget();
        breaker.requireClosed();
        long started = clock.millis();
        ChatResponse response;
        try {
            response = call(spec, model);
        } catch (LlmUnavailableException e) {
            Duration latency = Duration.ofMillis(clock.millis() - started);
            ledger.record(spec, model, provider(), TokenUsage.NONE, latency,
                    ModelCallLedger.Results.UNAVAILABLE, false);
            breaker.failed();
            meters.counter("policypilot.ai.provider.failure", "reason", e.reason().name()).increment();
            throw e;
        }

        Duration latency = Duration.ofMillis(clock.millis() - started);
        TokenUsage usage = usageOf(response);
        // the tokens were spent whether or not they became an answer, so the budget is charged either way
        budget.record(usage);
        breaker.succeeded();
        String answer = response.getResult().getOutput().getText();
        if (answer == null || answer.isBlank()) {
            // The model's reasoning and its text share one budget, so a call can end with the budget spent and
            // nothing written. The provider answered; the answer was cut off. It must not reach the repair
            // loop, because repairing nothing yields a small, wrong document that looks like a good one
            // (Document 4, Guardrails), and it is not cached, or the failure would outlive the call.
            ledger.record(spec, model, provider(), usage, latency, ModelCallLedger.Results.TRUNCATED, false);
            meters.counter("policypilot.ai.provider.failure",
                    "reason", LlmUnavailableException.Reason.OUTPUT_TRUNCATED.name()).increment();
            throw new LlmUnavailableException(LlmUnavailableException.Reason.OUTPUT_TRUNCATED,
                    "the provider returned no text for " + spec.promptName() + "/" + spec.promptVersion()
                            + "; the answer did not fit in " + spec.maxOutputTokens() + " tokens");
        }
        ledger.record(spec, model, provider(), usage, latency, ModelCallLedger.Results.VALID, false);
        cache.put(key, spec.promptName(), answer);
        return (Completion<T>) Completion.fromProvider(answer, usage);
    }

    /** The call itself, retried with backoff on a failure that may pass (Document 4, Guardrails). */
    private ChatResponse call(PromptSpec spec, String model) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return chat.call(new Prompt(
                        java.util.List.of(new SystemMessage(spec.system()), new UserMessage(spec.user())),
                        optionsFor(spec, model)));
            } catch (RuntimeException e) {
                last = e;
                if (attempt == ATTEMPTS || !worthRetrying(e)) {
                    break;
                }
                sleep(FIRST_BACKOFF.multipliedBy(1L << (attempt - 1)));
            }
        }
        throw new LlmUnavailableException(reasonOf(last), "the provider did not answer", last);
    }

    private ChatOptions optionsFor(PromptSpec spec, String model) {
        if (chat instanceof OpenAiChatModel && spec.outputSchema() != null) {
            // the current lineup takes max_completion_tokens, not the older max_tokens, and a prompt that asks
            // for the model's own temperature sends none at all
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                    // the prompt's own timeout, and no retries of the client's own: the gateway retries
                    .timeout(spec.timeout())
                    .maxRetries(0)
                    .model(model)
                    .maxCompletionTokens(spec.maxOutputTokens())
                    .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                            .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                            .jsonSchema(variantOf(spec.outputSchema()))
                            // strict mode makes every declared property mandatory, and the DSL forbids some of
                            // them in context (a derived field has no default), so the model would have to write
                            // a value the validator then refuses; the schema still guides it and the canonical
                            // validator with the repair loop is what actually holds (Document 4, Output discipline)
                            .strict(false)
                            .build());
            if (spec.temperature() != null) {
                options.temperature(spec.temperature());
            }
            return options.build();
        }
        ChatOptions.Builder<?> options = ChatOptions.builder()
                .model(model)
                .maxTokens(spec.maxOutputTokens());
        if (spec.temperature() != null) {
            options.temperature(spec.temperature());
        }
        return options.build();
    }

    /** The provider's variant of the canonical schema the prompt names. */
    static String variantOf(String location) {
        try (InputStream stream = SpringAiLlmGateway.class.getClassLoader().getResourceAsStream(location)) {
            if (stream == null) {
                throw new IllegalStateException("no schema at " + location);
            }
            ObjectNode canonical = (ObjectNode) JSON.readTree(stream.readAllBytes());
            return ProviderSchemaVariant.of(canonical).toString();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + location, e);
        }
    }

    private String modelFor(ModelRole role) {
        return role == ModelRole.STRONG ? properties.ai().models().strong() : properties.ai().models().fast();
    }

    /** The provider's own name, taken from the model implementation: OpenAiChatModel is openai, and so on. */
    private String provider() {
        String name = chat.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.endsWith("chatmodel") ? name.substring(0, name.length() - "chatmodel".length()) : name;
    }

    private static TokenUsage usageOf(ChatResponse response) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return TokenUsage.NONE;
        }
        var usage = response.getMetadata().getUsage();
        return new TokenUsage(
                usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
    }

    /** A rate limit or a server error may pass on the next attempt; anything else will not. */
    static boolean worthRetrying(RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        return message.contains("429") || message.contains("500") || message.contains("502")
                || message.contains("503") || message.contains("504") || message.contains("timeout")
                || message.contains("Timeout");
    }

    static LlmUnavailableException.Reason reasonOf(@Nullable RuntimeException e) {
        String message = e == null ? "" : String.valueOf(e.getMessage());
        if (message.contains("429")) {
            return LlmUnavailableException.Reason.RATE_LIMITED;
        }
        if (message.contains("timeout") || message.contains("Timeout")) {
            return LlmUnavailableException.Reason.TIMEOUT;
        }
        return LlmUnavailableException.Reason.PROVIDER_ERROR;
    }

    private static void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException(LlmUnavailableException.Reason.PROVIDER_ERROR, "interrupted", e);
        }
    }

    /**
     * Fails fast for 30 seconds after 5 consecutive failures (Document 4, Guardrails), so a provider outage
     * becomes an immediate honest error instead of a queue of timeouts.
     */
    static final class CircuitBreaker {

        private static final int FAILURES_TO_OPEN = 5;
        private static final Duration OPEN_FOR = Duration.ofSeconds(30);

        private final Clock clock;
        private int consecutiveFailures;
        private long openedAt;

        CircuitBreaker(Clock clock) {
            this.clock = clock;
        }

        synchronized void requireClosed() {
            if (consecutiveFailures >= FAILURES_TO_OPEN && clock.millis() - openedAt < OPEN_FOR.toMillis()) {
                throw new LlmUnavailableException(LlmUnavailableException.Reason.PROVIDER_ERROR,
                        "the provider failed " + consecutiveFailures + " times in a row; not calling it for now");
            }
        }

        synchronized void succeeded() {
            consecutiveFailures = 0;
        }

        synchronized void failed() {
            consecutiveFailures++;
            if (consecutiveFailures == FAILURES_TO_OPEN) {
                openedAt = clock.millis();
            }
        }
    }
}
