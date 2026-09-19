package com.liorshaya.policypilot.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.policy.entity.PolicyDocumentEntity;
import com.liorshaya.policypilot.policy.service.PolicyDocuments;
import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyTextException;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Building a policy document before it is stored (Brief FR-1; Document 2, Data Model: a document, its version 1 and
 * the paragraphs numbered from 1). Expected paragraphs come from Document 3's table of the demo policy.
 */
@Requirement("FR-1")
class PolicyDocumentsTest {

    private static final Instant NOW = Instant.parse("2026-09-24T09:00:00Z");
    private static final UUID SANDBOX = UUID.fromString("5b0e7f6a-0f3e-4c2a-9a55-1f0d3c2b1a02");

    private final PolicyDocuments documents = new PolicyDocuments(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void theLendingPolicyBecomesVersionOneWithNineNumberedParagraphs() throws IOException {
        PolicyView view = PolicyDocuments.view(documents.build(SANDBOX, false, "Lending", PolicyLanguage.HE, lending()));

        assertThat(view.versions()).singleElement().satisfies(version -> {
            assertThat(version.versionNo()).isEqualTo(1);
            assertThat(version.paragraphs()).extracting(PolicyView.Paragraph::index)
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
            assertThat(version.paragraphs().get(4).text())
                    .isEqualTo("ההחזר החודשי של ההלוואה יחושב לפי לוח שפיצר בריבית שנתית של 9%.");
        });
    }

    @Test
    void theViewCarriesTheDocumentsFields() {
        PolicyDocumentEntity document = documents.build(SANDBOX, false, "Lending", PolicyLanguage.EN, "one");

        PolicyView view = PolicyDocuments.view(document);

        assertThat(view.id()).isEqualTo(document.getId());
        assertThat(view.title()).isEqualTo("Lending");
        assertThat(view.language()).isEqualTo(PolicyLanguage.EN);
        assertThat(view.isProtected()).isFalse();
        assertThat(view.createdAt()).isEqualTo(NOW);
        assertThat(view.versions().getFirst().createdAt()).isEqualTo(NOW);
    }

    @Test
    void theVersionKeepsTheWholeText() {
        PolicyDocumentEntity document = documents.build(SANDBOX, false, "Two", PolicyLanguage.EN, "one\n\ntwo");

        assertThat(document.getVersions().getFirst().getRawText()).isEqualTo("one\n\ntwo");
        assertThat(document.getSandboxId()).isEqualTo(SANDBOX);
    }

    @Test
    void aProtectedDocumentBelongsToNoSandbox() {
        PolicyDocumentEntity document = documents.build(null, true, "Seed", PolicyLanguage.HE, "one");

        assertThat(document.isProtectedRow()).isTrue();
        assertThat(document.getSandboxId()).isNull();
    }

    @Test
    void aCopyKeepsTheTextsAndParagraphIndexesUnderNewIds() throws IOException {
        PolicyDocumentEntity original = documents.build(null, true, "Seed", PolicyLanguage.HE, lending());
        UUID sandbox = UUID.fromString("5b0e7f6a-0f3e-4c2a-9a55-1f0d3c2b1a04");

        PolicyDocumentEntity copy = documents.copy(original, sandbox);

        PolicyView copied = PolicyDocuments.view(copy);
        PolicyView source = PolicyDocuments.view(original);
        assertThat(copied.id()).isNotEqualTo(source.id());
        assertThat(copied.versions()).isEqualTo(source.versions());
        assertThat(copy.getVersions().getFirst().getRawText()).isEqualTo(original.getVersions().getFirst().getRawText());
        assertThat(copied.title()).isEqualTo("Seed");
        assertThat(copied.language()).isEqualTo(PolicyLanguage.HE);
    }

    @Test
    void aCopyBelongsToTheSandboxAndNamesItsOrigin() {
        PolicyDocumentEntity original = documents.build(null, true, "Seed", PolicyLanguage.HE, "one");
        UUID sandbox = UUID.fromString("5b0e7f6a-0f3e-4c2a-9a55-1f0d3c2b1a04");

        PolicyDocumentEntity copy = documents.copy(original, sandbox);

        assertThat(copy.getSandboxId()).isEqualTo(sandbox);
        assertThat(copy.isProtectedRow()).isFalse();
        assertThat(copy.getForkedFromId()).isEqualTo(original.getId());
        assertThat(PolicyDocuments.view(copy).forkedFromId()).isEqualTo(original.getId());
    }

    @Test
    void textOverTheLimitsIsRefusedBeforeAnythingIsBuilt() {
        assertThatThrownBy(() -> documents.build(SANDBOX, false, "Empty", PolicyLanguage.EN, "   "))
                .isInstanceOf(PolicyTextException.class);
    }

    @Test
    void languagesAreReadBackFromTheirCodes() {
        assertThat(PolicyLanguage.fromCode("he")).contains(PolicyLanguage.HE);
        assertThat(PolicyLanguage.fromCode("EN")).isEmpty();
        assertThat(PolicyLanguage.fromCode(null)).isEmpty();
    }

    private static String lending() throws IOException {
        return Files.readString(Fixtures.path("policies/consumer-lending/policy.he.md"));
    }
}
