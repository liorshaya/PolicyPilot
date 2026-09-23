import { useCallback, useEffect, useReducer, useRef } from 'react'
import { ApiError } from '../../api/client'
import { useDecideChange } from '../../api/queries'
import { openSse } from '../../api/sse'
import type { ChangeDecision } from '../../api/types'
import { changeReducer, IDLE, type ChangeState } from './changeReducer'
import type { Candidates, Proposal, StreamFailure } from './types'

/**
 * A change request and its stream (Document 2, API Surface: POST /rulesets/{id}/versions/{no}/changes answers
 * `analyzing`, `proposing`, `validating`, `regression`, then `proposal` or `error`), and a person's decision on the
 * proposal. The hook owns the stream: a new request abandons the one still open, and nothing of an abandoned stream
 * reaches the state.
 */

export interface ChangeTarget {
  rulesetId: string
  versionNo: number
}

export interface ChangeRun {
  state: ChangeState
  /** Proposes a change on the target version; a request still streaming is abandoned. */
  propose: (text: string) => void
  /** Approves or rejects the proposal on the screen; `then` hears the decision once the API has recorded it. */
  decide: (
    verdict: 'approve' | 'reject',
    note: string,
    then?: (decision: ChangeDecision) => void,
  ) => void
  deciding: boolean
  /** Why the API refused the decision, while the proposal waits to be decided again. */
  decisionError: ApiError | null
}

/** The stages that carry nothing but their name; `proposing` carries the candidates. */
type PlainStage = 'analyzing' | 'validating' | 'regression'
const STAGES = new Set<string>(['analyzing', 'validating', 'regression'] satisfies PlainStage[])

/** A failure the stream never reported: the network or the API, with no findings and no answer. */
const failure = (code: string): StreamFailure => ({ code, findings: [], document: null })

export function useChange(target: ChangeTarget | null): ChangeRun {
  const [state, dispatch] = useReducer(changeReducer, IDLE)
  const abortRef = useRef<AbortController | null>(null)
  const { mutate, reset, isPending, error } = useDecideChange()
  const rulesetId = target?.rulesetId ?? null
  const versionNo = target?.versionNo ?? null

  useEffect(() => () => abortRef.current?.abort(), [])

  const propose = useCallback(
    (text: string) => {
      if (rulesetId === null || versionNo === null) {
        return
      }
      // the request still streaming is abandoned: its next read rejects, so it hands over nothing more
      abortRef.current?.abort()
      const controller = new AbortController()
      abortRef.current = controller
      reset()
      dispatch({ type: 'submitted' })
      void (async () => {
        try {
          for await (const event of openSse(
            `/api/v1/rulesets/${rulesetId}/versions/${versionNo}/changes`,
            { body: { text }, signal: controller.signal },
          )) {
            const data: unknown = JSON.parse(event.data)
            if (STAGES.has(event.event)) {
              dispatch({ type: 'stage', stage: event.event as PlainStage })
            } else if (event.event === 'proposing') {
              dispatch({ type: 'proposing', candidates: data as Candidates })
            } else if (event.event === 'proposal') {
              dispatch({ type: 'proposal', proposal: data as Proposal })
            } else if (event.event === 'error') {
              dispatch({ type: 'failed', failure: data as StreamFailure })
            }
          }
          // a stream that closed without its last event; once a proposal or an error arrived, the reducer ignores it
          dispatch({ type: 'failed', failure: failure('INTERNAL_ERROR') })
        } catch (thrown: unknown) {
          // an abandoned request is the hook's own doing, and the new run it made way for must not hear of it
          if (!controller.signal.aborted) {
            const code = thrown instanceof ApiError ? thrown.code : 'PROVIDER_UNAVAILABLE'
            dispatch({ type: 'failed', failure: failure(code) })
          }
        }
      })()
    },
    [rulesetId, versionNo, reset],
  )

  const decide = useCallback(
    (verdict: 'approve' | 'reject', note: string, then?: (decision: ChangeDecision) => void) => {
      if (state.status !== 'proposed') {
        return
      }
      mutate(
        { changeId: state.proposal.id, verdict, note },
        {
          onSuccess: (decision) => {
            dispatch({ type: 'decided', decision })
            then?.(decision)
          },
        },
      )
    },
    [state, mutate],
  )

  return {
    state,
    propose,
    decide,
    deciding: isPending,
    decisionError: error instanceof ApiError ? error : null,
  }
}
