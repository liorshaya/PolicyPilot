package com.liorshaya.policypilot.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.liorshaya.policypilot.ai.Completion;
import com.liorshaya.policypilot.ai.LlmGateway;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.support.OfflineEmbeddings;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The provider adapter against Ollama's own HTTP API (Document 2, Configuration and Model Providers: the {@code ollama}
 * profile; Document 4, Model Configuration per Prompt). The {@code OllamaChatModel} is Spring AI's own, and only the
 * server is a fake, a JDK {@link HttpServer} that answers {@code POST /api/chat} as Ollama does and keeps what it was
 * sent: day 15 found that the profile had never made a call, and that its first one failed inside the model. A
 * database of its own, because the profile sizes the vector column for bge-m3.
 */
@SpringBootTest
@Import(OfflineEmbeddings.class)
@ActiveProfiles("ollama")
class OllamaGatewayIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresContainerSupport.PGVECTOR_IMAGE);

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<JsonNode> ASKED = new CopyOnWriteArrayList<>();
    /** A user prompt the fake answers only after three seconds. */
    private static final String SLOW = "a slow call";
    private static final HttpServer OLLAMA = fakeOllama();

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void ollamaAtTheFake(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.ollama.base-url", () -> "http://127.0.0.1:" + OLLAMA.getAddress().getPort());
    }

    @AfterAll
    static void stopTheFake() {
        OLLAMA.stop(0);
    }

    @Autowired
    private LlmGateway gateway;

    @BeforeEach
    void forgetWhatWasAsked() {
        ASKED.clear();
    }

    // Expected: the fake's answer, word for word, and the usage it reported (Ollama's prompt_eval_count, eval_count)
    @Test
    void aStructuredCallReturnsTheModelsText() {
        Completion<String> completion = gateway.complete(spec("a structured call"), String.class);

        assertThat(completion.value()).isEqualTo("{\"id\":\"from-ollama\"}");
        assertThat(completion.usage().inputTokens()).isEqualTo(120);
        assertThat(completion.usage().outputTokens()).isEqualTo(30);
    }

    // Document 2: thinking disabled for every prompt; Document 4: the prompt's output cap and its schema as the
    // response format. Expected: think false, num_predict the spec's 8,000, and format the provider variant of the
    // rule set schema the spec names
    @Test
    void theRequestCarriesTheSchemaTheCapAndNoThinking() {
        gateway.complete(spec("the request's shape"), String.class);

        JsonNode request = ASKED.getFirst();
        assertThat(request.path("think").asBoolean(true)).isFalse();
        assertThat(request.path("options").path("num_predict").asInt()).isEqualTo(8000);
        assertThat(request.path("model").asString()).isEqualTo("qwen3:14b");
        assertThat(request.path("format")).isEqualTo(JSON.readTree(SpringAiLlmGateway.variantOf(
                "schemas/ruleset-1.0.schema.json")));
    }

    // Document 4, Model Configuration per Prompt: Ollama's client takes no timeout per call, so the gateway holds the
    // prompt's. Expected: an answer slower than the prompt's second ends as TIMEOUT, not as a wait for the model
    @Test
    void anAnswerSlowerThanThePromptsTimeoutEndsAsATimeout() {
        PromptSpec slow = new PromptSpec("author", "ollama-gateway-it", ModelRole.STRONG, "system", SLOW,
                "schemas/ruleset-1.0.schema.json", null, 8000, Duration.ofSeconds(1), 1);

        Throwable refused = catchThrowable(() -> gateway.complete(slow, String.class));

        assertThat(refused).isInstanceOf(LlmUnavailableException.class);
        assertThat(((LlmUnavailableException) refused).reason()).isEqualTo(LlmUnavailableException.Reason.TIMEOUT);
    }

    // Document 4, Prompt 4: the answer prompt streams. Expected: Ollama's lines of JSON reach the caller as its tokens,
    // in order, and the usage of the last line is the turn's
    @Test
    void aStreamedAnswerArrivesAsItsTokens() {
        StringBuilder tokens = new StringBuilder();
        PromptSpec answer = new PromptSpec("answer", "ollama-gateway-it", ModelRole.FAST, "system", "a streamed turn",
                null, 0.3, 1200, Duration.ofSeconds(60), 1);

        TokenUsage usage = gateway.stream(answer, List.of(), tokens::append);

        assertThat(tokens.toString()).isEqualTo("שלום עולם");
        assertThat(usage.inputTokens()).isEqualTo(80);
        assertThat(usage.outputTokens()).isEqualTo(4);
        assertThat(ASKED.getFirst().path("think").asBoolean(true)).isFalse();
    }

    private static PromptSpec spec(String user) {
        return new PromptSpec("author", "ollama-gateway-it", ModelRole.STRONG, "system", user,
                "schemas/ruleset-1.0.schema.json", null, 8000, Duration.ofSeconds(60), 1);
    }

    /** Answers every chat as Ollama does, with a fixed text and counts, and keeps each request's body. */
    private static HttpServer fakeOllama() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chat", exchange -> {
                JsonNode request = JSON.readTree(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
                ASKED.add(request);
                if (request.toString().contains(SLOW)) {
                    sleep(Duration.ofSeconds(3));
                }
                if (request.path("stream").asBoolean(false)) {
                    // Ollama streams one JSON object a line; the last one is done and carries the counts
                    String head = "{\"model\":\"qwen3:14b\",\"created_at\":\"2026-09-24T00:00:00Z\","
                            + "\"message\":{\"role\":\"assistant\",\"content\":";
                    byte[] lines = (head + "\"שלום \"},\"done\":false}\n"
                            + head + "\"עולם\"},\"done\":true,\"done_reason\":\"stop\","
                            + "\"prompt_eval_count\":80,\"eval_count\":4}\n").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
                    exchange.sendResponseHeaders(200, lines.length);
                    exchange.getResponseBody().write(lines);
                    exchange.close();
                    return;
                }
                byte[] body = """
                        {"model":"qwen3:14b","created_at":"2026-09-24T00:00:00Z",
                         "message":{"role":"assistant","content":"{\\"id\\":\\"from-ollama\\"}"},
                         "done":true,"done_reason":"stop","total_duration":1,"load_duration":1,
                         "prompt_eval_count":120,"prompt_eval_duration":1,"eval_count":30,"eval_duration":1}"""
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            // a thread per request, so a slow answer never holds up the next test's
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
