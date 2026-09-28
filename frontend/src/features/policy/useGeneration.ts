import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import { ApiError } from '../../api/client'
import { keys } from '../../api/queries'
import { openSse } from '../../api/sse'
import type { Finding, VersionResponse } from '../../api/types'

/**
 * The generation stream of a rule set (Document 2, API Surface: `parsing`, `authoring`, `validating`, `reviewing`,
 * then the draft with its review, or an error). The hook owns the stream: it aborts on a new run and keeps what each
 * stage reported, so the screen shows where the work is and, once it is done, what each stage produced and how long
 * the run took in this browser (the owner's answer of 2026-09-28 to phase 5's first question).
 */

/** The stages the API reports, in the order it reports them. */
export const STAGES = ['parsing', 'authoring', 'validating', 'reviewing'] as const

export type Stage = (typeof STAGES)[number]

/** What the analyst reads while each stage runs. */
export const STAGE_LABELS: Record<Stage, string> = {
  parsing: 'Reading the policy',
  authoring: 'Writing the rules',
  validating: 'Checking every rule against the policy',
  reviewing: 'Reviewing the draft for gaps, conflicts and ambiguities',
}

export interface GenerationRefusal {
  code: string
  findings: Finding[]
  /** The model's last answer, when the stream carried one: what it proposed before the validator refused it. */
  document: unknown
}

export interface Generation {
  /** The policy the run is for, or null before the first run. */
  policyId: string | null
  /** The stages the stream reported, in order; while it is open, the last is the one running. */
  stages: Stage[]
  running: boolean
  /** The policy's paragraphs, as the first stage reported them. */
  paragraphs: number | null
  /** How long the run took in this browser, from its first event to the draft, in milliseconds. */
  took: number | null
  draft: VersionResponse | null
  refusal: GenerationRefusal | null
  /** Starts a run for one policy; a run already open is abandoned. */
  start: (policyId: string, hints?: string) => void
}

/** The payload of a progress event (Document 2): what the stage is working on. */
interface Progress {
  paragraphs?: number
}

export function useGeneration(): Generation {
  const [policyId, setPolicyId] = useState<string | null>(null)
  const [stages, setStages] = useState<Stage[]>([])
  const [running, setRunning] = useState(false)
  const [paragraphs, setParagraphs] = useState<number | null>(null)
  const [took, setTook] = useState<number | null>(null)
  const [draft, setDraft] = useState<VersionResponse | null>(null)
  const [refusal, setRefusal] = useState<GenerationRefusal | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const client = useQueryClient()

  const start = useCallback(
    (policy: string, hints?: string) => {
      abortRef.current?.abort()
      const controller = new AbortController()
      abortRef.current = controller
      setPolicyId(policy)
      setRunning(true)
      setStages([])
      setParagraphs(null)
      setTook(null)
      setDraft(null)
      setRefusal(null)

      void (async () => {
        // the run's time starts with its first event, the moment the API began the work
        let began: number | null = null
        try {
          for await (const event of openSse(`/api/v1/policies/${policy}/rulesets`, {
            body: hints === undefined || hints === '' ? {} : { hints },
            signal: controller.signal,
          })) {
            began ??= performance.now()
            if (STAGES.includes(event.event as Stage)) {
              const stage = event.event as Stage
              setStages((seen) => (seen.includes(stage) ? seen : [...seen, stage]))
              const progress = JSON.parse(event.data) as Progress
              if (stage === 'parsing' && progress.paragraphs !== undefined) {
                setParagraphs(progress.paragraphs)
              }
            } else if (event.event === 'draft') {
              const written = JSON.parse(event.data) as VersionResponse
              setTook(performance.now() - began)
              setDraft(written)
              // a generation creates a rule set the cached list does not have, and the screen that opens the
              // draft reads that list; without this it would open whichever rule set the stale list held
              void client.invalidateQueries({ queryKey: keys.rulesets })
              client.setQueryData(keys.version(written.rulesetId, written.versionNo), written)
            } else if (event.event === 'error') {
              const failed = JSON.parse(event.data) as GenerationRefusal
              setRefusal({
                code: failed.code,
                findings: failed.findings ?? [],
                document: failed.document ?? null,
              })
            }
          }
        } catch (error) {
          // an aborted stream is the caller's own doing; a refusal before the stream opened carries the API's code
          // (429 RATE_LIMITED, 404 NOT_FOUND), and anything without one is the provider or the network
          if (!controller.signal.aborted) {
            setRefusal({
              code:
                error instanceof ApiError
                  ? (error.envelope?.code ?? 'PROVIDER_UNAVAILABLE')
                  : 'PROVIDER_UNAVAILABLE',
              findings: [],
              document: null,
            })
          }
        } finally {
          if (abortRef.current === controller) {
            abortRef.current = null
            setRunning(false)
          }
        }
      })()
    },
    [client],
  )

  return { policyId, stages, running, paragraphs, took, draft, refusal, start }
}
