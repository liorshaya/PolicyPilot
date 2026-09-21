package com.liorshaya.policypilot.rag;

import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.Fixtures;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * What the rag tests build on: a version of the lending rule set published in a sandbox of its own, on a policy whose
 * tenth paragraph the test writes, so that no two tests share a version or a text the fake gateway keys on; and a
 * bounded wait for the asynchronous embedding job, reading the status where the API reads it.
 */
final class RagFixtures {

    private static final Duration PATIENCE = Duration.ofSeconds(15);

    private final PolicyService policies;
    private final RulesetService rulesets;
    private final JdbcClient jdbc;

    RagFixtures(PolicyService policies, RulesetService rulesets, JdbcClient jdbc) {
        this.policies = policies;
        this.rulesets = rulesets;
        this.jdbc = jdbc;
    }

    /** A published version in a fresh sandbox; the sandbox and the rule set are what a request names. */
    Published publish(String tenthParagraph) {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = draft(sandbox, tenthParagraph);
        UUID version = rulesets.publish(draft.rulesetId(), 1, sandbox).orElseThrow().versionId();
        return new Published(sandbox, draft.rulesetId(), version);
    }

    /** Published, and waited for until its chunks are stored. */
    Published ready(String tenthParagraph) {
        Published published = publish(tenthParagraph);
        awaitStatus(published.versionId(), "READY");
        return published;
    }

    VersionView draft(UUID sandbox, String tenthParagraph) {
        PolicyView policy = policies.create(sandbox, "Lending", PolicyLanguage.HE,
                Fixtures.lendingPolicyText().strip() + "\n\n" + tenthParagraph);
        UUID policyVersion = policies.version(policy.id(), 1, sandbox).orElseThrow().id();
        return rulesets.createDraft(sandbox, policyVersion, Fixtures.lendingV1(), ValidationContext.ANALYST_EDIT,
                Set.of());
    }

    @Nullable String status(UUID version) {
        return jdbc.sql("select embedding_status from ruleset_version where id = :id").param("id", version)
                .query(String.class).optional().orElse(null);
    }

    /** Waits for the asynchronous job; fails after {@link #PATIENCE}. */
    void awaitStatus(UUID version, String expected) {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        while (!expected.equals(status(version))) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("version " + version + " is " + status(version) + ", not " + expected);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    /** A version a sandbox published. */
    record Published(UUID sandboxId, UUID rulesetId, UUID versionId) {}
}
