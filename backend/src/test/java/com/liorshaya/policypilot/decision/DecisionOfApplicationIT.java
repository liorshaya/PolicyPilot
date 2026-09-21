package com.liorshaya.policypilot.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.decision.service.BatchResult;
import com.liorshaya.policypilot.decision.service.CaseSummary;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.decision.service.DecisionView;
import com.liorshaya.policypilot.ruleset.service.PublishedVersion;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Requirement;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The decision a chat means by "application 17" (Document 2, Tools available to the {@code answer} prompt): the
 * sandbox's latest stored decision of fixture case 17 on the session's version. Expected outcomes come from
 * Document 3's worked example, where case 17 is referred by R-330.
 */
@Requirement("FR-14")
@Isolated
class DecisionOfApplicationIT extends ApiIntegrationTest {

    @Autowired
    private DecisionService decisions;

    @Autowired
    private RulesetService rulesets;

    // Expected: after the fixture set ran twice, the second run's row for case 17, referred by R-330
    @Test
    void theLatestStoredDecisionOfTheCaseIsTheOneMeant() {
        UUID sandbox = UUID.randomUUID();
        PublishedVersion version = seeded(sandbox);
        decisions.decideFixtureSet(version, sandbox, Decisions.FIXTURE_SET);
        // the clock stands still in tests; the second run is a minute later, so "latest" means one row
        clock.advance(Duration.ofMinutes(1));
        BatchResult second = decisions.decideFixtureSet(version, sandbox, Decisions.FIXTURE_SET);
        UUID latest = second.results().stream().filter(summary -> Integer.valueOf(17).equals(summary.caseNo()))
                .map(CaseSummary::id).findFirst().orElseThrow();

        DecisionView decision = decisions.ofApplication(version, sandbox, 17).orElseThrow();

        assertThat(decision.id()).isEqualTo(latest);
        assertThat(decision.caseNo()).isEqualTo(17);
        assertThat(decision.decision().path("outcome").asString()).isEqualTo("refer");
        assertThat(decision.decision().path("decidingRuleId").asString()).isEqualTo("R-330");
    }

    // Document 5, Authorization (sandbox); RT-03's premise. Expected: another sandbox, and a case no one decided,
    // find nothing
    @Test
    void anotherSandboxOrAnUndecidedCaseFindsNothing() {
        UUID sandbox = UUID.randomUUID();
        PublishedVersion version = seeded(sandbox);
        decisions.decideFixtureSet(version, sandbox, Decisions.FIXTURE_SET);
        UUID stranger = UUID.randomUUID();

        assertThat(decisions.ofApplication(seeded(stranger), stranger, 17)).isEmpty();
        assertThat(decisions.ofApplication(version, sandbox, 9999)).isEmpty();
    }

    private PublishedVersion seeded(UUID sandbox) {
        return rulesets.published(rulesets.protectedRulesets().getFirst().id(), 1, sandbox).orElseThrow();
    }
}
