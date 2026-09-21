package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The argument validators of the chat's tools (Document 2, Tools available to the answer prompt; Document 5, model
 * output is untrusted input): {@code getDecision(applicationNumber)} and {@code simulate(applicationNumber,
 * overrides)}, whose overrides must name declared fields of the version. The fields are the lending fixture's.
 */
@Requirement("FR-14")
class ToolArgumentsTest {

    private static final RuleSet LENDING = new RuleSetMapper().toRuleSet(Fixtures.lendingV1());

    // Expected: the number as the model wrote it
    @Test
    void readsTheApplicationNumber() {
        assertThat(ToolArguments.applicationNumber("{\"applicationNumber\":17}")).isEqualTo(17);
    }

    // Expected: anything that is not one positive whole number of at most nine digits, alone, is refused
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{\"applicationNumber\":\"17\"}", "{\"applicationNumber\":0}",
        "{\"applicationNumber\":-3}", "{\"applicationNumber\":17.5}", "{\"applicationNumber\":1000000000}",
        "{\"applicationNumber\":17,\"sandbox\":\"x\"}", "not json", "[17]"})
    void refusesAnythingElse(String arguments) {
        assertThatThrownBy(() -> ToolArguments.applicationNumber(arguments))
                .isInstanceOf(ToolArgumentException.class);
    }

    // Document 4, Scripted demo questions: simulate(17, {has_guarantor: true}). Expected: the overrides as written
    @Test
    void readsTheOverridesOfASimulation() {
        String arguments = "{\"applicationNumber\":17,\"overrides\":{\"has_guarantor\":true}}";

        assertThat(ToolArguments.applicationNumber(arguments, "overrides")).isEqualTo(17);
        assertThat(ToolArguments.overrides(arguments, LENDING).toString()).isEqualTo("{\"has_guarantor\":true}");
    }

    // Document 2, simulate: "overrides must name declared fields". Expected: no overrides, an empty object, a field the
    // lending rule set does not declare, and overrides that are not an object are all refused, naming what was wrong
    @Test
    void refusesOverridesThatNameNoDeclaredField() {
        assertThatThrownBy(() -> ToolArguments.overrides("{\"applicationNumber\":17}", LENDING))
                .isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> ToolArguments.overrides("{\"applicationNumber\":17,\"overrides\":{}}", LENDING))
                .isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> ToolArguments.overrides(
                "{\"applicationNumber\":17,\"overrides\":{\"salary\":9000}}", LENDING))
                .isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("salary");
        assertThatThrownBy(() -> ToolArguments.overrides(
                "{\"applicationNumber\":17,\"overrides\":[\"has_guarantor\"]}", LENDING))
                .isInstanceOf(ToolArgumentException.class);
    }
}
