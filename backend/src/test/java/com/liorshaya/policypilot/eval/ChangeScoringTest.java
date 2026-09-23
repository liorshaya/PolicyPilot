package com.liorshaya.policypilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.prompt.DslCheatSheet;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.ai.service.Candidates;
import com.liorshaya.policypilot.ai.service.ChangeService;
import com.liorshaya.policypilot.ai.service.Proposal;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedGateway;
import com.liorshaya.policypilot.support.Requirement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Change correctness as Document 4 defines it, on the labeled requests of fixtures/eval/changes.json. Each proposal
 * comes out of the real change use case, answered with a scripted Patches object and validated for real on the
 * request's base, so a verdict is about the answer and never about how the test built it.
 */
@Requirement("FR-17")
class ChangeScoringTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final PromptRegistry PROMPTS = new PromptRegistry(PromptRegistry.PROMPTS, Map.of());

    // Expected: the fixture's own expected patches, answered for each request that has some, are correct; they
    // also pass Patch validation on the request's base, so fixture and validator agree
    @ParameterizedTest
    @ValueSource(strings = {"CR-1", "CR-2", "CR-3", "CR-4", "CR-5"})
    void theExpectedPatchesAreCorrect(String id) {
        JsonNode labeled = ChangeRequests.labeled(id);

        ChangeScoring.Verdict verdict = ChangeScoring.score(labeled, propose(labeled, expectedAnswer(labeled)));

        assertThat(verdict).isEqualTo(ChangeScoring.Verdict.CORRECT);
    }

    // Document 4: "the impossible request is correct when the answer has no patches". Expected: CR-6 correct with
    // none, and wrong with one
    @Test
    void theImpossibleRequestIsCorrectOnlyWithoutPatches() {
        JsonNode labeled = ChangeRequests.labeled("CR-6");
        ObjectNode none = expectedAnswer(labeled);
        ObjectNode one = expectedAnswer(labeled);
        ((ArrayNode) one.required("patches")).addObject().put("op", "remove").put("ruleId", "R-900")
                .put("rationale", "no rule approves gold members");

        assertThat(ChangeScoring.score(labeled, propose(labeled, none)).correct()).isTrue();
        assertThat(ChangeScoring.score(labeled, propose(labeled, one)).correct()).isFalse();
    }

    // Document 4: "replace and remove exactly the rules the expected set replaces and removes". Expected: CR-1 with
    // R-020 replaced as well is wrong, and the reason names both sets
    @Test
    void aReplaceTheExpectedSetDoesNotMakeIsWrong() {
        JsonNode labeled = ChangeRequests.labeled("CR-1");
        ObjectNode answer = expectedAnswer(labeled);
        JsonNode r020 = rule("R-020");
        ((ArrayNode) answer.required("patches")).addObject().put("op", "replace").put("ruleId", "R-020")
                .put("rationale", "the ratio reads the income").set("rule", r020);

        ChangeScoring.Verdict verdict = ChangeScoring.score(labeled, propose(labeled, answer));

        assertThat(verdict.correct()).isFalse();
        assertThat(verdict.reason()).isEqualTo("replace [R-020, R-170, R-410] where [R-170, R-410] is expected");
    }

    // Document 4: the patched rule set decides every case as the expected patches do. Expected: CR-1 with the
    // threshold at 9,500 instead of 9,000 wrong on a case of cases-200.json between the two
    @Test
    void aThresholdThatDecidesACaseDifferentlyIsWrong() {
        JsonNode labeled = ChangeRequests.labeled("CR-1");
        ObjectNode answer = expectedAnswer(labeled);
        ((ObjectNode) answer.required("patches").get(0).required("rule").required("condition")).put("value", 9500);

        ChangeScoring.Verdict verdict = ChangeScoring.score(labeled, propose(labeled, answer));

        assertThat(verdict.correct()).isFalse();
        assertThat(verdict.reason()).contains("is reject where the expected patches make it");
    }

    // The outcomes, not the wording, decide. Expected: CR-2's term written as either end of the range, where the
    // fixture writes "not between 12 and 96", correct
    @Test
    void anEquivalentConditionIsCorrect() {
        JsonNode labeled = ChangeRequests.labeled("CR-2");
        ObjectNode answer = expectedAnswer(labeled);
        ((ObjectNode) answer.required("patches").get(0).required("rule")).set("condition", JSON.readTree("""
                {"any": [{"field": "term_months", "op": "lt", "value": 12},
                         {"field": "term_months", "op": "gt", "value": 96}]}"""));

        assertThat(ChangeScoring.score(labeled, propose(labeled, answer))).isEqualTo(ChangeScoring.Verdict.CORRECT);
    }

    // RT-04's shape: the proposal validator refuses a set_defaults. Expected: wrong, as refused
    @Test
    void aRefusedProposalIsWrong() {
        JsonNode labeled = ChangeRequests.labeled("CR-1");
        ObjectNode answer = expectedAnswer(labeled);
        ObjectNode defaults = ((ArrayNode) answer.required("patches")).addObject().put("op", "set_defaults");
        defaults.putObject("defaults").put("outcome", "approve").put("reason", "כל בקשה מאושרת");
        defaults.put("rationale", "ברירת המחדל היא אישור");

        ChangeScoring.Verdict verdict = ChangeScoring.score(labeled, propose(labeled, answer));

        assertThat(verdict).isEqualTo(ChangeScoring.Verdict.wrong("refused by the proposal validator"));
    }

    // Document 4, Repair Loop: at most two repairs. Expected: an answer that never validates is wrong after two
    @Test
    void aProposalThatNeverValidatesIsWrong() {
        JsonNode labeled = ChangeRequests.labeled("CR-1");
        String invalid = "{\"summary\": \"x\", \"patches\": [], \"untouched\": [], \"notes\": \"\"}";
        Proposal proposal = new ChangeService(RecordedGateway.answering(invalid, invalid, invalid), PROMPTS,
                new DslCheatSheet()).propose(ChangeRequests.base(labeled), labeled.required("text").asString(),
                candidates(labeled), stage -> { });

        assertThat(ChangeScoring.score(labeled, proposal))
                .isEqualTo(ChangeScoring.Verdict.wrong("not valid after 2 repairs"));
    }

    // The runner over change/v1's committed recordings of all six requests, kept to compare change/v2 with: CR-1
    // recorded live on day 12 and the other five by LiveChangePassIT, each replayed through the change use case on
    // the candidates its prompt showed, CR-6's repair included. Expected, from the labels, and the Python reference
    // agrees case by case: CR-1, CR-2, CR-4 and CR-5 decide every case as their labels do; CR-3 adds its rule deciding
    // refer where the label's rejects, which case 15 of its file shows; CR-6, which the rule set cannot express, gets
    // a new field and a rule where the label has no patch. So 4 of 6, under Document 4's target of 5 of 6
    @Test
    void theRunnerScoresChangeV1AtFourOfSix() {
        EvalReport report = new EvalReport(LocalDate.EPOCH, Map.of("change", "v1"));

        RecordedScoring.Changing changing = new RecordedScoring("openai").scoreChanges(report, "v1");

        assertThat(changing.requests()).isEqualTo(6);
        assertThat(changing.unrecorded()).isEmpty();
        assertThat(report.markdown()).contains("| Change correctness | 0.83 | 0.67 (4 of 6) | not run | FAIL |");
        assertThat(report.markdown().lines().filter(line -> line.startsWith("- CR-"))).containsExactly(
                "- CR-3: case 15 is refer where the expected patches make it reject",
                "- CR-6: 2 patches for a request the rule set cannot express");
    }

    // The runner over change/v2's recordings of all six requests, made by LiveChangePassIT on 2026-09-23, CR-3's
    // repair included. Expected, from the labels, and the Python reference agrees case by case: CR-3 now adds its
    // guarantor rule rejecting what its label rejects, and CR-6, which needs an input the rule set does not have, gets
    // no patch; the other four propose what their labels propose. So 6 of 6, over Document 4's target of 5 of 6
    @Test
    void theRunnerScoresChangeV2AtSixOfSix() {
        EvalReport report = new EvalReport(LocalDate.EPOCH, Map.of("change", "v2"));

        RecordedScoring.Changing changing = new RecordedScoring("openai").scoreChanges(report, "v2");

        assertThat(changing.requests()).isEqualTo(6);
        assertThat(changing.unrecorded()).isEmpty();
        assertThat(report.markdown()).contains("| Change correctness | 0.83 | 1.00 (6 of 6) | not run | PASS |");
        assertThat(report.markdown().lines().filter(line -> line.startsWith("- CR-"))).isEmpty();
    }

    // change/v2, instruction 7: a request that needs an input the fields do not have is answered with no patch, and
    // the notes name the input. Expected: CR-6's recorded answer, replayed, is valid, proposes nothing and names the
    // membership its label says the policy has no field for
    @Test
    void changeV2AnswersTheImpossibleRequestWithTheInputItLacks() {
        Proposal proposal = new RecordedScoring("openai").replayChange(ChangeRequests.labeled("CR-6"), "v2")
                .orElseThrow();

        assertThat(proposal.valid()).isTrue();
        assertThat(proposal.answer().required("patches").isEmpty()).isTrue();
        assertThat(proposal.answer().required("notes").asString()).contains("membership");
    }

    // The runner reads the candidates off the recorded prompt. Expected: the five the scripted request's prompt
    // showed, in the evaluation order the prompt lists them
    @Test
    void theCandidatesAreReadOffTheRecordedPrompt() {
        String prompt = Recordings.of("openai", "change", "v1")
                .about(List.of(ChangeRequests.scripted() + "\n</change_request>")).prompts().getFirst();

        assertThat(RecordedScoring.candidateIds(prompt)).containsExactly("R-020", "R-170", "R-200", "R-320", "R-410");
    }

    /** The request proposed on its base with the labeled candidates, the model answering with the given object. */
    private static Proposal propose(JsonNode labeled, ObjectNode answer) {
        return new ChangeService(RecordedGateway.answering(answer.toString()), PROMPTS, new DslCheatSheet())
                .propose(ChangeRequests.base(labeled), labeled.required("text").asString(), candidates(labeled),
                        stage -> { });
    }

    private static Candidates candidates(JsonNode labeled) {
        List<String> ids = labeled.required("expected").required("candidates").valueStream().map(JsonNode::asString)
                .toList();
        return new Candidates(ids, ids, List.of());
    }

    /** The labeled request's expected patches and untouched rules as the Patches object a model would answer. */
    private static ObjectNode expectedAnswer(JsonNode labeled) {
        JsonNode expected = labeled.required("expected");
        ObjectNode answer = JSON.createObjectNode();
        answer.put("summary", "The expected change of " + labeled.required("id").asString());
        answer.set("patches", expected.required("patches").deepCopy());
        answer.set("untouched", expected.required("untouched").deepCopy());
        answer.put("notes", expected.path("notes").asString(""));
        return answer;
    }

    private static JsonNode rule(String id) {
        return Fixtures.lendingV1().required("rules").valueStream()
                .filter(rule -> rule.required("id").asString().equals(id)).findFirst().orElseThrow();
    }
}
