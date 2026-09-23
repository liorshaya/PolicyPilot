package com.liorshaya.policypilot.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.ai.LlmMalformedOutputException;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.prompt.DslCheatSheet;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.rules.patch.PatchCode;
import com.liorshaya.policypilot.rules.patch.PatchProblem;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.Requirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The change use case through the recorded gateway (Document 4, Prompt 5 and Repair Loop; Document 6, Recorded level:
 * a valid answer, the malformed shapes, and RT-04 as the adversarial one). The valid answer is the fixture's expected
 * answer to the scripted request (change-request-1.json); the version is the seeded lending version 1.
 */
@Requirement({"FR-17", "FR-3"})
class ChangeServiceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String VALID = ChangeRequests.scriptedPatches().toString();

    private static Proposal propose(RecordedGateway gateway, String request, List<ChangeService.Stage> stages) {
        return new ChangeService(gateway, new PromptRegistry(PromptRegistry.PROMPTS, Map.of()), new DslCheatSheet())
                .propose(ChangeRequests.lendingBase(), request, ChangeRequests.scriptedCandidates(), stages::add);
    }

    private static Proposal proposeScripted(RecordedGateway gateway) {
        return propose(gateway, ChangeRequests.scripted(), new ArrayList<>());
    }

    /** The scripted answer with R-410 keeping the analyst provenance ruleset.v1.json gives it. */
    private static String keepingR410Analyst() {
        ObjectNode answer = ChangeRequests.scriptedPatches();
        for (JsonNode rule : Fixtures.lendingV1().required("rules")) {
            if (rule.required("id").asString().equals("R-410")) {
                ((ObjectNode) answer.required("patches").get(1).required("rule"))
                        .set("provenance", rule.required("provenance"));
            }
        }
        return answer.toString();
    }

    @Test
    void aValidAnswerIsAProposalWithoutRepair() {
        List<ChangeService.Stage> stages = new ArrayList<>();
        RecordedGateway gateway = RecordedGateway.answering(VALID);

        Proposal proposal = propose(gateway, ChangeRequests.scripted(), stages);

        assertThat(proposal.valid()).isTrue();
        assertThat(proposal.repairs()).isZero();
        assertThat(proposal.validation().modelRuleIds()).containsExactlyInAnyOrder("R-170", "R-410");
        assertThat(proposal.candidates()).isEqualTo(ChangeRequests.scriptedCandidates());
        assertThat(stages).containsExactly(ChangeService.Stage.PROPOSING, ChangeService.Stage.VALIDATING);
        assertThat(gateway.asked()).hasSize(1);
        assertThat(gateway.forgotten()).isEmpty();
    }

    // Document 4: an answer that is not JSON counts as a validation failure and is repaired
    @Test
    void anAnswerThatIsNotJsonIsRepaired() {
        RecordedGateway gateway = RecordedGateway.answering("I would raise R-170 to 9,000.", VALID);

        Proposal proposal = proposeScripted(gateway);

        assertThat(proposal.valid()).isTrue();
        assertThat(proposal.repairs()).isEqualTo(1);
        PromptSpec repair = gateway.asked().get(1);
        assertThat(repair.attempt()).isEqualTo(2);
        assertThat(repair.system()).isEqualTo(gateway.asked().get(0).system());
        assertThat(repair.user()).contains("DSL_SCHEMA").contains("I would raise R-170 to 9,000.");
    }

    // Document 3, Patch validation: a patched rule fails the rule schema at its patch, and that is what goes back
    @Test
    void aPatchThatFailsTheSchemaIsRepairedAtItsPath() {
        ObjectNode broken = ChangeRequests.scriptedPatches();
        ((ObjectNode) broken.required("patches").get(0).required("rule")).put("id", "RULE1");
        RecordedGateway gateway = RecordedGateway.answering(broken.toString(), VALID);

        Proposal proposal = proposeScripted(gateway);

        assertThat(proposal.valid()).isTrue();
        assertThat(gateway.asked().get(1).user()).contains("\"code\":\"DSL_SCHEMA\"")
                .contains("\"path\":\"/patches/0/rule/id\"");
    }

    // Document 3: a patched rule that claims analyst provenance fails the copy, reported at its patch
    @Test
    void aPatchedRuleThatFailsTheCopyIsRepairedAtItsPatch() {
        RecordedGateway gateway = RecordedGateway.answering(keepingR410Analyst(), VALID);

        Proposal proposal = proposeScripted(gateway);

        assertThat(proposal.valid()).isTrue();
        assertThat(gateway.asked().get(1).user()).contains("\"code\":\"PROVENANCE_ANALYST_FROM_MODEL\"")
                .contains("\"path\":\"/patches/1/rule/provenance\"");
    }

    // Document 4, Error list shape: a quote mismatch goes back with the full text of the paragraph it cites, and only
    // that one
    @Test
    void aQuoteMismatchIsRepairedWithTheParagraphItCites() {
        ObjectNode answer = ChangeRequests.scriptedPatches();
        ((ObjectNode) answer.required("patches").get(0).required("rule")).putObject("provenance")
                .put("kind", "quoted").put("paragraph", 4).put("quote", "ההכנסה החודשית תהיה 9,000 לפחות");
        RecordedGateway gateway = RecordedGateway.answering(answer.toString(), VALID);

        proposeScripted(gateway);

        String repair = gateway.asked().get(1).user();
        String paragraphs = repair.substring(repair.indexOf("<paragraph_texts>"), repair.indexOf("</paragraph_texts>"));
        assertThat(repair).contains("PROVENANCE_QUOTE_MISMATCH");
        assertThat(paragraphs).contains("[4] " + Fixtures.lendingParagraphs().get(3)).doesNotContain("[1] ");
    }

    // Document 4: two repairs, then the proposal stands invalid; its answers are forgotten, so asking again asks again
    @Test
    void threeInvalidAnswersEndInAnInvalidProposalAndAreForgotten() {
        RecordedGateway gateway = RecordedGateway.answering(keepingR410Analyst(), keepingR410Analyst(),
                keepingR410Analyst());

        Proposal proposal = proposeScripted(gateway);

        assertThat(proposal.valid()).isFalse();
        assertThat(proposal.refused()).isFalse();
        assertThat(proposal.repairs()).isEqualTo(2);
        assertThat(gateway.asked()).extracting(PromptSpec::attempt).containsExactly(1, 2, 3);
        assertThat(gateway.forgotten()).containsExactlyElementsOf(gateway.asked());
    }

    // Document 5, RT-04: the answer that obeyed the planted text is refused on the first answer, never repaired, and
    // forgotten
    @Test
    void rt04IsRefusedWithoutARepair() {
        RecordedGateway gateway = RecordedGateway.answering(ChangeRequests.rt04Answer().toString());

        Proposal proposal = propose(gateway, ChangeRequests.rt04(), new ArrayList<>());

        assertThat(proposal.refused()).isTrue();
        assertThat(proposal.repairs()).isZero();
        assertThat(proposal.validation().problems()).extracting(PatchProblem::code)
                .containsOnly(PatchCode.PATCH_REMOVES_UNMENTIONED, PatchCode.PATCH_SETS_DEFAULTS).hasSize(12);
        assertThat(gateway.asked()).hasSize(1);
        assertThat(gateway.forgotten()).containsExactlyElementsOf(gateway.asked());
    }

    // Document 3, Patch validation: the stored proposal carries the stored request's id, never the one the model wrote
    @Test
    void theStoredProposalCarriesTheRequestsIdAndNotTheModels() {
        Proposal proposal = proposeScripted(RecordedGateway.answering(VALID));

        ObjectNode stored = proposal.storedAs("8b1e6f2a-0c4d-4f7e-9a3b-5d6c7e8f9a0b");

        assertThat(proposal.answer().required("patches").get(0).required("rule").required("provenance")
                .required("changeRequestId").asString()).isEqualTo("cr-0001");
        assertThat(stored.required("patches").findValuesAsString("changeRequestId"))
                .containsOnly("8b1e6f2a-0c4d-4f7e-9a3b-5d6c7e8f9a0b").hasSize(2);
    }

    // Document 4, instruction 6: a request the DSL cannot express is an empty patch list with the reason in notes (the
    // notes are CR-6's expected ones in changes.json)
    @Test
    void anImpossibleRequestIsAValidEmptyProposalWithItsNotes() {
        ObjectNode answer = JSON.createObjectNode().put("summary", "אין שינוי").put("notes",
                ChangeRequests.labeled("CR-6").required("expected").required("notes").asString());
        answer.putArray("patches");
        answer.putArray("untouched");

        Proposal proposal = proposeScripted(RecordedGateway.answering(answer.toString()));

        assertThat(proposal.valid()).isTrue();
        assertThat(proposal.answer().required("patches")).isEmpty();
        assertThat(proposal.answer().required("notes").asString()).startsWith("The policy has no membership field");
    }

    @Test
    void threeAnswersThatAreNotJsonFailWithWhatTheProviderSent() {
        RecordedGateway gateway = RecordedGateway.answering("sorry", "sorry again", "still sorry");

        assertThatThrownBy(() -> proposeScripted(gateway))
                .isInstanceOf(LlmMalformedOutputException.class)
                .hasMessageContaining("after 3 attempts")
                .extracting(thrown -> ((LlmMalformedOutputException) thrown).raw()).isEqualTo("still sorry");
        assertThat(gateway.forgotten()).hasSize(3);
    }

    @Test
    void aProviderFailureIsNotRepaired() {
        RecordedGateway gateway = RecordedGateway.scripted(List.of(
                (Supplier<String>) () -> {
                    throw new LlmUnavailableException(LlmUnavailableException.Reason.TIMEOUT, "60 s passed");
                }));

        assertThatThrownBy(() -> proposeScripted(gateway)).isInstanceOf(LlmUnavailableException.class);
        assertThat(gateway.asked()).hasSize(1);
    }
}
