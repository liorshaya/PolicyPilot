package com.liorshaya.policypilot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.demo.service.DemoFixture;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Seeded;
import com.liorshaya.policypilot.web.validation.InputNormalizer;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The seeded policies as the image carries them (Work Plan day 4: loading of the lending policy fixture; day 15: the
 * second domain; Document 6, Versioning: the backend copies equal the committed fixtures). Expected values are read
 * from {@code fixtures/}: the lending policy's directory, and the labeled arnona-discount-seniors of the evaluation
 * set.
 */
class DemoFixtureTest {

    private static final String LENDING = "policies/consumer-lending/";

    static Stream<Arguments> seeded() {
        return Stream.of(Arguments.of(DemoFixture.lending(), LENDING, "ruleset.v1.json"),
                Arguments.of(DemoFixture.secondDomain(), Fixtures.SECOND_DOMAIN, "expected.ruleset.json"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("seeded")
    void theTextIsTheCommittedPolicy(DemoFixture fixture, String directory, String ruleSet) throws IOException {
        assertThat(fixture.text()).isEqualTo(Files.readString(Fixtures.path(directory + "policy.he.md")));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("seeded")
    void theDomainTitleAndLanguageAreTheRuleSets(DemoFixture fixture, String directory, String ruleSet) {
        assertThat(fixture.domain()).isEqualTo(Fixtures.json(directory + ruleSet).required("id").stringValue());
        assertThat(fixture.title()).isEqualTo(Fixtures.json(directory + ruleSet).required("name").stringValue());
        assertThat(fixture.language()).isEqualTo("he");
    }

    // Expected: Document 5, Input Validation: what is stored is the normalized string, so the seed, which bypasses the
    // API, must already be what the API's normalization would store
    @ParameterizedTest(name = "{1}")
    @MethodSource("seeded")
    void theTextIsAlreadyNormalized(DemoFixture fixture, String directory, String ruleSet) {
        assertThat(InputNormalizer.normalize(fixture.text())).isEqualTo(fixture.text());
    }

    // Document 6, Versioning: the copy in the backend resources is the committed fixture, byte for byte
    @ParameterizedTest(name = "{1}")
    @MethodSource("seeded")
    void theRuleSetIsTheCommittedOne(DemoFixture fixture, String directory, String ruleSet) {
        assertThat(new RuleSetMapper().readTree(fixture.ruleSetJson())).isEqualTo(Fixtures.json(directory + ruleSet));
    }

    // Document 2, Second domain: data only, the lending policy's 200 cases and none for the second domain
    @Test
    void onlyTheLendingPolicyCarriesCases() throws IOException {
        assertThat(DemoFixture.lending().casesJson())
                .isEqualTo(Files.readString(Fixtures.path(LENDING + "cases-200.json")));
        assertThat(DemoFixture.secondDomain().casesJson()).isNull();
    }

    // The demo's policy is seeded first, so on a new database it is the first protected row too
    @Test
    void theLendingPolicyIsSeededFirst() {
        assertThat(DemoFixture.seeded()).extracting(DemoFixture::domain)
                .containsExactly(Seeded.LENDING, Seeded.SECOND_DOMAIN);
    }
}
