package com.liorshaya.policypilot.demo.service;

import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.RulesetView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads the demo policy, its rule set and its 200 cases on an empty database (Document 2, Local: a seed job loads the
 * fixtures; Work Plan days 4 and 5), and the second domain's policy and rule set (Document 2, Second domain), as the
 * protected rows every sandbox reads and none may modify. It runs at startup and on every reset, and does nothing when
 * the protected rows are already there. The fixture text is clean as committed (NFC, no format or control characters,
 * checked by FixtureSeedIT), so it is stored as it is.
 */
@Component
public class FixtureLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(FixtureLoader.class);

    private final PolicyService policies;
    private final RulesetService rulesets;
    private final DecisionService decisions;

    public FixtureLoader(PolicyService policies, RulesetService rulesets, DecisionService decisions) {
        this.policies = policies;
        this.rulesets = rulesets;
        this.decisions = decisions;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        seed();
    }

    /**
     * Seeds whatever of the protected rows is missing and leaves what is there: the startup seed, and the re-seed of
     * the nightly and manual reset (Document 5, Availability, Nightly reset).
     *
     * @return whether anything was missing and so seeded
     */
    public boolean seed() {
        boolean seeded = false;
        for (DemoFixture fixture : DemoFixture.seeded()) {
            seeded |= seed(fixture);
        }
        return seeded;
    }

    /** One policy: its rule set, found by its domain, and the policy before it, found by its title; then its cases. */
    private boolean seed(DemoFixture fixture) {
        boolean seeded = false;
        if (rulesets.protectedRulesets().stream().noneMatch(ruleset -> ruleset.domain().equals(fixture.domain()))) {
            PolicyView policy = policies.protectedPolicies().stream()
                    .filter(candidate -> candidate.title().equals(fixture.title())).findFirst()
                    .orElseGet(() -> seedPolicy(fixture));
            seedRuleSet(fixture, policy);
            seeded = true;
        }
        if (fixture.casesJson() != null) {
            int seededCases = decisions.seedProtectedCases(fixture.casesJson());
            if (seededCases > 0) {
                LOG.atInfo().setMessage("demo.seed.cases").addKeyValue("cases", seededCases).log();
                seeded = true;
            }
        }
        return seeded;
    }

    private void seedRuleSet(DemoFixture fixture, PolicyView policy) {
        PolicyVersionRef version = policies.version(policy.id(), 1, null).orElseThrow();
        RulesetView seeded = rulesets.seedProtected(version.id(), fixture.ruleSetJson());
        LOG.atInfo().setMessage("demo.seed.ruleset").addKeyValue("ruleset", seeded.id())
                .addKeyValue("domain", seeded.domain()).log();
    }

    private PolicyView seedPolicy(DemoFixture fixture) {
        PolicyView seeded = policies.createProtected(fixture.title(),
                PolicyLanguage.fromCode(fixture.language()).orElseThrow(), fixture.text());
        LOG.atInfo().setMessage("demo.seed").addKeyValue("policy", seeded.id())
                .addKeyValue("paragraphs", seeded.versions().getFirst().paragraphs().size()).log();
        return seeded;
    }
}
