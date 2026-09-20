import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import { keys } from '../../api/queries'
import { openSse } from '../../api/sse'
import type { Finding, VersionResponse } from '../../api/types'

/**
 * The generation stream of a rule set (Document 2, API Surface: `parsing`, `authoring`, `validating`, then the
 * draft). The hook owns the stream: it aborts on a new run and reports where the work is, so the button can show
 * progress instead of a spinner with nothing behind it.
 */

/** The stages the API reports, in the order it reports them. */
export const STAGES = ['parsing', 'authoring', 'validating'] as const

export type Stage = (typeof STAGES)[number]

/** What the analyst reads while each stage runs. */
export const STAGE_LABELS: Record<Stage, string> = {
  parsing: 'Reading the policy',
  authoring: 'Writing the rules',
  validating: 'Checking every rule against the policy',
}

export interface GenerationRefusal {
  code: string
  findings: Finding[]
}

export interface Generation {
  /** The stage the stream last reported, or null when nothing is running. */
  stage: Stage | null
  running: boolean
  draft: VersionResponse | null
  refusal: GenerationRefusal | null
  /** Starts a run for one policy; a run already open is abandoned. */
  start: (policyId: string, hints?: string) => void
  cancel: () => void
}

export function useGeneration(): Generation {
  const [stage, setStage] = useState<Stage | null>(null)
  const [running, setRunning] = useState(false)
  const [draft, setDraft] = useState<VersionResponse | null>(null)
  const [refusal, setRefusal] = useState<GenerationRefusal | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const client = useQueryClient()

  const cancel = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setRunning(false)
    setStage(null)
  }, [])

  const start = useCallback(
    (policyId: string, hints?: string) => {
      abortRef.current?.abort()
      const controller = new AbortController()
      abortRef.current = controller
      setRunning(true)
      setStage(null)
      setDraft(null)
      setRefusal(null)

      void (async () => {
        try {
          for await (const event of openSse(`/api/v1/policies/${policyId}/rulesets`, {
            body: hints === undefined || hints === '' ? {} : { hints },
            signal: controller.signal,
          })) {
            if (STAGES.includes(event.event as Stage)) {
              setStage(event.event as Stage)
            } else if (event.event === 'draft') {
              const written = JSON.parse(event.data) as VersionResponse
              setDraft(written)
              // a generation creates a rule set the cached list does not have, and the screen that opens the
              // draft reads that list; without this it would open whichever rule set the stale list held
              void client.invalidateQueries({ queryKey: keys.rulesets })
              client.setQueryData(keys.version(written.rulesetId, written.versionNo), written)
            } else if (event.event === 'error') {
              const failed = JSON.parse(event.data) as GenerationRefusal
              setRefusal({ code: failed.code, findings: failed.findings ?? [] })
            }
          }
        } catch {
          // an aborted stream is the caller's own doing; anything else is the provider or the network
          if (!controller.signal.aborted) {
            setRefusal({ code: 'PROVIDER_UNAVAILABLE', findings: [] })
          }
        } finally {
          if (abortRef.current === controller) {
            abortRef.current = null
            setRunning(false)
            setStage(null)
          }
        }
      })()
    },
    [client],
  )

  return { stage, running, draft, refusal, start, cancel }
}
