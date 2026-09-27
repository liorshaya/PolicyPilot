import { useId, type ReactNode } from 'react'
import './Section.css'

interface SectionProps {
  /** The section's title; it names the region for a screen reader. */
  title: ReactNode
  /** A count or a note in the title's quiet voice, never a subline under it. */
  subtitle?: ReactNode
  /** The section's own controls, at the side of its title row. */
  actions?: ReactNode
  /** No padding, for a table that reaches the sheet's edges. */
  flush?: boolean
  children: ReactNode
}

/**
 * A section of the sheet (the spec, section 08): a title row in 13/600 with its controls at the side, then its body. It
 * draws no card of its own: the sheet is the surface, and sections stand on it one after the other.
 */
export function Section({ title, subtitle, actions, flush = false, children }: SectionProps) {
  const id = useId()
  return (
    <section aria-labelledby={id}>
      <div className="sec">
        <h2 className="sec__title" aria-labelledby={id}>
          <span id={id}>{title}</span>
          {subtitle ? (
            <>
              {' '}
              <span className="quiet">
                · <span>{subtitle}</span>
              </span>
            </>
          ) : null}
        </h2>
        {actions ? <div className="sec__side">{actions}</div> : null}
      </div>
      {flush ? children : <div className="sheet__body">{children}</div>}
    </section>
  )
}
