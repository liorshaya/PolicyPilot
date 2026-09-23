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
