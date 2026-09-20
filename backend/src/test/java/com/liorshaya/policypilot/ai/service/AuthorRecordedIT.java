package com.liorshaya.policypilot.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.prompt.DslCheatSheet;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedGateway;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The authoring pipeline over what a real model actually answered (Document 6, Recorded level: the recordings a
 * live run left behind are the truth for CI). The recording is replayed by its input hash, so this test proves
 * the prompt, the cheat sheet and the pipeline work together on the lending policy without a provider.
 */
class AuthorRecordedIT {

    private static final Path RECORDINGS = Path.of("..", "fixtures", "eval", "recordings", "openai");

    private static PolicyVersionRef lendingPolicy() {
        List<PolicyVersionRef.Paragraph> paragraphs = new ArrayList<>();
        List<String> texts = Fixtures.lendingParagraphs();
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
}
