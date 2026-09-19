package com.liorshaya.policypilot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.demo.service.LendingFixture;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.web.validation.InputNormalizer;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * The demo policy as the image carries it (Work Plan day 4: loading of the lending policy fixture; Document 6,
 * Versioning: the backend copies equal the committed fixtures). Expected values are read from {@code fixtures/}.
 */
class LendingFixtureTest {

    private final LendingFixture fixture = LendingFixture.load();

    @Test
    void theTextIsTheCommittedPolicy() throws IOException {
        assertThat(fixture.text())
                .isEqualTo(Files.readString(Fixtures.path("policies/consumer-lending/policy.he.md")));
    }

    @Test
    void theTitleAndLanguageAreTheRuleSets() {
        assertThat(fixture.title())
                .isEqualTo(Fixtures.json("policies/consumer-lending/ruleset.v1.json").get("name").stringValue());
        assertThat(fixture.language()).isEqualTo("he");
    }

    // Expected: Document 5, Input Validation: what is stored is the normalized string, so the seed, which bypasses the
    // API, must already be what the API's normalization would store
    @Test
    void theTextIsAlreadyNormalized() {
        assertThat(InputNormalizer.normalize(fixture.text())).isEqualTo(fixture.text());
    }

    // Document 6, Versioning: the copy in the backend resources is the committed fixture, byte for byte
    @Test
    void theRuleSetIsTheCommittedVersionOne() {
        assertThat(new RuleSetMapper().readTree(LendingFixture.load().ruleSetJson()))
                .isEqualTo(Fixtures.lendingV1());
    }

}
