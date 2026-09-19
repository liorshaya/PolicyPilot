package com.liorshaya.policypilot.ruleset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.ruleset.service.RulesetInvalidException;
import com.liorshaya.policypilot.ruleset.service.RulesetProblem;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionStatus;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code PUT /rulesets/{id}/versions/{no}/rules}: a manual edit of a DRAFT, validated exactly as a model draft is
 * (Brief FR-6; Document 2, API Surface; Document 3, Validation contexts; Document 5, fork on write).
 */
@Requirement("FR-6")
class RuleSetEditIT extends ApiIntegrationTest {

    @Autowired
    private PolicyService policies;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private MeterRegistry registry;

    private RulesetFixtures fixtures;

    @BeforeEach
    void fixtures() {
        fixtures = new RulesetFixtures(policies, rulesets);
    }

    // FR-6 first proof (Document 6 matrix). Expected: the code of invalid-DERIVED_ORDER.json, at the reading rule
    @Test
    void putRulesBreakingDerivedOrderIs422WithThePointerOfTheReadingRule() {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);
        String code = Fixtures.json("conformance/invalid-DERIVED_ORDER.json").path("code").asString();
        // R-020 sets debt_to_income; moving it behind R-200, which reads it, is the edit the analyst makes by hand
        ObjectNode edited = RulesetFixtures.lendingWithPriority("R-020", 250);

        assertThatThrownBy(() -> rulesets.replaceRules(draft.rulesetId(), 1, sandbox, edited))
                .isInstanceOf(RulesetInvalidException.class)
                .satisfies(thrown -> {
                    List<RulesetProblem> problems = ((RulesetInvalidException) thrown).problems();
                    assertThat(problems).extracting(RulesetProblem::code).containsExactly(code);
                    assertThat(problems.getFirst().path())
                            .startsWith("/rules/" + RulesetFixtures.ruleIndex("R-200"));
                });
    }

    // Document 2, PUT row. Expected: the submitted document read back
    @Test
    void putRulesReplacesTheDraftDocument() {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);
        ObjectNode edited = RulesetFixtures.lendingWithPriority("R-320", 321);

        rulesets.replaceRules(draft.rulesetId(), 1, sandbox, edited).orElseThrow();

        assertThat(rulesets.version(draft.rulesetId(), 1, sandbox).orElseThrow().document()).isEqualTo(edited);
    }

    // Document 3, Publishing gate: warnings are shown, not refused. Expected: invalid-REFER_PRECEDES_REJECT.json
    @Test
    void putRulesAnswersWarningsWithoutRefusing() {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);
        String warning = Fixtures.json("conformance/invalid-REFER_PRECEDES_REJECT.json").path("code").asString();

        VersionView edited = rulesets
                .replaceRules(draft.rulesetId(), 1, sandbox, RulesetFixtures.lendingWithPriority("R-320", 210))
                .orElseThrow();

        assertThat(edited.findings()).extracting(finding -> finding.code().name()).contains(warning);
        assertThat(edited.status()).isEqualTo(VersionStatus.DRAFT);
    }

    // Document 3, Validation contexts: a person may create analyst provenance through the UI. Expected: accepted
    @Test
    void putRulesAcceptsAnalystProvenanceInTheAnalystEditContext() {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);
        ObjectNode edited = Fixtures.lendingV1();

        VersionView stored = rulesets.replaceRules(draft.rulesetId(), 1, sandbox, edited).orElseThrow();

        assertThat(stored.document().withArray("rules").valueStream()
                .map(rule -> rule.path("provenance").path("kind").asString())
                .filter("analyst"::equals)
                .count()).isEqualTo(analystRulesOfTheFixture());
    }

    // Document 5, Input limits: 500 rules. Expected: the document is refused, the draft unchanged
    @Test
    void putRulesWithMoreThan500RulesIs422() {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);
        ObjectNode tooMany = Fixtures.lendingV1();
        ObjectNode rule = (ObjectNode) tooMany.withArray("rules").get(0);
        for (int i = tooMany.withArray("rules").size(); i <= 500; i++) {
            tooMany.withArray("rules").add(rule.deepCopy().put("id", "R-9%03d".formatted(i)).put("priority", 900 + i));
        }

        assertThatThrownBy(() -> rulesets.replaceRules(draft.rulesetId(), 1, sandbox, tooMany))
                .isInstanceOf(RulesetInvalidException.class);
        assertThat(rulesets.version(draft.rulesetId(), 1, sandbox).orElseThrow().document())
                .isEqualTo(Fixtures.lendingV1());
    }

    // Document 2: a DRAFT version only; Document 3, Version lineage. Expected: refused, the version unchanged
    @Test
    void putRulesOnAPublishedVersionIsRefusedAndChangesNothing() {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);
        rulesets.publish(draft.rulesetId(), 1, sandbox);
        ObjectNode edited = RulesetFixtures.lendingWithPriority("R-320", 321);

        assertThatThrownBy(() -> rulesets.replaceRules(draft.rulesetId(), 1, sandbox, edited))
                .isInstanceOf(VersionStatusException.class);
        assertThat(rulesets.version(draft.rulesetId(), 1, sandbox).orElseThrow().document())
                .isEqualTo(Fixtures.lendingV1());
    }

    // Document 5, Authorization (sandbox): a write against a protected row forks a copy. Expected: new ids
    @Test
    void putRulesOnTheProtectedVersionForksTheRuleSetIntoTheSandbox() {
        UUID sandbox = UUID.randomUUID();
        UUID seeded = fixtures.seeded().id();
        ObjectNode edited = RulesetFixtures.lendingWithPriority("R-320", 321);

        VersionView fork = rulesets.replaceRules(seeded, 1, sandbox, edited).orElseThrow();

        assertThat(fork.rulesetId()).isNotEqualTo(seeded);
        assertThat(fork.forkedFromId()).isEqualTo(seeded);
        assertThat(fork.isProtected()).isFalse();
        assertThat(fork.status()).isEqualTo(VersionStatus.DRAFT);
        assertThat(fork.document()).isEqualTo(edited);
    }

    // Document 5: the protected rows never change. Expected: fixtures/policies/consumer-lending/ruleset.v1.json
    @Test
    void theForkLeavesTheProtectedVersionEqualToTheFixture() {
        UUID sandbox = UUID.randomUUID();
        UUID seeded = fixtures.seeded().id();

        rulesets.replaceRules(seeded, 1, sandbox, RulesetFixtures.lendingWithPriority("R-320", 321));

        VersionView original = fixtures.seededVersion(sandbox);
        assertThat(original.document()).isEqualTo(Fixtures.lendingV1());
        assertThat(original.status()).isEqualTo(VersionStatus.PUBLISHED);
    }

    // Document 2, Data Model: one copy per sandbox and origin. Expected: the same rule set id, the second edit stored
    @Test
    void aSecondEditFromTheSameSandboxReusesItsFork() {
        UUID sandbox = UUID.randomUUID();
        UUID seeded = fixtures.seeded().id();

        VersionView first = rulesets.replaceRules(seeded, 1, sandbox, RulesetFixtures.lendingWithPriority("R-320", 321))
                .orElseThrow();
        VersionView second = rulesets.replaceRules(seeded, 1, sandbox, RulesetFixtures.lendingWithPriority("R-320", 322))
                .orElseThrow();

        assertThat(second.rulesetId()).isEqualTo(first.rulesetId());
        assertThat(second.versionId()).isEqualTo(first.versionId());
        assertThat(second.document()).isEqualTo(RulesetFixtures.lendingWithPriority("R-320", 322));
    }

    // Document 5, Security Logging: security.protected.write_attempt. Expected: this edit increments it
    @Test
    void aProtectedEditIncrementsTheProtectedWriteCounter() {
        UUID sandbox = UUID.randomUUID();
        double before = registry.counter("security.protected.write_attempt", "entity", "ruleset").count();

        rulesets.replaceRules(fixtures.seeded().id(), 1, sandbox, RulesetFixtures.lendingWithPriority("R-320", 321));

        // test classes run in parallel and share the registry, so the assertion is on this test's own increment
        assertThat(registry.counter("security.protected.write_attempt", "entity", "ruleset").count() - before)
                .isGreaterThanOrEqualTo(1.0);
    }

    // Document 2, PUT rules: the document's id stays the rule set's. Expected: refused at /id
    @Test
    void putRulesWithAnotherDocumentIdIsRefused() {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);
        ObjectNode renamed = Fixtures.lendingV1().put("id", "another-policy");

        assertThatThrownBy(() -> rulesets.replaceRules(draft.rulesetId(), 1, sandbox, renamed))
                .isInstanceOf(RulesetInvalidException.class)
                .satisfies(thrown -> assertThat(((RulesetInvalidException) thrown).problems())
                        .containsExactly(new RulesetProblem("/id", RulesetProblem.ID_CHANGED)));
    }

    /** How many rules of the committed lending set a person wrote (R-310 and R-410, Document 3's worked example). */
    private static long analystRulesOfTheFixture() {
        return Fixtures.lendingV1().withArray("rules").valueStream()
                .filter(rule -> "analyst".equals(rule.path("provenance").path("kind").asString()))
                .count();
    }
}
