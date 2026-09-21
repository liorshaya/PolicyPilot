package com.liorshaya.policypilot.ai.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.ChatTool;
import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.ai.PromptSpec;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * The request a streamed answer sends to OpenAI (Document 4, Prompt 4: the answer prompt's model, temperature and cap,
 * with its tools). The provider refuses function tools with reasoning on the fast model of this lineup over chat
 * completions ("Function tools with reasoning_effort are not supported for gpt-5.6-luna in /v1/chat/completions ...
 * set reasoning_effort to 'none'", the live run of 2026-09-22), so a turn with tools asks for none.
 */
class StreamingOptionsTest {

    // Expected: reasoning effort none, beside the prompt's temperature 0.3, cap 1200, the model and the tool
    @Test
    void anAnswerWithToolsAsksForNoReasoning() {
        OpenAiChatOptions options = SpringAiLlmGateway.openAiStreamingOptions(spec(), "gpt-5.6-luna",
                List.of(SpringAiLlmGateway.callbackOf(tool())));

        assertThat(options.getReasoningEffort()).isEqualTo("none");
        assertThat(options.getModel()).isEqualTo("gpt-5.6-luna");
        assertThat(options.getTemperature()).isEqualTo(0.3);
        assertThat(options.getMaxCompletionTokens()).isEqualTo(1200);
        assertThat(options.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("getDecision");
    }

    // Expected: without tools the provider's own reasoning is left alone, as the author prompt's is
    @Test
    void anAnswerWithoutToolsLeavesTheReasoningToTheModel() {
        OpenAiChatOptions options = SpringAiLlmGateway.openAiStreamingOptions(spec(), "gpt-5.6-luna",
                List.<ToolCallback>of());

        assertThat(options.getReasoningEffort()).isNull();
    }

    private static PromptSpec spec() {
        return new PromptSpec("answer", "v1", ModelRole.FAST, "system", "user", null, 0.3, 1200,
                Duration.ofSeconds(60), 1);
    }

    private static ChatTool tool() {
        return new ChatTool() {
            @Override
            public String name() {
                return "getDecision";
            }

            @Override
            public String description() {
                return "Fetch a decision";
            }

            @Override
            public String inputSchema() {
                return "{\"type\":\"object\"}";
            }

            @Override
            public String call(String argumentsJson) {
                return "{}";
            }
        };
    }
}
