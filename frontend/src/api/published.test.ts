import { describe, expect, it } from 'vitest'
import type { RulesetSummary } from './types'
import { publishedTarget } from './published'

/** Expected values: Document 2, decide and chat sessions — only a PUBLISHED version decides or is asked about. */

function ruleset(
  id: string,
  versions: RulesetSummary['versions'],
  isProtected = false,
): RulesetSummary {
  return { id, name: id, domain: id, protected: isProtected, policyId: `${id}-policy`, versions }
}

const seeded = ruleset('seeded', [{ versionNo: 1, status: 'PUBLISHED' }], true)
const draft = ruleset('draft', [{ versionNo: 1, status: 'DRAFT' }])
const published2 = ruleset('own', [
  { versionNo: 1, status: 'PUBLISHED' },
  { versionNo: 2, status: 'DRAFT' },
])

describe('publishedTarget', () => {
  it('works on the newest published version of the rule set asked for', () => {
    expect(publishedTarget([seeded, published2], 'own')).toEqual({
      ruleset: published2,
      versionNo: 1,
      elsewhere: false,
    })
  })

  it('falls back to a rule set that has a published version, and says it is elsewhere', () => {
    expect(publishedTarget([seeded, draft], 'draft')).toEqual({
      ruleset: seeded,
      versionNo: 1,
      elsewhere: true,
    })
  })

  it('takes the seeded rule set when none was asked for', () => {
    expect(publishedTarget([draft, seeded], null)?.ruleset.id).toBe('seeded')
  })

  it('is nothing when no rule set has a published version', () => {
    expect(publishedTarget([draft], 'draft')).toBeNull()
    expect(publishedTarget([], null)).toBeNull()
  })
})
