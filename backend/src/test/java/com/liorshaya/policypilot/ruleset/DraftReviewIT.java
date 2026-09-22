package com.liorshaya.policypilot.ruleset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.audit.service.AuditAction;
import com.liorshaya.policypilot.audit.service.AuditEntry;
import com.liorshaya.policypilot.audit.service.AuditLog;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.ruleset.service.FindingKind;
import com.liorshaya.policypilot.ruleset.service.FindingsUnresolvedException;
import com.liorshaya.policypilot.ruleset.service.GapResolution;
import com.liorshaya.policypilot.ruleset.service.Review;
import com.liorshaya.policypilot.ruleset.service.ReviewFinding;
import com.liorshaya.policypilot.ruleset.service.ReviewStatus;
import com.liorshaya.policypilot.ruleset.service.RulesetProblem;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionStatus;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Requirement;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * A draft's review as it is stored, acknowledged and checked at publishing (Document 2, Flow 1, "The review is part of
 * the draft", and the acknowledge route; Document 3, Publishing gate; Work Plan day 10). The findings are written here
 * with the anchors of fixtures/eval/policies/consumer-lending/seeded.findings.json, so every expected value comes from
 * the documents and the labeled set, not from the model.
 */
@Requirement("FR-5")
class DraftReviewIT extends ApiIntegrationTest {

    @Autowired
    private PolicyService policies;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private AuditLog audit;

    @Autowired
    private MeterRegistry registry;

    private RulesetFixtures fixtures;
    private UUID sandbox;
    private VersionView draft;

    @BeforeEach
    void aDraft() {
        fixtures = new RulesetFixtures(policies, rulesets);
        sandbox = UUID.randomUUID();
        draft = fixtures.draft(sandbox);
    }

    /** SF-1 to SF-5 of the lending policy's seeded findings, in that order, and one injection on paragraph 3. */
    private static Review review() {
        return new Review(ReviewStatus.DONE, "v1", List.of(
                finding("F-1", FindingKind.AMBIGUITY, List.of("R-420"), List.of(4)),
                finding("F-2", FindingKind.CONFLICT, List.of("R-110", "R-115"), List.of(1, 8)),
                finding("F-3", FindingKind.UNSUPPORTED, List.of("R-170"), List.of(4)),
                finding("F-4", FindingKind.GAP, List.of("R-160"), List.of(3)),
                finding("F-5", FindingKind.DUPLICATE, List.of("R-100"), List.of(1)),
                finding("F-6", FindingKind.INJECTION, List.of(), List.of(3))),
                Map.of("4", List.of("R-170", "R-420")));
    }

    private static ReviewFinding finding(String id, FindingKind kind, List<String> rules, List<Integer> paragraphs) {
        return new ReviewFinding(id, kind, rules, paragraphs, "message", "suggestion", 0.8, null);
    }

    private VersionView reviewed(Review review) {
        return rulesets.recordReview(draft.rulesetId(), 1, sandbox, review).orElseThrow();
    }

    private List<RulesetProblem> refusal() {
        try {
            rulesets.publish(draft.rulesetId(), 1, sandbox);
        } catch (FindingsUnresolvedException e) {
            return e.problems();
        }
        throw new AssertionError("the publish was not refused");
    }

    // Document 2, Flow 1: the findings are stored with the draft, "so the analyst sees them again on every visit"
    @Test
    void theReviewIsStoredWithTheDraftAndReadBackWithIt() {
        reviewed(review());

        Review read = rulesets.version(draft.rulesetId(), 1, sandbox).orElseThrow().review();

        assertThat(read).isEqualTo(review());
    }

    // Document 2, Flow 1: publishing is refused "until the review is DONE". Expected: FINDINGS_UNRESOLVED at /review
    @Test
    void aDraftWithoutAReviewIsNotPublished() {
        assertThat(refusal()).containsExactly(new RulesetProblem("/review", "REVIEW_MISSING"));
        assertThat(rulesets.version(draft.rulesetId(), 1, sandbox).orElseThrow().status())
                .isEqualTo(VersionStatus.DRAFT);
    }

    @Test
    void aDraftWhoseReviewFailedIsNotPublished() {
        reviewed(Review.failed("v1"));

        assertThat(refusal()).containsExactly(new RulesetProblem("/review", "FAILED"));
    }

    // Document 2, Flow 1: "An edit through PUT .../rules marks the review STALE"
    @Test
    void anEditMarksTheReviewStaleAndTheDraftIsNotPublishedUntilItIsReviewedAgain() {
        reviewed(new Review(ReviewStatus.DONE, "v1", List.of(), Map.of()));

        VersionView edited = rulesets.replaceRules(draft.rulesetId(), 1, sandbox,
                RulesetFixtures.lendingWithPriority("R-320", 321)).orElseThrow();

        assertThat(edited.review().status()).isEqualTo(ReviewStatus.STALE);
        assertThat(refusal()).containsExactly(new RulesetProblem("/review", "STALE"));
    }

    // Document 2, Flow 1: the blocking findings are the errors, every gap and every injection. Expected: F-2, F-3, F-4
    // and F-6 by kind; the ambiguity F-1 and the duplicate F-5 do not block
    @Test
    void theErrorsTheGapAndTheInjectionBlockPublishingAndTheWarningsDoNot() {
        reviewed(review());

        assertThat(refusal()).containsExactly(
                new RulesetProblem("/review/findings/F-2", "CONFLICT"),
                new RulesetProblem("/review/findings/F-3", "UNSUPPORTED"),
                new RulesetProblem("/review/findings/F-4", "GAP"),
                new RulesetProblem("/review/findings/F-6", "INJECTION"));
    }

    // Document 3, Publishing gate: "Acknowledging a gap finding requires a resolution, not just a tick"
    @Test
    void aGapIsAcknowledgedOnlyWithAResolutionAndWritesAGapAcknowledgedEntry() {
        reviewed(review());

        assertThatThrownBy(() -> rulesets.acknowledge(draft.rulesetId(), 1, sandbox, "F-4", null, "covered"))
                .isInstanceOf(IllegalArgumentException.class);
        VersionView acknowledged = rulesets.acknowledge(draft.rulesetId(), 1, sandbox, "F-4",
                GapResolution.FLAG_ADDED, "R-420 flags every approval").orElseThrow();

        ReviewFinding gap = acknowledged.review().finding("F-4").orElseThrow();
        assertThat(gap.acknowledgement().resolution()).isEqualTo(GapResolution.FLAG_ADDED);
        assertThat(gap.acknowledgement().note()).isEqualTo("R-420 flags every approval");
        assertThat(gap.acknowledgement().at()).isEqualTo(START);
        List<AuditEntry> entries = audit.forVersion(draft.versionId());
        assertThat(entries).extracting(AuditEntry::action).containsExactly(AuditAction.GAP_ACKNOWLEDGED);
        assertThat(entries.getFirst().actor()).isEqualTo(sandbox.toString());
        // Document 3: "the choice and the note are part of the audit entry"
        JsonNode recorded = entries.getFirst().details().path("acknowledgement");
        assertThat(recorded.path("resolution").asString()).isEqualTo("flag_added");
        assertThat(recorded.path("note").asString()).isEqualTo("R-420 flags every approval");
        assertThat(entries.getFirst().details().path("id").asString()).isEqualTo("F-4");
    }

    // Document 2, Review pass output: an error is "explicitly overridden with a note"
    @Test
    void anErrorIsAcknowledgedOnlyWithANote() {
        reviewed(review());

        assertThatThrownBy(() -> rulesets.acknowledge(draft.rulesetId(), 1, sandbox, "F-2", null, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        VersionView acknowledged = rulesets.acknowledge(draft.rulesetId(), 1, sandbox, "F-2", null,
                "R-110 is narrowed to non-retirees").orElseThrow();

        assertThat(acknowledged.review().finding("F-2").orElseThrow().acknowledged()).isTrue();
        assertThat(audit.forVersion(draft.versionId())).isEmpty();
    }

    // Document 2, acknowledge row: "a resolution is given for a kind other than gap" is refused
    @Test
    void onlyAGapTakesAResolution() {
        reviewed(review());

        assertThatThrownBy(() -> rulesets.acknowledge(draft.rulesetId(), 1, sandbox, "F-1",
                GapResolution.RULE_ADDED, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownFindingIsNotThere() {
        reviewed(review());

        assertThat(rulesets.acknowledge(draft.rulesetId(), 1, sandbox, "F-9", null, "note")).isEmpty();
        assertThat(rulesets.acknowledge(draft.rulesetId(), 1, UUID.randomUUID(), "F-1", null, null)).isEmpty();
    }

    // Document 2, Flow 1 and the publish row: once every blocking finding is acknowledged the draft publishes, and
    // the audit entry lists the acknowledged findings and the warnings left open (Document 3)
    @Test
    void onceEveryBlockingFindingIsAcknowledgedTheDraftPublishesWithTheReviewInItsAuditEntry() {
        reviewed(review());
        rulesets.acknowledge(draft.rulesetId(), 1, sandbox, "F-2", null, "R-110 is narrowed to non-retirees");
        rulesets.acknowledge(draft.rulesetId(), 1, sandbox, "F-3", null, "the text says 8,000; fixed");
        rulesets.acknowledge(draft.rulesetId(), 1, sandbox, "F-4", GapResolution.RULE_ADDED, null);
        rulesets.acknowledge(draft.rulesetId(), 1, sandbox, "F-6", null, null);

        VersionView published = rulesets.publish(draft.rulesetId(), 1, sandbox).orElseThrow();

        assertThat(published.status()).isEqualTo(VersionStatus.PUBLISHED);
        JsonNode review = audit.forVersion(draft.versionId()).stream()
                .filter(entry -> entry.action() == AuditAction.PUBLISH).findFirst().orElseThrow()
                .details().path("review");
        assertThat(review.path("status").asString()).isEqualTo("DONE");
        assertThat(review.path("findings")).hasSize(6);
        assertThat(review.path("findings").get(0).has("acknowledgement")).isFalse();
        assertThat(review.path("findings").get(3).path("acknowledgement").path("resolution").asString())
                .isEqualTo("rule_added");
    }

    // Document 2, the review and acknowledge routes: "409 on a version that is not a DRAFT or is protected";
    // Document 5, Authorization (protected demo): the attempt is counted. Expected: refused, the counter plus one
    @Test
    void aProtectedVersionIsNeverReviewedAndTheAttemptIsCounted() {
        UUID seeded = fixtures.seeded().id();
        double before = registry.counter("security.protected.write_attempt", "entity", "ruleset").count();

        assertThatThrownBy(() -> rulesets.recordReview(seeded, 1, sandbox, review()))
                .isInstanceOf(VersionStatusException.class);
        // test classes run in parallel and share the registry, so the assertion is on this test's own increment
        assertThat(registry.counter("security.protected.write_attempt", "entity", "ruleset").count() - before)
                .isEqualTo(1.0);
    }

    @Test
    void aPublishedVersionTakesNoNewReviewOrAcknowledgement() {
        reviewed(new Review(ReviewStatus.DONE, "v1", List.of(), Map.of()));
        rulesets.publish(draft.rulesetId(), 1, sandbox);

        assertThatThrownBy(() -> rulesets.recordReview(draft.rulesetId(), 1, sandbox, review()))
                .isInstanceOf(VersionStatusException.class);
        assertThatThrownBy(() -> rulesets.acknowledge(draft.rulesetId(), 1, sandbox, "F-1", null, null))
                .isInstanceOf(VersionStatusException.class);
    }
}
