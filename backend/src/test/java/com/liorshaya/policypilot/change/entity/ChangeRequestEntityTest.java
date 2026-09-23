package com.liorshaya.policypilot.change.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.support.Requirement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A change request's own states (Document 2, change_request: PROPOSED, then APPROVED or REJECTED, stamped with the
 * time of the decision). The row itself refuses a second decision, whatever its caller checked first.
 */
@Requirement("FR-19")
class ChangeRequestEntityTest {

    private static final Instant PROPOSED_AT = Instant.parse("2026-09-24T09:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-09-24T09:05:00Z");

    // Expected: APPROVED, the published version as its result, and the time of the decision
    @Test
    void anApprovalRecordsItsResultAndItsTime() {
        ChangeRequestEntity request = proposed();
        UUID version = UUID.randomUUID();

        request.approve(version, DECIDED_AT);

        assertThat(request.getStatus()).isEqualTo("APPROVED");
        assertThat(request.getResultVersionId()).isEqualTo(version);
        assertThat(request.getDecidedAt()).isEqualTo(DECIDED_AT);
        assertThat(request.isProposed()).isFalse();
    }

    // Expected: REJECTED, no result, and the time of the decision
    @Test
    void aRejectionRecordsItsTimeAndNoResult() {
        ChangeRequestEntity request = proposed();

        request.reject(DECIDED_AT);

        assertThat(request.getStatus()).isEqualTo("REJECTED");
        assertThat(request.getResultVersionId()).isNull();
        assertThat(request.getDecidedAt()).isEqualTo(DECIDED_AT);
    }

    // Document 2: a proposal is decided once. Expected: a second approval and a rejection after an approval refused
    @Test
    void aDecidedRequestIsNotDecidedAgain() {
        ChangeRequestEntity approved = proposed();
        approved.approve(UUID.randomUUID(), DECIDED_AT);
        ChangeRequestEntity rejected = proposed();
        rejected.reject(DECIDED_AT);

        assertThatThrownBy(() -> approved.approve(UUID.randomUUID(), DECIDED_AT))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APPROVED");
        assertThatThrownBy(() -> rejected.approve(UUID.randomUUID(), DECIDED_AT))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("REJECTED");
        assertThatThrownBy(() -> approved.reject(DECIDED_AT)).isInstanceOf(IllegalStateException.class);
    }

    private static ChangeRequestEntity proposed() {
        return new ChangeRequestEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "העלה את הסף",
                "[]", "{}", "{}", PROPOSED_AT, "sandbox");
    }
}
