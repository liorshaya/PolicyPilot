import type { BudgetResponse } from '../../api/types'

/**
 * What GET /system/budget answers (Document 2, API Surface): the day's token budget as the ledger has it, resuming at
 * the next midnight UTC. The day not spent is Document 2's own example; the spent one is the same day stopped.
 */

export const budgetOpen: BudgetResponse = { spent: false, resumesAt: '2026-09-29T00:00:00Z' }

export const budgetSpent: BudgetResponse = { spent: true, resumesAt: '2026-09-29T00:00:00Z' }
