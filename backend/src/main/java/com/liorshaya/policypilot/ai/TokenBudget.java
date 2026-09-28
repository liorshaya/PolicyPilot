package com.liorshaya.policypilot.ai;

import java.time.Instant;

/**
 * The day's token budget as the web app's banner reads it (Document 2, {@code GET /system/budget}; Document 5, Spend
 * caps and Degradation order). The ledger counts a day in UTC, so a spent budget resumes at the next midnight UTC.
 */
public interface TokenBudget {

    /** Whether today's hard stop has been reached: every model call is refused until {@link #resumesAt()}. */
    boolean stopped();

    /** When today's count ends and the next day's starts: the next midnight UTC. */
    Instant resumesAt();
}
