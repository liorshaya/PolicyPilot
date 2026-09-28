import type { UseQueryResult } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { useDecision, useProposedDecision } from '../../api/queries'
import type { FieldSchema } from '../../api/types'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { Button } from '../../shared/ui/Button'
import { ErrorState, LoadingRows } from '../../shared/ui/States'
import { TraceView } from '../cases/TraceView'
import type { Flip } from './types'
import './BothTraces.css'

interface BothTracesProps {
  flip: Flip
  /** The proposal whose patched copy decided the case again. */
  changeId: string
  language: ContentLanguage
  /** The base version's fields, for the unit of each value the engine derived. */
  fields: FieldSchema[]
  onClose: () => void
}

/**
 * A flipped case's two traces, one under the other in the margin (the spec, section 09: "each flipped case with both
 * traces one click away"): the decision the sandbox stored on the base version, then what the proposal decides for the
 * same input on its patched copy (Document 2, GET /changes/{id}/decisions/{decisionId}/trace), which is never stored.
 */
export function BothTraces({ flip, changeId, language, fields, onClose }: BothTracesProps) {
  const stored = useDecision(flip.decisionId)
  const proposed = useProposedDecision({ changeId, decisionId: flip.decisionId })
  return (
    <div className="sheet both-traces">
      <div className="sheet__scroll">
        {stored.data ? (
          <TraceView decision={stored.data} language={language} fields={fields} onClose={onClose} />
        ) : (
          <Waiting query={stored} title="Decision" what="the decision" onClose={onClose} />
        )}
        {proposed.data ? (
          <TraceView
            decision={proposed.data}
            caseNo={flip.caseNo}
            language={language}
            fields={fields}
          />
        ) : (
          <Waiting query={proposed} title="Proposed" what="what the proposal decides" />
        )}
      </div>
    </div>
  )
}

/** A trace not read yet: its section's head, and the wait or the failure in its place. */
function Waiting({
  query,
  title,
  what,
  onClose,
}: {
  query: UseQueryResult
  title: string
  what: string
  onClose?: () => void
}) {
  return (
    <>
      <div className="sec">
        <h2 className="sec__title">{title}</h2>
        {onClose ? (
          <div className="sec__side">
            <Button variant="quiet" size="sm" onClick={onClose}>
              Close
            </Button>
          </div>
        ) : null}
      </div>
      {query.isPending ? <LoadingRows label={`Loading ${what}`} /> : null}
      {query.error ? (
        <ErrorState
          code={query.error instanceof ApiError ? query.error.code : undefined}
          description={`${what.charAt(0).toUpperCase()}${what.slice(1)} could not be read.`}
          onRetry={() => void query.refetch()}
        />
      ) : null}
    </>
  )
}
