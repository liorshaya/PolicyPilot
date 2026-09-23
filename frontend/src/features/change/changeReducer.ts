import type { ChangeDecision } from '../../api/types'
import type { Candidates, ChangeStage, Proposal, StreamFailure } from './types'

/**
 * A change request as the screen holds it (Document 2, the change stream: `analyzing`, `proposing` with the candidate
 * rules and fields, `validating`, `regression`, then `proposal` or `error`). A run ends with a proposal a person then
 * approves or rejects, or with a failure; nothing that arrives after the end changes it, so a late event of an
 * abandoned stream can never overwrite what the analyst is looking at.
 */
export type ChangeState =
  | { status: 'idle' }
  | { status: 'running'; stage: ChangeStage | null; candidates: Candidates | null }
  | { status: 'proposed'; candidates: Candidates | null; proposal: Proposal }
  | { status: 'failed'; candidates: Candidates | null; failure: StreamFailure }
  | { status: 'decided'; proposal: Proposal; decision: ChangeDecision }

export type ChangeAction =
  | { type: 'submitted' }
  | { type: 'stage'; stage: 'analyzing' | 'validating' | 'regression' }
  | { type: 'proposing'; candidates: Candidates }
  | { type: 'proposal'; proposal: Proposal }
  | { type: 'failed'; failure: StreamFailure }
  | { type: 'decided'; decision: ChangeDecision }

export const IDLE: ChangeState = { status: 'idle' }

export function changeReducer(state: ChangeState, action: ChangeAction): ChangeState {
  if (action.type === 'submitted') {
    return { status: 'running', stage: null, candidates: null }
  }
  if (action.type === 'decided') {
    return state.status === 'proposed'
      ? { status: 'decided', proposal: state.proposal, decision: action.decision }
      : state
  }
  if (state.status !== 'running') {
    return state
  }
  switch (action.type) {
    case 'stage':
      return { ...state, stage: action.stage }
    case 'proposing':
      return { ...state, stage: 'proposing', candidates: action.candidates }
    case 'proposal':
      return { status: 'proposed', candidates: state.candidates, proposal: action.proposal }
    case 'failed':
      return { status: 'failed', candidates: state.candidates, failure: action.failure }
  }
}
