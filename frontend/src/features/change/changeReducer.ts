import type { ChangeDecision } from '../../api/types'
import type { Candidates, ChangeStage, Proposal, StageEnded, StreamFailure } from './types'

/**
 * A change request as the screen holds it (Document 2, the change stream: `analyzing`, `proposing` with the candidate
 * rules and fields, `validating`, `regression`, then `proposal` or `error`, every event after the first with the stage
 * it ended). A run ends with a proposal a person then approves or rejects, or with a failure; nothing that arrives
 * after the end changes it, so a late event of an abandoned stream can never overwrite what the analyst is looking at.
 * The stages' times and tokens stay with the run, to be read beside its result.
 */
export type ChangeState =
  | { status: 'idle' }
  | {
      status: 'running'
      stage: ChangeStage | null
      candidates: Candidates | null
      timings: StageEnded[]
    }
  | { status: 'proposed'; candidates: Candidates | null; timings: StageEnded[]; proposal: Proposal }
  | {
      status: 'failed'
      candidates: Candidates | null
      timings: StageEnded[]
      failure: StreamFailure
    }
  | {
      status: 'decided'
      candidates: Candidates | null
      timings: StageEnded[]
      proposal: Proposal
      decision: ChangeDecision
    }

export type ChangeAction =
  | { type: 'submitted' }
  | { type: 'stage'; stage: 'analyzing' | 'validating' | 'regression'; ended: StageEnded | null }
  | { type: 'proposing'; candidates: Candidates; ended: StageEnded | null }
  | { type: 'proposal'; proposal: Proposal }
  | { type: 'failed'; failure: StreamFailure }
  | { type: 'decided'; decision: ChangeDecision }

export const IDLE: ChangeState = { status: 'idle' }

/** The times so far and the one an event ended, when it carries one. */
function timed(timings: StageEnded[], ended: StageEnded | null | undefined): StageEnded[] {
  return ended ? [...timings, ended] : timings
}

export function changeReducer(state: ChangeState, action: ChangeAction): ChangeState {
  if (action.type === 'submitted') {
    return { status: 'running', stage: null, candidates: null, timings: [] }
  }
  if (action.type === 'decided') {
    return state.status === 'proposed'
      ? {
          status: 'decided',
          candidates: state.candidates,
          timings: state.timings,
          proposal: state.proposal,
          decision: action.decision,
        }
      : state
  }
  if (state.status !== 'running') {
    return state
  }
  switch (action.type) {
    case 'stage':
      return { ...state, stage: action.stage, timings: timed(state.timings, action.ended) }
    case 'proposing':
      return {
        ...state,
        stage: 'proposing',
        candidates: action.candidates,
        timings: timed(state.timings, action.ended),
      }
    case 'proposal':
      return {
        status: 'proposed',
        candidates: state.candidates,
        timings: timed(state.timings, action.proposal.ended),
        proposal: action.proposal,
      }
    case 'failed':
      return {
        status: 'failed',
        candidates: state.candidates,
        timings: timed(state.timings, action.failure.ended),
        failure: action.failure,
      }
  }
}
