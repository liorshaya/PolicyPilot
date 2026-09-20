package com.liorshaya.policypilot.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.validation.RuleSetValidator;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.rules.validation.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The author prompt's few-shot example (Document 4, Few-shot example): a three-paragraph English policy and its
 * complete rule set, "chosen to demonstrate a derivation with a guard, a {@code not between} gate, a referral,
 * and the final approval" — the guard being the 30,000 cap the derivation is written around. The example is what
 * the model copies the shapes from, so it passes the same validator an analyst's edit passes, against its own
 * paragraphs; an example with an error would teach that error.
 */
class AuthorExampleTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode example() {
        String file = new PromptRegistry(List.of("author"), Map.of()).get("author").examples();
        assertThat(file).as("prompts/author/v1.examples.json is rendered into <example>").isNotNull();
        return JSON.readTree(file);
    }

    /** The paragraphs as the policy states them, without the {@code [n]} prefix the prompt cites by. */
    private static List<String> paragraphs(JsonNode example) {
        List<String> texts = new ArrayList<>();
        for (String line : example.path("policy").asString().split("\n")) {
            texts.add(line.replaceFirst("^\\[\\d+] ", ""));
        }
        return texts;
    }

    @Test
    void theExampleRuleSetValidatesAgainstTheExamplePolicy() {
        JsonNode document = example();

        ValidationResult result = new RuleSetValidator().validate(
                document.path("ruleSet"), ValidationContext.AUTHORING, paragraphs(document), Set.of());

        assertThat(result.findings()).isEmpty();
    }

    @Test
    void theExamplePolicyHasTheThreeParagraphsDocument4Gives() {
        assertThat(paragraphs(example())).hasSize(3);
    }

    @Test
    void theExampleShowsTheFourShapesItWasChosenFor() {
        JsonNode rules = example().path("ruleSet").path("rules");
        List<String> ids = new ArrayList<>();
        rules.forEach(rule -> ids.add(rule.path("id").asString()));

        // Document 4 names the four: the derivation, the not between gate, the referral, the approval
        assertThat(ids).containsExactly("R-010", "R-100", "R-110", "R-310", "R-900");
        assertThat(rules.get(0).path("actions").get(0).path("type").asString()).isEqualTo("set");
        // the DSL has no not_between operator: the gate is a not around between (Document 3, Operators)
        assertThat(rules.get(1).path("condition").path("not").path("op").asString()).isEqualTo("between");
        assertThat(rules.get(3).path("actions").get(0).path("outcome").asString()).isEqualTo("refer");
        assertThat(rules.get(4).path("actions").get(0).path("outcome").asString()).isEqualTo("approve");
    }
}
