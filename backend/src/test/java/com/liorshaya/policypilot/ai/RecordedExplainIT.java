package com.liorshaya.policypilot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.ai.service.ExplainService;
import com.liorshaya.policypilot.ai.service.ExplainService.Audience;
import com.liorshaya.policypilot.ai.service.ExplainService.Explanation;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.ruleset.service.PublishedVersion;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedModel;
import com.liorshaya.policypilot.support.Requirement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.node.ObjectNode;

/**
 * The explain prompt's recorded answers, replayed through the whole pipeline (Document 6, Recorded level). The
 * recordings are the first live run of explain/v1 (LiveExplainRecordingIT); the decisions are the engine's, on the
 * seeded version, so the prompts match the recorded ones byte for byte. Expected values: the traces of cases 17 and 2
 * (sample-decision.json and cases-expected.json) and the day's Done when.
 */
@Requirement("FR-11")
@Import(RecordedModel.class)
@Isolated
class RecordedExplainIT extends ApiIntegrationTest {

    @Autowired
    private ExplainService explainer;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private DecisionService decisions;

    private PublishedVersion version;
    private UUID sandbox;

    @BeforeEach
    void theSeededVersion() {
        sandbox = UUID.randomUUID();
        version = rulesets.published(rulesets.protectedRulesets().getFirst().id(), 1, sandbox).orElseThrow();
    }

    private Explanation explain(int caseNo, Audience audience) {
        ObjectNode input = (ObjectNode) Fixtures.json("policies/consumer-lending/cases-200.json").required("cases")
                .valueStream().filter(fixture -> fixture.path("id").asInt() == caseNo).findFirst().orElseThrow()
                .required("input");
        return explainer.explain(decisions.decide(version, sandbox, input).decision(), audience, "he").explanation();
    }

    // Work Plan day 10, Done when: "Explain on case 17 cites R-330 and its paragraph". Expected: R-330 with paragraph
    // 7, its provenance in sample-decision.json, for the officer; the applicant's structured fields cite it too
    @Test
    void caseSeventeenIsExplainedByRuleThreeThirtyAndParagraphSeven() {
        Explanation officer = explain(17, Audience.OFFICER);
        Explanation applicant = explain(17, Audience.APPLICANT);

        assertThat(officer.factors()).anySatisfy(factor -> {
            assertThat(factor.ruleId()).isEqualTo("R-330");
            assertThat(factor.paragraph()).isEqualTo(7);
        });
        assertThat(applicant.factors()).anySatisfy(factor -> assertThat(factor.ruleId()).isEqualTo("R-330"));
        // Document 4, Prompt 3: never a skipped rule; R-410, R-420 and R-900 were skipped in case 17
        assertThat(officer.factors()).noneSatisfy(factor ->
                assertThat(factor.ruleId()).isIn("R-410", "R-420", "R-900"));
    }

    // Document 4, Prompt 3: "For approve, list the flags as conditions that a person still checks". Expected: case 2 is
    // approved by R-900 with the flag STABLE_INCOME_MANUAL_CHECK (cases-expected.json)
    @Test
    void anApprovedCaseListsItsFlagAsAConditionAPersonStillChecks() {
        Explanation approved = explain(2, Audience.OFFICER);

        assertThat(approved.conditions()).extracting(ExplainService.Condition::flagCode)
                .containsExactly("STABLE_INCOME_MANUAL_CHECK");
        assertThat(approved.factors()).anySatisfy(factor -> assertThat(factor.ruleId()).isEqualTo("R-900"));
    }
}
