package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.ai.TokenBudget;
import java.time.Instant;

/**
 * What {@code GET /system/budget} returns (Document 2, API Surface): whether the day's token budget is spent, the
 * banner of Document 5, and when the day's count starts again.
 *
 * @param resumesAt the next midnight UTC
 */
public record BudgetResponse(@JsonProperty(required = true) boolean spent,
        @JsonProperty(required = true) Instant resumesAt) {

    public static BudgetResponse of(TokenBudget budget) {
        return new BudgetResponse(budget.stopped(), budget.resumesAt());
    }
}
