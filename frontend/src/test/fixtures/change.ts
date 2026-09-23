import type { ChangeDecision } from '../../api/types'
import changeRequestFile from '../../../../fixtures/policies/consumer-lending/change-request-1.json'
import casesExpectedFile from '../../../../fixtures/policies/consumer-lending/cases-expected.json'
import {
  rt04Failure,
  scriptedProposal,
  type CasesExpectedFixture,
  type ChangeRequestFixture,
} from './changeRequest'
import { lendingRuleSet } from './lending'

/**
 * The scripted change request for the component tests, built by ./changeRequest from the committed fixtures, which are
 * imported from fixtures/ itself (Document 6, Fixtures and Test Data).
 */
export const scriptedRequest = changeRequestFile as unknown as ChangeRequestFixture
export const casesExpected = casesExpectedFile as unknown as CasesExpectedFixture

export const PROPOSAL_ID = '0f4c1c9e-0000-4000-8000-0000000000f1'
export const BASE_VERSION_ID = '0f4c1c9e-0000-4000-8000-0000000000c1'

/** A decision id per case, readable in a failure message: case 8 is ...000000000008. */
export const decisionOf = (caseNo: number): string =>
  `0f4c1c9e-0000-4000-8000-${String(caseNo).padStart(12, '0')}`

export const scriptedProposalEvent = scriptedProposal(
  lendingRuleSet,
  scriptedRequest,
  casesExpected,
  { proposalId: PROPOSAL_ID, baseVersionId: BASE_VERSION_ID, decisionOf },
)

export const rt04 = rt04Failure(lendingRuleSet, scriptedRequest)

/** The scripted request as the stream answers it: the four stages, the candidates, then the proposal. */
export const scriptedEvents: [string, unknown][] = [
  ['analyzing', { rules: lendingRuleSet.rules.length }],
  [
    'proposing',
    {
      candidates: scriptedRequest.expected.candidates,
      fields: scriptedRequest.expected.candidateFields,
    },
  ],
  ['validating', { rules: lendingRuleSet.rules.length }],
  ['regression', { rules: lendingRuleSet.rules.length }],
  ['proposal', scriptedProposalEvent],
]

/** RT-04 as the stream answers it: the validator refuses before any regression, and nothing is stored. */
export const rt04Events: [string, unknown][] = [...scriptedEvents.slice(0, 3), ['error', rt04]]

/** The version the approval publishes: version 2 of the sandbox's own copy of the seeded rule set (Document 3). */
export const COPY_RULESET_ID = '0f4c1c9e-0000-4000-8000-0000000000b3'
export const COPY_VERSION_ID = '0f4c1c9e-0000-4000-8000-0000000000c3'

export const approvedDecision: ChangeDecision = {
  id: PROPOSAL_ID,
  status: 'APPROVED',
  decidedAt: '2026-09-27T09:12:00Z',
  result: { rulesetId: COPY_RULESET_ID, versionNo: 2, versionId: COPY_VERSION_ID },
}

export const rejectedDecision: ChangeDecision = {
  id: PROPOSAL_ID,
  status: 'REJECTED',
  decidedAt: '2026-09-27T09:13:00Z',
}
