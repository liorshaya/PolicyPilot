import { describe, expect, it } from 'vitest'
import type { ChangeDecision } from '../../api/types'
import {
  PROPOSAL_ID,
  rt04,
  scriptedProposalEvent,
  scriptedRequest,
} from '../../test/fixtures/change'
import { changeReducer, IDLE, type ChangeAction, type ChangeState } from './changeReducer'
import { CHANGE_STAGES } from './types'

// @requirement FR-17
// @requirement FR-18
// @requirement FR-19

/**
 * A change request as the screen holds it (Document 2, the change stream: `analyzing`, `proposing` with the candidate
 * rules and fields, `validating`, `regression`, then `proposal` or `error`; Work Plan day 14: the change reducer). The
 * candidates, the proposal and the RT-04 refusal are built from the committed fixtures (src/test/fixtures/change.ts).
 */

const running = changeReducer(IDLE, { type: 'submitted' })
const candidates = {
  candidates: scriptedRequest.expected.candidates,
  fields: scriptedRequest.expected.candidateFields,
}
const proposing = changeReducer(running, { type: 'proposing', candidates })
const proposed = changeReducer(proposing, { type: 'proposal', proposal: scriptedProposalEvent })

/** The version the approval published, in the sandbox's own copy of the seeded rule set (Document 3). */
const approved: ChangeDecision = {
  id: PROPOSAL_ID,
  status: 'APPROVED',
  decidedAt: '2026-09-27T09:12:00Z',
  result: {
    rulesetId: '0f4c1c9e-0000-4000-8000-0000000000b3',
    versionNo: 2,
    versionId: '0f4c1c9e-0000-4000-8000-0000000000c3',
  },
}

describe('changeReducer', () => {
  it('a submitted request is running with no stage and nothing left of the last proposal', () => {
    expect(changeReducer(proposed, { type: 'submitted' })).toStrictEqual({
      status: 'running',
      stage: null,
      candidates: null,
    })
  })

  it('the four stages arrive in order and the progress follows them', () => {
    const analyzing = changeReducer(running, { type: 'stage', stage: 'analyzing' })
    const afterProposing = changeReducer(analyzing, { type: 'proposing', candidates })
    const validating = changeReducer(afterProposing, { type: 'stage', stage: 'validating' })
    const regression = changeReducer(validating, { type: 'stage', stage: 'regression' })

    expect(CHANGE_STAGES).toStrictEqual(['analyzing', 'proposing', 'validating', 'regression'])
    expect(
      [analyzing, afterProposing, validating, regression].map((state) =>
        state.status === 'running' ? state.stage : state.status,
      ),
    ).toStrictEqual(['analyzing', 'proposing', 'validating', 'regression'])
  })

  it('proposing records the candidate rules and fields the model is shown', () => {
    // the Work Plan's expected five for the scripted request, and the one field it touches
    expect(proposing).toStrictEqual({
      status: 'running',
      stage: 'proposing',
      candidates: {
        candidates: ['R-170', 'R-410', 'R-020', 'R-200', 'R-320'],
        fields: ['monthly_income'],
      },
    })
  })

  it('a proposal ends the run with its patches, its diff and its regression report', () => {
    expect(proposed).toStrictEqual({
      status: 'proposed',
      candidates,
      proposal: scriptedProposalEvent,
    })
  })

  it("an error event ends the run refused, with the findings and the model's answer", () => {
    // RT-04: the stream ends with the refusal and what the model attempted, so the analyst sees it (Document 5)
    expect(changeReducer(proposing, { type: 'failed', failure: rt04 })).toStrictEqual({
      status: 'failed',
      candidates,
      failure: rt04,
    })
  })

  it('a failure before or during the stream ends it with its code and no findings', () => {
    const failure = { code: 'PROVIDER_UNAVAILABLE', findings: [], document: null }

    expect(changeReducer(running, { type: 'failed', failure })).toStrictEqual({
      status: 'failed',
      candidates: null,
      failure,
    })
  })

  it('an approval or a rejection is recorded on the proposal it decides', () => {
    const rejected: ChangeDecision = {
      id: PROPOSAL_ID,
      status: 'REJECTED',
      decidedAt: '2026-09-27T09:13:00Z',
    }

    expect(changeReducer(proposed, { type: 'decided', decision: approved })).toStrictEqual({
      status: 'decided',
      proposal: scriptedProposalEvent,
      decision: approved,
    })
    expect(changeReducer(proposed, { type: 'decided', decision: rejected })).toStrictEqual({
      status: 'decided',
      proposal: scriptedProposalEvent,
      decision: rejected,
    })
    expect(changeReducer(running, { type: 'decided', decision: approved })).toBe(running)
  })

  it('events after the run has ended change nothing', () => {
    const late: ChangeAction[] = [
      { type: 'stage', stage: 'validating' },
      { type: 'proposing', candidates },
      { type: 'proposal', proposal: scriptedProposalEvent },
      { type: 'failed', failure: rt04 },
    ]
    const ended: ChangeState[] = [
      proposed,
      changeReducer(proposed, { type: 'decided', decision: approved }),
      changeReducer(proposing, { type: 'failed', failure: rt04 }),
      IDLE,
    ]

    for (const state of ended) {
      for (const action of late) {
        expect(changeReducer(state, action)).toBe(state)
      }
    }
  })
})
