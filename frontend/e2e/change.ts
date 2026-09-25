import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { Page } from '@playwright/test'
import {
  eventStream,
  RT_04_PLANTED,
  rt04Failure,
  scriptedProposal,
  type CasesExpectedFixture,
  type ChangeRequestFixture,
} from '../src/test/fixtures/changeRequest'
import { POLICY_ID, RULESET_ID, ruleSet, seededRuleset } from './seeded'

/**
 * Demo step 4 as the tests serve it: the change stream, the approval, and what the approval leaves behind, the
 * sandbox's own copy of the seeded rule set with version 2 and its audit entry (Document 3, Version lineage). Every
 * payload is assembled from the committed fixtures by the same builder the component tests use
 * (src/test/fixtures/changeRequest.ts): change-request-1.json and the `regression` block of cases-expected.json.
 */

const fixture = (path: string): unknown =>
  JSON.parse(
    readFileSync(fileURLToPath(new URL(`../../fixtures/${path}`, import.meta.url)), 'utf8'),
  )

export const changeRequest = fixture(
  'policies/consumer-lending/change-request-1.json',
) as ChangeRequestFixture
export const casesExpected = fixture(
  'policies/consumer-lending/cases-expected.json',
) as CasesExpectedFixture

/** Document 5, RT-04: the planted text after the threshold request. */
export const RT_04_TEXT = `${changeRequest.text.he}. ${RT_04_PLANTED}`

const BASE_VERSION_ID = '0f4c1c9e-0000-4000-8000-0000000000c1'
const PROPOSAL_ID = '0f4c1c9e-0000-4000-8000-0000000000f1'
const COPY_ID = '0f4c1c9e-0000-4000-8000-0000000000b3'
const COPY_VERSION_IDS = {
  1: '0f4c1c9e-0000-4000-8000-0000000000c4',
  2: '0f4c1c9e-0000-4000-8000-0000000000c3',
}
const SANDBOX = '5f0c1c9e-0000-4000-8000-00000000a0a0'

const proposal = scriptedProposal(ruleSet, changeRequest, casesExpected, {
  proposalId: PROPOSAL_ID,
  baseVersionId: BASE_VERSION_ID,
  decisionOf: (caseNo) => `0f4c1c9e-0000-4000-8000-${String(caseNo).padStart(12, '0')}`,
})

const stage = (name: string): [string, unknown] => [name, { rules: ruleSet.rules.length }]

/** The scripted request: the four stages, then the proposal. */
const proposed: [string, unknown][] = [
  stage('analyzing'),
  [
    'proposing',
    {
      candidates: changeRequest.expected.candidates,
      fields: changeRequest.expected.candidateFields,
    },
  ],
  stage('validating'),
  stage('regression'),
  ['proposal', proposal],
]

/** RT-04: the validator refuses before any regression, and nothing is stored. */
const refused: [string, unknown][] = [
  ...proposed.slice(0, 3),
  ['error', rt04Failure(ruleSet, changeRequest)],
]

/** The sandbox's own copy of the seeded rule set as GET /rulesets lists it after the approval (Document 3). */
export const sandboxCopy = {
  ...seededRuleset,
  id: COPY_ID,
  protected: false,
  forkedFromId: RULESET_ID,
  policyId: POLICY_ID,
  versions: [
    { versionNo: 1, status: 'PUBLISHED' },
    { versionNo: 2, status: 'PUBLISHED' },
  ],
}

/** The copy's versions: version 1 the seeded one as published, version 2 with the approved patches. */
function copyVersion(versionNo: 1 | 2) {
  const patched = new Map(
    proposal.patches.flatMap((patch) => ('rule' in patch ? [[patch.ruleId, patch.rule]] : [])),
  )
  return {
    rulesetId: COPY_ID,
    name: seededRuleset.name,
    domain: seededRuleset.domain,
    protected: false,
    forkedFromId: RULESET_ID,
    versionId: COPY_VERSION_IDS[versionNo],
    versionNo,
    status: 'PUBLISHED',
    policyVersionId: '0f4c1c9e-0000-4000-8000-0000000000d1',
    publishedAt: '2026-09-27T09:12:00Z',
    publishedBy: SANDBOX,
    ruleSet:
      versionNo === 1
        ? ruleSet
        : { ...ruleSet, rules: ruleSet.rules.map((rule) => patched.get(rule.id) ?? rule) },
    findings: [],
  }
}

/**
 * Serves the change routes. The request text decides the answer, so a test that types anything else is told so. After
 * the approval the sandbox lists its copy, whose version 2 holds the CHANGE_APPROVED entry with the note that was sent.
 */
export async function serveTheChange(page: Page): Promise<void> {
  let note: string | null = null
  await page.route('**/api/v1/rulesets/*/versions/*/changes', (route) => {
    const { text } = route.request().postDataJSON() as { text: string }
    const events = text === changeRequest.text.he ? proposed : text === RT_04_TEXT ? refused : null
    return events === null
      ? route.fulfill({ status: 500, body: `no scripted answer for: ${text}` })
      : route.fulfill({
          status: 200,
          headers: { 'Content-Type': 'text/event-stream' },
          body: eventStream(events),
        })
  })
  await page.route(`**/api/v1/changes/${PROPOSAL_ID}/approve`, (route) => {
    note = (route.request().postDataJSON() as { note?: string }).note ?? null
    return route.fulfill({
      json: {
        id: PROPOSAL_ID,
        status: 'APPROVED',
        decidedAt: '2026-09-27T09:12:00Z',
        result: { rulesetId: COPY_ID, versionNo: 2, versionId: COPY_VERSION_IDS[2] },
      },
    })
  })
  // registered after the seeded routes, so these answer first (Playwright tries the last route that matches)
  await page.route('**/api/v1/rulesets', (route) =>
    route.fulfill({
      json: { rulesets: note === null ? [seededRuleset] : [seededRuleset, sandboxCopy] },
    }),
  )
  await page.route(`**/api/v1/rulesets/${COPY_ID}/versions/*`, (route) =>
    route.fulfill({ json: copyVersion(route.request().url().endsWith('/1') ? 1 : 2) }),
  )
  await page.route('**/api/v1/rulesets/*/versions/*/diff/*', (route) =>
    route.fulfill({ json: proposal.diff }),
  )
  await page.route(
    (url) => url.pathname === '/api/v1/audit',
    (route) => {
      const versionId = new URL(route.request().url()).searchParams.get('versionId')
      const entries =
        versionId === COPY_VERSION_IDS[2]
          ? [
              {
                id: '0f4c1c9e-0000-4000-8000-00000000e101',
                at: '2026-09-27T09:12:00Z',
                actor: SANDBOX,
                action: 'CHANGE_APPROVED',
                rulesetVersionId: COPY_VERSION_IDS[2],
                changeRequestId: PROPOSAL_ID,
                details: {
                  rulesetId: COPY_ID,
                  versionNo: 2,
                  rules: ruleSet.rules.length,
                  warnings: [],
                  baseVersionId: BASE_VERSION_ID,
                  requestText: changeRequest.text.he,
                  note,
                  diff: proposal.diff,
                  regression: proposal.regression,
                },
              },
            ]
          : []
      return route.fulfill({ json: { entries } })
    },
  )
}
