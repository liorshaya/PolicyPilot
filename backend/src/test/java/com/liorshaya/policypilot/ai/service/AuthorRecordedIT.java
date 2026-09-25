package com.liorshaya.policypilot.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.prompt.DslCheatSheet;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.eval.FieldHints;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.Requirement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * The authoring pipeline over what a real model actually answered (Document 6, Recorded level: the recordings a
 * live run left behind are the truth for CI). The recording is replayed by its input hash, so this test proves
 * the prompt, the cheat sheet and the pipeline work together on the lending policy without a provider, and on the
 * second domain (Work Plan day 15: "the second-domain fixture ... through one recorded generation").
 */
@Requirement("FR-2")
class AuthorRecordedIT {

    private static final Path RECORDINGS = Path.of("..", "fixtures", "eval", "recordings", "openai");

    private static PolicyVersionRef lendingPolicy() {
        return policyOf(Fixtures.lendingParagraphs());
    }

    private static PolicyVersionRef policyOf(List<String> texts) {
        List<PolicyVersionRef.Paragraph> paragraphs = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            paragraphs.add(new PolicyVersionRef.Paragraph(UUID.randomUUID(), i + 1, texts.get(i)));
        }
        return new PolicyVersionRef(UUID.randomUUID(), UUID.randomUUID(), 1, paragraphs);
    }

    @Test
    void theRecordedAnswerForTheLendingPolicyIsAValidDraft() {
        AuthorService author = new AuthorService(RecordedGateway.replaying(RECORDINGS),
                new PromptRegistry(PromptRegistry.PROMPTS, Map.of()), new DslCheatSheet());

        AuthorService.Authored authored = author.write(lendingPolicy(),
                "מדיניות אשראי צרכני - הלוואות אישיות", "he", null, stage -> { });

        assertThat(authored.findings()).isEmpty();
        assertThat(authored.valid()).isTrue();
        assertThat(authored.repairs()).isZero();
        assertThat(authored.document().get("rules")).isNotEmpty();
    }

    // Document 2, Second domain: the municipal tax discount policy, authored with the field hints of its labeled rule
    // set as the live pass of day 15 asked it. Expected: a valid draft on the first answer whose every rule cites one
    // of the policy's own paragraphs
    @Test
    void theRecordedAnswerForTheSecondDomainIsAValidDraft() {
        AuthorService author = new AuthorService(RecordedGateway.replaying(RECORDINGS),
                new PromptRegistry(PromptRegistry.PROMPTS, Map.of()), new DslCheatSheet());
        JsonNode label = Fixtures.json(Fixtures.SECOND_DOMAIN + "expected.ruleset.json");
        PolicyVersionRef policy =
                policyOf(Fixtures.paragraphs(Fixtures.evaluationPolicyText("arnona-discount-seniors")));

        AuthorService.Authored authored = author.write(policy, label.required("name").stringValue(),
                label.required("language").stringValue(), FieldHints.of(label), stage -> { });

        assertThat(authored.findings()).isEmpty();
        assertThat(authored.valid()).isTrue();
        assertThat(authored.repairs()).isZero();
        assertThat(authored.document().get("rules")).isNotEmpty()
                .allSatisfy(rule -> assertThat(rule.required("provenance").required("paragraph").asInt())
                        .isBetween(1, policy.paragraphs().size()));
    }
}
