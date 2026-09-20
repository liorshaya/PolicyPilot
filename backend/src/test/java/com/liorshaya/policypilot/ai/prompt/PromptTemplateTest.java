package com.liorshaya.policypilot.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The placeholder pass the prompts are rendered by (Document 4, Template format). Only {@code {name}} is a
 * placeholder; every other brace is the literal text of the prompt, which is what lets the author prompt carry
 * JSON examples in its instructions.
 */
class PromptTemplateTest {

    @Test
    void readsTheNamesItsTextDeclares() {
        PromptTemplate template = new PromptTemplate("<policy language=\"{language}\">\n{policy}\n</policy>");

        assertThat(template.placeholders()).containsExactlyInAnyOrder("language", "policy");
    }

    @Test
    void leavesJsonBracesAlone() {
        // the author prompt writes this guard inline, and it must reach the model exactly as written
        String json = "guard the rule with {\"field\": \"monthly_income\", \"op\": \"gt\", \"value\": 0}";
        PromptTemplate template = new PromptTemplate(json);

        assertThat(template.placeholders()).isEmpty();
        assertThat(template.render(Map.of())).isEqualTo(json);
    }

    @Test
    void fillsEveryOccurrenceOfAName() {
        PromptTemplate template = new PromptTemplate("in {language}; and in {language} again");

        assertThat(template.render(Map.of("language", "Hebrew")))
                .isEqualTo("in Hebrew; and in Hebrew again");
    }

    @Test
    void putsTheValueInAsItIs() {
        PromptTemplate template = new PromptTemplate("{policy}");

        // a policy full of backslashes and dollars is data, not a replacement pattern
        assertThat(template.render(Map.of("policy", "$1 \\n {not a placeholder}")))
                .isEqualTo("$1 \\n {not a placeholder}");
    }

    @Test
    void refusesValuesThatDoNotMatchItsPlaceholders() {
        PromptTemplate template = new PromptTemplate("{policy} in {language}");

        assertThatThrownBy(() -> template.render(Map.of("policy", "a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("language");
        assertThatThrownBy(() -> template.render(Map.of("policy", "a", "language", "he", "extra", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extra");
    }
}
