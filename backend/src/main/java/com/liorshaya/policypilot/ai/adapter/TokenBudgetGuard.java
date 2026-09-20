package com.liorshaya.policypilot.ai.adapter;

import com.liorshaya.policypilot.ai.LlmUnavailableException;
import com.liorshaya.policypilot.ai.TokenUsage;
import com.liorshaya.policypilot.ai.entity.TokenLedgerDayEntity;
import com.liorshaya.policypilot.ai.repository.TokenLedgerRepository;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The daily spend cap (Document 5, Spend caps; Document 2, {@code ai.adapter.TokenBudgetGuard}). Every call asks
 * the guard before it goes to the provider and tells the guard what it spent afterwards; once the day's tokens
 * reach the budget the guard refuses, and only the cache and everything that needs no model keep working.
 *
 * <p>The ledger is written in its own transaction, so tokens already spent are counted even when the request
 * that spent them fails afterwards.
 */
@Component
public class TokenBudgetGuard {

    private final TokenLedgerRepository ledger;
    private final PolicyPilotProperties properties;
    private final Clock clock;

    public TokenBudgetGuard(TokenLedgerRepository ledger, PolicyPilotProperties properties, Clock clock) {
        this.ledger = ledger;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Refuses the call when today's budget is spent.
     *
     * @throws LlmUnavailableException with {@link LlmUnavailableException.Reason#BUDGET_EXHAUSTED}
     */
    @Transactional(readOnly = true)
    public void requireBudget() {
        Long budget = properties.ai().dailyTokenBudget();
        if (budget == null) {
            return;
        }
        long used = ledger.findById(today()).map(TokenLedgerDayEntity::tokensUsed).orElse(0L);
        if (used >= budget) {
            throw new LlmUnavailableException(
                    LlmUnavailableException.Reason.BUDGET_EXHAUSTED,
                    "today's token budget of " + budget + " is spent");
        }
    }

    /** Counts what a call cost against today's budget; a cache hit costs nothing and is not counted. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(TokenUsage usage) {
        Long budget = properties.ai().dailyTokenBudget();
        if (usage.total() == 0) {
            return;
        }
        LocalDate today = today();
        TokenLedgerDayEntity day = ledger.findForUpdate(today)
                .orElseGet(() -> ledger.save(new TokenLedgerDayEntity(today, 0L, false)));
        day.add(usage.total(), budget == null ? Long.MAX_VALUE : budget);
        ledger.save(day);
    }

    /** What today has spent so far, for the cost view and the tests. */
    @Transactional(readOnly = true)
    public long spentToday() {
        return ledger.findById(today()).map(TokenLedgerDayEntity::tokensUsed).orElse(0L);
    }

    /** Whether the hard stop has been reached today, which the UI shows as a banner. */
    @Transactional(readOnly = true)
    public boolean stopped() {
        return ledger.findById(today()).map(TokenLedgerDayEntity::hardStop).orElse(false);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
