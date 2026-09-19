package com.liorshaya.policypilot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.demo.service.FixtureLoader;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Loading of the lending policy fixture (Document 2, Local: a seed job loads the fixtures on an empty database; Work
 * Plan day 4). The application under test started on the test database, so the seed already ran once.
 */
@Requirement("FR-1")
class FixtureSeedIT extends ApiIntegrationTest {

    @Autowired
    private PolicyService policies;

    @Autowired
    private FixtureLoader loader;

    @Autowired
    private JdbcClient jdbc;

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
}
