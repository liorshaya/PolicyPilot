package com.liorshaya.policypilot.ruleset;

import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.RulesetView;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.Fixtures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the rule set tests build on: the seeded protected rule set, and a sandbox's own DRAFT of the lending document
 * on a policy of its own. Every value comes from the committed fixtures, never from the code under test.
 */
final class RulesetFixtures {

    private final PolicyService policies;
    private final RulesetService rulesets;

    RulesetFixtures(PolicyService policies, RulesetService rulesets) {
        this.policies = policies;
        this.rulesets = rulesets;
    }

    /** The seeded demo rule set, which no sandbox may write to. */
    RulesetView seeded() {
        return rulesets.protectedRulesets().getFirst();
    }

    /** Version 1 of the seeded rule set, as any sandbox reads it. */
    VersionView seededVersion(UUID sandboxId) {
        return rulesets.version(seeded().id(), 1, sandboxId).orElseThrow();
    }

    /** A DRAFT of the lending document in {@code sandboxId}, on that sandbox's own copy of the policy text. */
    VersionView draft(UUID sandboxId) {
        return draft(sandboxId, Fixtures.lendingV1(), ValidationContext.ANALYST_EDIT, Set.of());
    }

    /** A DRAFT of {@code document}, validated in {@code context} (a change proposal may carry pending provenance). */
    VersionView draft(UUID sandboxId, ObjectNode document, ValidationContext context, Set<String> modelRuleIds) {
        return rulesets.createDraft(sandboxId, policyVersion(sandboxId).id(), document, context, modelRuleIds);
    }

    /** The sandbox's own copy of the lending policy text, created on first use. */
    PolicyVersionRef policyVersion(UUID sandboxId) {
        PolicyView policy = policies.create(sandboxId, "Lending", PolicyLanguage.HE, lendingText());
        return policies.version(policy.id(), 1, sandboxId).orElseThrow();
    }

    /** The committed lending rule set, with one rule's priority changed. */
    static ObjectNode lendingWithPriority(String ruleId, int priority) {
        ObjectNode document = Fixtures.lendingV1();
        document.withArray("rules").valueStream()
                .filter(rule -> ruleId.equals(rule.path("id").asString("")))
                .forEach(rule -> ((ObjectNode) rule).put("priority", priority));
        return document;
    }

    /** The index of a rule in the committed document, which a JSON pointer of a finding names. */
    static int ruleIndex(String ruleId) {
        List<String> ids = Fixtures.lendingV1().withArray("rules").valueStream()
                .map(rule -> rule.path("id").asString(""))
                .toList();
        return ids.indexOf(ruleId);
    }

    static String lendingText() {
        try {
            return Files.readString(Fixtures.path("policies/consumer-lending/policy.he.md"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
