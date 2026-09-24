package com.liorshaya.policypilot.support;

import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.RulesetView;

/**
 * The seeded lending rows, found by what the fixture says they are. Two protected policies are seeded since day 15
 * (the lending policy and the second domain), both at the test clock's instant, so "the first protected one" would
 * be either: a test that means the lending demo names it by its domain or its title.
 */
public final class Seeded {

    /** The lending rule set's domain, the DSL document's id in {@code ruleset.v1.json}. */
    public static final String LENDING = Fixtures.lendingV1().required("id").stringValue();
    /** The JSON path of the seeded lending rule set's id in a {@code GET /rulesets} body. */
    public static final String LENDING_RULESET_ID = "$.rulesets[?(@.protected == true && @.domain == '" + LENDING
            + "')].id";

    /** The second domain's (Document 2, Second domain): the labeled arnona-discount-seniors of the evaluation set. */
    public static final String SECOND_DOMAIN =
            Fixtures.json(Fixtures.SECOND_DOMAIN + "expected.ruleset.json").required("id").stringValue();

    private static final String LENDING_TITLE = Fixtures.lendingV1().required("name").stringValue();

    private Seeded() {}

    /** The protected lending rule set. */
    public static RulesetView lendingRuleset(RulesetService rulesets) {
        return rulesets.protectedRulesets().stream().filter(ruleset -> ruleset.domain().equals(LENDING)).findFirst()
                .orElseThrow(() -> new IllegalStateException("the lending rule set is not seeded"));
    }

    /** The protected lending policy. */
    public static PolicyView lendingPolicy(PolicyService policies) {
        return policies.protectedPolicies().stream().filter(policy -> policy.title().equals(LENDING_TITLE))
                .findFirst().orElseThrow(() -> new IllegalStateException("the lending policy is not seeded"));
    }
}
