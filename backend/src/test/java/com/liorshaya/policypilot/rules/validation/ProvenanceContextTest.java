package com.liorshaya.policypilot.rules.validation;

import static com.liorshaya.policypilot.rules.validation.Validations.invalidFixture;
import static com.liorshaya.policypilot.rules.validation.Validations.lending;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import com.liorshaya.policypilot.support.RuleSetBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/**
 * The four validation contexts (Document 3, Provenance), named after the reference self-test's "provenance
 * contexts": the scripted change raises R-170's threshold to 9,000 with pending provenance (cr-0042).
 */
@Requirement({"FR-4", "FR-19"})
class ProvenanceContextTest {

    private static final String R170_AT = "/rules/11/provenance";

    @Test
    void changeProposalWithPendingOnThePatchedRuleIsAccepted() {
        assertThat(lending(proposal(), ValidationContext.CHANGE_PROPOSAL, "R-170")).isEmpty();
    }

    @Test
    void sameProposalAtPublishIsRejectedWithPendingAtPublish() {
        assertThat(lending(proposal(), ValidationContext.PUBLISH)).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.PROVENANCE_PENDING_AT_PUBLISH, R170_AT));
    }

    @Test
    void modelPatchClaimingAnalystIsRejectedInAProposal() {
        ObjectNode claimed = RuleSetBuilder.from(proposal())
                .rule("R-170", r -> r.putObject("provenance").put("kind", "analyst")
                        .put("actor", "demo-analyst").put("note", "claimed by the model"))
                .build();

        assertThat(lending(claimed, ValidationContext.CHANGE_PROPOSAL, "R-170"))
                .extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.PROVENANCE_ANALYST_FROM_MODEL, R170_AT));
    }

    @Test
    void untouchedAnalystRulesAreAllowedInAProposal() {
        // R-310 and R-410 carry analyst provenance and are not among the rules the model patched
        assertThat(lending(proposal(), ValidationContext.CHANGE_PROPOSAL, "R-170"))
                .noneMatch(finding -> finding.code() == ValidationCode.PROVENANCE_ANALYST_FROM_MODEL);
        assertThat(lending(proposal(), ValidationContext.CHANGE_PROPOSAL, "R-170", "R-310"))
                .extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.PROVENANCE_ANALYST_FROM_MODEL, "/rules/14/provenance"));
    }

    @Test
    void authoringDraftMayNotContainAnalystRules() {
        assertThat(lending(Fixtures.lendingV1(), ValidationContext.AUTHORING)).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.PROVENANCE_ANALYST_FROM_MODEL, "/rules/14/provenance"),
                        tuple(ValidationCode.PROVENANCE_ANALYST_FROM_MODEL, "/rules/17/provenance"));
        assertThat(invalidFixture("PROVENANCE_ANALYST_FROM_MODEL"))
                .allMatch(finding -> finding.code() == ValidationCode.PROVENANCE_ANALYST_FROM_MODEL)
                .extracting(Finding::ruleIds).containsExactly(List.of("R-310"), List.of("R-410"));
    }

    @Test
    void authoringDraftWithPendingIsRejectedAsPendingFromModel() {
        assertThat(invalidFixture("PROVENANCE_PENDING_FROM_MODEL")).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.PROVENANCE_PENDING_FROM_MODEL, R170_AT));
    }

    @Test
    void approvedProposalRewrittenToAnalystPublishesClean() {
        ObjectNode approved = RuleSetBuilder.from(proposal())
                .rule("R-170", r -> r.putObject("provenance").put("kind", "analyst").put("actor", "demo-analyst")
                        .put("note", "Change request cr-0042: raise minimum income to 9,000. threshold raised per request")
                        .put("changeRequestId", "cr-0042"))
                .build();

        assertThat(lending(approved, ValidationContext.PUBLISH)).isEmpty();
    }

    @Test
    void analystEditAcceptsAnalystProvenance() {
        assertThat(lending(Fixtures.lendingV1(), ValidationContext.ANALYST_EDIT)).isEmpty();
    }

    @Test
    void pendingInAnAnalystEditIsRejected() {
        assertThat(lending(proposal(), ValidationContext.ANALYST_EDIT)).extracting(Finding::code, Finding::path)
                .containsExactly(tuple(ValidationCode.PROVENANCE_PENDING_FROM_MODEL, R170_AT));
    }

    /** The reference self-test's proposal: R-170 raised to 9,000 with pending provenance. */
    private static ObjectNode proposal() {
        return RuleSetBuilder.lendingV1()
                .rule("R-170", r -> {
                    ((ObjectNode) r.get("condition")).put("value", 9000);
                    r.putObject("provenance").put("kind", "pending").put("changeRequestId", "cr-0042")
                            .put("rationale", "threshold raised per request");
                })
                .build();
    }
}
