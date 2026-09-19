package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.web.response.PolicyResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON of a policy (Document 2, API Surface: the policy with its versions and paragraphs): field names, the
 * language as its code and {@code protected} as the flag the web app reads.
 */
class PolicyResponseTest {

    private static final Instant NOW = Instant.parse("2026-09-24T09:00:00Z");

    @Test
    void aPolicyIsWrittenWithItsVersionsAndParagraphs() {
        PolicyView view = new PolicyView(UUID.fromString("0f4c1c9e-0000-4000-8000-000000000001"), "Lending",
                PolicyLanguage.HE, true, NOW, List.of(new PolicyView.Version(1, NOW,
                        List.of(new PolicyView.Paragraph(1, "first"), new PolicyView.Paragraph(2, "second")))));

        String json = JsonMapper.builder().build().writeValueAsString(PolicyResponse.of(view));

        assertThat((String) JsonPath.read(json, "$.id")).isEqualTo("0f4c1c9e-0000-4000-8000-000000000001");
        assertThat((String) JsonPath.read(json, "$.language")).isEqualTo("he");
        assertThat((Boolean) JsonPath.read(json, "$.protected")).isTrue();
        assertThat((Integer) JsonPath.read(json, "$.versions[0].versionNo")).isEqualTo(1);
        assertThat((List<String>) JsonPath.read(json, "$.versions[0].paragraphs[*].text")).containsExactly("first", "second");
        assertThat((List<Integer>) JsonPath.read(json, "$.versions[0].paragraphs[*].index")).containsExactly(1, 2);
    }
}
