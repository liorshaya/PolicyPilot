import type { ReactNode } from 'react'
import './States.css'

interface EmptyStateProps {
  title: string
  /** What to do about it, in one sentence. */
  description: ReactNode
  action?: ReactNode
}

/** Nothing here yet, and what would put something here. */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="state">
      <p className="state__title">{title}</p>
      <p className="state__description">{description}</p>
      {action ? <div className="state__action">{action}</div> : null}
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

/** Something failed: the code, what it means, and the way to try again. */
export function ErrorState({
  code,
  description,
  onRetry,
  retryLabel = 'Try again',
}: ErrorStateProps) {
  return (
    <div className="state state--error" role="alert">
      <p className="state__title">Something went wrong</p>
      <p className="state__description">{description}</p>
      {code ? <p className="state__code mono">{code}</p> : null}
      {onRetry ? (
        <div className="state__action">
          <button type="button" className="button button--secondary button--md" onClick={onRetry}>
            <span>{retryLabel}</span>
          </button>
        </div>
      ) : null}
    </div>
  )
}

/** The shape of the content that is loading, so the layout does not jump when it arrives. */
export function LoadingRows({ rows = 4, label = 'Loading' }: { rows?: number; label?: string }) {
  return (
    <div className="loading" aria-busy="true" aria-live="polite">
      <span className="sr-only">{label}</span>
      {Array.from({ length: rows }, (_, index) => (
        <span className="loading__row" key={index} />
      ))}
    </div>
  )
}
