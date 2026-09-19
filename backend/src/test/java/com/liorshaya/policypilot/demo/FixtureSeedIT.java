package com.liorshaya.policypilot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.audit.service.AuditAction;
import com.liorshaya.policypilot.audit.service.AuditEntry;
import com.liorshaya.policypilot.audit.service.AuditLog;
import com.liorshaya.policypilot.demo.service.FixtureLoader;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.RulesetView;
import com.liorshaya.policypilot.ruleset.service.VersionStatus;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Loading of the lending fixtures (Document 2, Local: a seed job loads the fixtures on an empty database; Work Plan
 * days 4 and 5): the policy text and the rule set as protected rows. The application under test started on the test
 * database, so the seed already ran once.
 */
@Requirement("FR-1")
class FixtureSeedIT extends ApiIntegrationTest {

    @Autowired
    private PolicyService policies;

    @Autowired
    private FixtureLoader loader;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private AuditLog audit;

    // Expected: Document 3, the demo policy is nine Hebrew paragraphs
    @Test
    void lendingPolicyIsSeededAsProtectedWithNineParagraphs() {
        PolicyView seeded = policies.protectedPolicies().getFirst();

        assertThat(seeded.isProtected()).isTrue();
        assertThat(seeded.versions().getFirst().paragraphs()).hasSize(9);
        assertThat(jdbc.sql("select count(*) from policy_document where id = :id and sandbox_id is null")
                .param("id", seeded.id()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void seededParagraphsAreTheFixturesParagraphs() {
        List<String> paragraphs = policies.protectedPolicies().getFirst().versions().getFirst().paragraphs().stream()
                .map(PolicyView.Paragraph::text).toList();

        assertThat(paragraphs).isEqualTo(Fixtures.lendingParagraphs());
    }

    @Test
    void seededTitleAndLanguageComeFromTheFixture() {
        PolicyView seeded = policies.protectedPolicies().getFirst();

        assertThat(seeded.title())
                .isEqualTo(Fixtures.json("policies/consumer-lending/ruleset.v1.json").get("name").stringValue());
        assertThat(seeded.language().code()).isEqualTo("he");
    }

    @Test
    void seedIsIdempotentAcrossRestarts() {
        loader.run(new DefaultApplicationArguments());

        assertThat(policies.protectedPolicies()).hasSize(1);
    }

    @Test
    void theProtectedPolicyIsReadableFromEverySandbox() {
        String id = policies.protectedPolicies().getFirst().id().toString();

        for (String session : List.of(api().login(), api().login())) {
            HttpResponse<String> response = api().get("/api/v1/policies/" + id).cookie(session).send();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat((Boolean) JsonPath.read(response.body(), "$.protected")).isTrue();
        }
    }

    // Work Plan day 5: seeding of rule set version 1 as protected. Expected: ruleset.v1.json, status PUBLISHED
    @Test
    void lendingVersionOneIsSeededAsAProtectedPublishedVersion() {
        RulesetView seeded = rulesets.protectedRulesets().getFirst();

        assertThat(seeded.isProtected()).isTrue();
        assertThat(seeded.domain()).isEqualTo(Fixtures.lendingV1().get("id").stringValue());
        assertThat(seeded.versions()).containsExactly(new RulesetView.VersionSummary(1, VersionStatus.PUBLISHED));
        assertThat(jdbc.sql("select count(*) from ruleset where id = :id and sandbox_id is null")
                .param("id", seeded.id()).query(Integer.class).single()).isEqualTo(1);
    }

    // Expected: fixtures/policies/consumer-lending/ruleset.v1.json
    @Test
    void seededRulesEqualTheFixture() {
        VersionView seeded = seededVersion();

        assertThat(seeded.document()).isEqualTo(Fixtures.lendingV1());
        assertThat(seeded.publishedBy()).isEqualTo(RulesetService.DEMO_ACTOR);
    }

    // Document 2, ruleset_version.policy_version_id. Expected: version 1 of the seeded policy
    @Test
    void seededVersionCitesTheSeededPolicysFirstVersion() {
        PolicyView policy = policies.protectedPolicies().getFirst();

        assertThat(seededVersion().policyVersionId())
                .isEqualTo(policies.version(policy.id(), 1, null).orElseThrow().id());
    }

    // Document 5: the actor of seeded data is demo-analyst. Expected: one PUBLISH entry
    @Test
    void seedingWritesOnePublishAuditEntryByDemoAnalyst() {
        List<AuditEntry> entries = audit.forVersion(seededVersion().versionId()).stream()
                .filter(entry -> RulesetService.DEMO_ACTOR.equals(entry.actor()))
                .toList();

        assertThat(entries).singleElement().satisfies(entry ->
                assertThat(entry.action()).isEqualTo(AuditAction.PUBLISH));
    }

    // Document 2, Local: a seed job loads the fixtures on an empty database
    @Test
    void ruleSetAndPolicySeedsAreIdempotent() {
        loader.run(new DefaultApplicationArguments());

        assertThat(policies.protectedPolicies()).hasSize(1);
        assertThat(rulesets.protectedRulesets()).hasSize(1);
    }

    /** Version 1 of the seeded rule set, as any sandbox reads it. */
    private VersionView seededVersion() {
        UUID ruleset = rulesets.protectedRulesets().getFirst().id();
        return rulesets.version(ruleset, 1, UUID.randomUUID()).orElseThrow();
    }

}
