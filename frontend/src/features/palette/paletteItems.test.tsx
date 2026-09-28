import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { RulesetSummary, VersionResponse } from '../../api/types'
import { everyEntry, proposedEntry, rejectedEntry } from '../../test/fixtures/audit'
import { PROPOSAL_ID } from '../../test/fixtures/change'
import { decisionIdOf, lendingRuleSet, lendingRun } from '../../test/fixtures/lending'
import { seededReview } from '../../test/fixtures/review'
import {
  publishedVersion,
  SEEDED_POLICY_ID,
  SEEDED_RULESET_ID,
  seededPolicy,
} from '../../test/msw/handlers'
import { paletteItems, type PaletteSources } from './paletteItems'

/**
 * What the palette can reach, read from what the screens already read (the owner's answer to phase 6's second
 * question): a case from this session's run of the 200 cases, a rule and a finding from the workspace's version, a
 * paragraph from its policy, a change request from the audit log, a version from the rule set list. Each with its own
 * chip and its state (the spec, section 08); the values are the committed fixtures'.
 */

/** The seeded rule set with a draft written on it, the review of its five planted findings on the draft. */
const ruleset: RulesetSummary = {
  id: SEEDED_RULESET_ID,
  name: lendingRuleSet.name,
  domain: 'consumer-lending',
  protected: true,
  policyId: SEEDED_POLICY_ID,
  versions: [
    { versionNo: 1, status: 'PUBLISHED' },
    { versionNo: 2, status: 'DRAFT' },
  ],
}
const draft: VersionResponse = {
  ...publishedVersion,
  versionNo: 2,
  status: 'DRAFT',
  review: seededReview,
}

const sources: PaletteSources = {
  run: lendingRun,
  ruleset,
  version: draft,
  policy: seededPolicy,
  audit: [rejectedEntry, ...everyEntry],
}

/** An item's state, as the palette's row draws it. */
function stateOf(kind: string, code: string): HTMLElement {
  const found = paletteItems(sources).find((item) => item.kind === kind && item.code === code)
  const { container } = render(<>{found?.state}</>)
  return container
}

describe('paletteItems', () => {
  it("reaches each case of this session's run by its number, with its decision, to open its trace", () => {
    const cases = paletteItems(sources).filter((item) => item.kind === 'case')

    expect(cases).toHaveLength(200)
    expect(cases.find((item) => item.code === 'Case 17')).toMatchObject({
      digits: '17',
      target: { kind: 'case', decisionId: decisionIdOf(17) },
    })
    // cases-expected.json: case 17 is referred by R-330, case 170 declined
    expect(stateOf('case', 'Case 17')).toHaveTextContent('Manual review')
    expect(stateOf('case', 'Case 170')).toHaveTextContent('Declined')
  })

  it("reaches each rule of the workspace's version, its label in the policy's language, to open it in the table", () => {
    const rules = paletteItems(sources).filter((item) => item.kind === 'rule')

    expect(rules.map((item) => item.code)).toStrictEqual(
      lendingRuleSet.rules.map((rule) => rule.id),
    )
    expect(rules.find((item) => item.code === 'R-170')).toMatchObject({
      digits: '170',
      target: { kind: 'rule', rulesetId: SEEDED_RULESET_ID, ruleId: 'R-170' },
    })
    const label = stateOf('rule', 'R-170').firstElementChild
    expect(label).toHaveTextContent('דחייה: הכנסה חודשית נטו נמוכה מ-8,000')
    expect(label).toHaveAttribute('lang', 'he')
    expect(label).toHaveAttribute('dir', 'rtl')
  })

  it("reaches each paragraph of the workspace's policy, to open it in the policy", () => {
    const paragraphs = paletteItems(sources).filter((item) => item.kind === 'paragraph')

    expect(paragraphs.map((item) => item.code)).toStrictEqual([
      '1',
      '2',
      '3',
      '4',
      '5',
      '6',
      '7',
      '8',
      '9',
    ])
    expect(paragraphs[3]).toMatchObject({
      digits: '4',
      target: { kind: 'paragraph', policyId: SEEDED_POLICY_ID, index: 4 },
    })
  })

  it("reaches each finding of the version's review with its severity, to open it in the review", () => {
    const findings = paletteItems(sources).filter((item) => item.kind === 'finding')

    expect(findings.map((item) => item.code)).toStrictEqual(['F-1', 'F-2', 'F-3', 'F-4', 'F-5'])
    expect(findings[0]).toMatchObject({
      digits: '1',
      target: { kind: 'finding', rulesetId: SEEDED_RULESET_ID, findingId: 'F-1' },
    })
    // seeded.findings.json, in order: an ambiguity, a conflict, an unsupported rule, a gap, a duplicate
    expect(findings.map((item) => render(<>{item.state}</>).container.textContent)).toStrictEqual([
      'Ambiguity',
      'Conflict',
      'Unsupported',
      'Gap',
      'Duplicate',
    ])
  })

  it('reaches each change request once, by its number, in the state its latest entry records', () => {
    const changes = paletteItems(sources).filter((item) => item.kind === 'change')

    expect(changes.map((item) => [item.code, item.digits])).toStrictEqual([
      ['CR-0001', '0001'],
      ['CR-0002', '0002'],
    ])
    expect(changes[0]!.target).toStrictEqual({ kind: 'change', changeRequestId: PROPOSAL_ID })
    // the approval and the rejection are a person's, so each carries the seal the audit log draws
    expect(stateOf('change', 'CR-0001').querySelector('.seal')).toHaveTextContent('Approved')
    expect(stateOf('change', 'CR-0002').querySelector('.seal')).toHaveTextContent('Rejected')
    // a request still waiting is the model's proposal, dashed
    const waiting = paletteItems({ audit: [proposedEntry] })[0]
    expect(
      render(<>{waiting?.state}</>).container.querySelector('.vstatus--pending'),
    ).toHaveTextContent('Proposed')
  })

  it("reaches each version of the workspace's rule set with its state, to open it in the table", () => {
    const versions = paletteItems(sources).filter((item) => item.kind === 'version')

    expect(versions.map((item) => [item.code, item.digits])).toStrictEqual([
      ['v1', '1'],
      ['v2', '2'],
    ])
    expect(versions[1]!.target).toStrictEqual({
      kind: 'version',
      rulesetId: SEEDED_RULESET_ID,
      versionNo: 2,
    })
    expect(stateOf('version', 'v1')).toHaveTextContent('Published')
    expect(stateOf('version', 'v2')).toHaveTextContent('Draft')
  })

  it('reaches nothing the screens have not read', () => {
    expect(paletteItems({})).toStrictEqual([])
  })
})
