import type { ReactNode } from 'react'
import { Button } from './Button'
import { Note } from './Note'
import './States.css'

interface EmptyStateProps {
  /** The one sentence: what is not here yet. */
  title: string
  /** Beside the action, in the quiet voice: what the action works on. */
  description?: ReactNode
  /** The one action that would put something here. */
  action?: ReactNode
}

/**
 * Nothing here yet (the spec, section 08): the sheet's own ruled lines, the sentence on the first, the one action on the
 * second. No dashed frame, no illustration.
 */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="empty">
      <div className="empty__rule empty__rule--text">{title}</div>
      <div className="empty__rule empty__rule--action">
        {action}
        {description === undefined ? null : <span className="muted">{description}</span>}
      </div>
      <div className="empty__rule" />
      <div className="empty__rule" />
    </div>
  )
}

interface ErrorStateProps {
  /** The envelope's code, shown as it is: it is what a report to the owner quotes (Document 2). */
  code?: string
  description: ReactNode
  onRetry?: () => void
  retryLabel?: string
}

/** Something failed, said in place: a red note with the code and the way to try again. */
export function ErrorState({
  code,
  description,
  onRetry,
  retryLabel = 'Try again',
}: ErrorStateProps) {
  return (
    <Note tone="error">
      Something went wrong. <span>{description}</span>
      {code ? <span className="mono"> {code}</span> : null}
      {onRetry ? (
        <>
          {' '}
          <Button size="sm" onClick={onRetry}>
            {retryLabel}
          </Button>
        </>
      ) : null}
    </Note>
  )
}

/**
 * The wait, stated and not animated (the spec, section 08): three still rows under the real header, and one line that
 * says what is loading.
 */
export function LoadingRows({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="loading" aria-busy="true" aria-live="polite">
      {[0, 1, 2].map((row) => (
        <div className="loading__row" key={row} aria-hidden="true">
          <span />
          <span />
          <span />
          <span />
        </div>
      ))}
      <div className="loading__text">{label}</div>
    </div>
  )
}
