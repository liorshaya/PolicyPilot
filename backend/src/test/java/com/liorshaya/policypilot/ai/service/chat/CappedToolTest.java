package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.Requirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * A tool held to its turn (Document 5, Tool call volume: "at most 4 tool calls per chat turn and one simulate per
 * turn"; Document 5, Security logging: {@code ai.tool.rejected}).
 */
@Requirement("FR-14")
class CappedToolTest {

    private final ChatTurn turn = new ChatTurn(Set.of());
    private final List<String> refused = new ArrayList<>();

    // Expected: four calls answered, the fifth refused as a limit, reported, and the turn overrun
    @Test
    void fourCallsAreAnsweredAndTheFifthIsRefused() {
        CappedTool tool = tool(false, arguments -> ToolResults.result("d:17", JsonMapper.builder().build()
                .createObjectNode()));

        List<String> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.add(tool.call("{\"applicationNumber\":17}"));
        }

        assertThat(results.subList(0, 4)).allMatch(result -> result.startsWith("<tool_result id=\"d:17\">"));
        assertThat(results.get(4)).startsWith("<tool_result error=\"limit\">");
        assertThat(refused).containsExactly("getDecision:limit");
        assertThat(turn.overrun()).isTrue();
        assertThat(turn.calls()).extracting(ChatTurn.ToolCallRecord::outcome)
                .containsExactly("d:17", "d:17", "d:17", "d:17", "refused:limit");
    }

    // Expected: one simulate answered, the second refused and the turn overrun, though only two calls were made
    @Test
    void oneSimulateIsAnsweredAndTheSecondIsRefused() {
        CappedTool simulate = tool(true, arguments -> ToolResults.result("sim:d17:has_guarantor=true",
                JsonMapper.builder().build().createObjectNode()));

        simulate.call("{}");
        String second = simulate.call("{}");

        assertThat(second).startsWith("<tool_result error=\"limit\">");
        assertThat(turn.overrun()).isTrue();
    }

    // Document 5: model output is untrusted input. Expected: arguments the body refuses become a refusal the model
    // reads, reported with its reason, and the turn is not overrun by it
    @Test
    void aRefusalOfTheBodyIsARefusalTheModelReads() {
        CappedTool tool = tool(false, arguments -> {
            throw new ToolArgumentException("applicationNumber must be a whole number from 1 to 999999999");
        });

        String result = tool.call("{\"applicationNumber\":\"17\"}");

        assertThat(result).isEqualTo("<tool_result error=\"invalid_arguments\">applicationNumber must be a whole "
                + "number from 1 to 999999999</tool_result>");
        assertThat(refused).containsExactly("getDecision:invalid_arguments");
        assertThat(turn.overrun()).isFalse();
        assertThat(turn.calls()).extracting(ChatTurn.ToolCallRecord::arguments)
                .containsExactly("{\"applicationNumber\":\"17\"}");
    }

    private CappedTool tool(boolean simulation, java.util.function.Function<String, String> body) {
        return new CappedTool(simulation ? "simulate" : "getDecision", "a tool", "{}", turn, simulation, body,
                (name, reason) -> refused.add(name + ":" + reason));
    }
}
