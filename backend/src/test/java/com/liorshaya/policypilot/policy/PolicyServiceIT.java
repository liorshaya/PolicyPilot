package com.liorshaya.policypilot.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyTextException;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The policy use case through the service layer against PostgreSQL (Document 6, Definition of Done: an integration
 * test through the service layer; Work Plan day 4 done-when: the Hebrew policy paste returns its 9 paragraphs).
 */
@Requirement({"FR-1", "NFR-5"})
class PolicyServiceIT extends ApiIntegrationTest {

    private static final UUID SANDBOX = UUID.fromString("5b0e7f6a-0f3e-4c2a-9a55-1f0d3c2b1a01");

    @Autowired
    private PolicyService policies;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void hebrewLendingPasteStoresNineParagraphs() throws IOException {
        PolicyView policy = policies.create(SANDBOX, "Lending", PolicyLanguage.HE, lendingText());

        Integer stored = jdbc.sql("""
                        select count(*) from policy_paragraph p join policy_version v on v.id = p.policy_version_id
                        where v.document_id = :id""")
                .param("id", policy.id()).query(Integer.class).single();
        assertThat(stored).isEqualTo(9);
        assertThat(policies.find(policy.id(), SANDBOX).orElseThrow().versions().getFirst().paragraphs()).hasSize(9);
    }

    @Test
    void firstVersionIsNumberOne() throws IOException {
        PolicyView policy = policies.create(SANDBOX, "Lending", PolicyLanguage.HE, lendingText());

        assertThat(policy.versions()).extracting(PolicyView.Version::versionNo).containsExactly(1);
    }

    @Test
    void policyCarriesTheCallersSandboxAndIsNotProtected() throws IOException {
        PolicyView policy = policies.create(SANDBOX, "Lending", PolicyLanguage.HE, lendingText());

        UUID sandbox = jdbc.sql("select sandbox_id from policy_document where id = :id")
                .param("id", policy.id()).query(UUID.class).single();
        assertThat(sandbox).isEqualTo(SANDBOX);
        assertThat(policy.isProtected()).isFalse();
    }

    @Test
    void textOverTheLimitsIsNotStored() {
        UUID sandbox = UUID.randomUUID();

        assertThatThrownBy(() -> policies.create(sandbox, "Too long", PolicyLanguage.EN, "a".repeat(4_001)))
                .isInstanceOf(PolicyTextException.class);
        assertThat(jdbc.sql("select count(*) from policy_document where sandbox_id = :s")
                .param("s", sandbox).query(Integer.class).single()).isZero();
    }

    // Expected: Document 5, Input Validation: what is stored, searched, shown and sent to the model is the same
    // normalized string; RT-09: bidi overrides and zero-width characters are stripped before storage
    @Test
    void storedTextIsTheNormalizedText() {
        String session = api().login("198.51.100.110");
        String body = JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
                "title", "Bidi", "language", "en", "text", "reject\u202Eevorppa\u202C\u200B\r\n\r\nsecond"));

        String created = api().post("/api/v1/policies").web().cookie(session).json(body).send().body();

        String raw = jdbc.sql("select raw_text from policy_version where document_id = :id")
                .param("id", UUID.fromString(JsonPath.read(created, "$.id"))).query(String.class).single();
        assertThat(raw).isEqualTo("rejectevorppa\n\nsecond");
    }

    private static String lendingText() throws IOException {
        return Files.readString(Fixtures.path("policies/consumer-lending/policy.he.md"));
    }
}
