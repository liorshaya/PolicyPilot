package com.liorshaya.policypilot.ai.change;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.ai.service.Candidates;
import com.liorshaya.policypilot.ai.service.ChangeBase;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.Seeded;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The impact analysis on a real database (Document 2, Change impact analysis; Document 4, Prompt 5): the version a
 * change is proposed against, and its candidates. The chunks here are embedded by the offline fake, which gives every
 * text the same vector, so the nearest rules are the first two rule ids; what the provider's vectors pick for the
 * scripted request is shown on the recorded vectors.
 */
@Requirement("FR-17")
class ChangeAnalysisIT extends ApiIntegrationTest {

    @Autowired
    private ChangeAnalysis analysis;

    @Autowired
    private RulesetService rulesets;

    // The seeded lending version 1: published, embedded, titled as the seed titles it, nothing retired yet
    @Test
    void theSeededVersionIsTheBaseOfAChange() {
        ChangeBase base = analysis.base(seeded(), 1, UUID.randomUUID()).orElseThrow();

        assertThat(base.versionNo()).isEqualTo(1);
        assertThat(base.title()).isEqualTo(Fixtures.lendingV1().required("name").asString());
        assertThat(base.document()).isEqualTo(Fixtures.lendingV1());
        assertThat(base.paragraphs()).hasSize(9);
        assertThat(base.retiredIds()).isEmpty();
    }

    // Document 5: a rule set the sandbox cannot see reads as absent
    @Test
    void aRuleSetTheSandboxCannotSeeHasNoBase() {
        assertThat(analysis.base(UUID.randomUUID(), 1, UUID.randomUUID())).isEmpty();
    }

    // Document 2: a change is proposed against a PUBLISHED version; the sandbox's own copy of the seed is a DRAFT
    @Test
    void aDraftIsNoBaseForAChange() {
        UUID sandbox = UUID.randomUUID();
        UUID draft = rulesets.replaceRules(seeded(), 1, sandbox, Fixtures.lendingV1()).orElseThrow().rulesetId();

        assertThatThrownBy(() -> analysis.base(draft, 1, sandbox)).isInstanceOf(VersionStatusException.class);
    }

    // The nearest two are rules, never paragraphs, whose chunks sort before them here; the rule the request names
    // joins them; and the rule set's field references do the rest (R-010 derives what R-020 reads, R-410 tests
    // monthly_income, which R-170 and R-020 read, and R-020 derives what R-200 and R-320 test)
    @Test
    void theNearestRulesAndTheNamedOneAreTheSeeds() {
        UUID sandbox = UUID.randomUUID();
        ChangeBase base = analysis.base(seeded(), 1, sandbox).orElseThrow();

        Candidates candidates = analysis.candidates(base, "R-410: העלה את הרצועה ל-9,000 עד 10,000");

        assertThat(candidates.seeds()).containsExactly("R-010", "R-020", "R-410");
        assertThat(candidates.ruleIds()).containsExactly("R-010", "R-020", "R-170", "R-200", "R-320", "R-410");
        assertThat(candidates.fields()).containsExactly("monthly_income");
    }

    private UUID seeded() {
        return Seeded.lendingRuleset(rulesets).id();
    }
}
