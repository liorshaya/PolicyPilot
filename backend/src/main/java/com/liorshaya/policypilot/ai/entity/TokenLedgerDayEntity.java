package com.liorshaya.policypilot.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * A row of {@code token_ledger} (Document 2, Data Model; Document 5, Spend caps): the tokens one day has spent
 * and whether the hard stop was reached. One row per day, updated in place.
 */
@Entity
@Table(name = "token_ledger")
public class TokenLedgerDayEntity {

    @Id
    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "tokens_used", nullable = false)
    private long tokensUsed;

    @Column(name = "hard_stop", nullable = false)
    private boolean hardStop;

    protected TokenLedgerDayEntity() {}

    public TokenLedgerDayEntity(LocalDate day, long tokensUsed, boolean hardStop) {
        this.day = day;
        this.tokensUsed = tokensUsed;
        this.hardStop = hardStop;
    }

    public LocalDate day() {
        return day;
    }

    public long tokensUsed() {
        return tokensUsed;
    }

    public boolean hardStop() {
        return hardStop;
    }

    public void add(long tokens, long budget) {
        tokensUsed += tokens;
        hardStop = tokensUsed >= budget;
    }
}
