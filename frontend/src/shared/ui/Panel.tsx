import type { ReactNode } from 'react'
import './Panel.css'

interface PanelProps {
  /** The panel's own title; it is the heading a screen reader lands on. */
  title: ReactNode
  /** One line under the title, for the context the title cannot carry. */
  subtitle?: ReactNode
  /** Controls that belong to this panel, shown at the end of its header. */
  actions?: ReactNode
  /** Removes the body padding, for a table that reaches the panel's edges. */
  flush?: boolean
  className?: string
  children: ReactNode
}

/** A working surface: white, hairline border, a quiet header and the content below it. */
export function Panel({
  title,
  subtitle,
  actions,
  flush = false,
  className,
  children,
}: PanelProps) {
  const classes = ['panel', className].filter(Boolean).join(' ')
  return (
    <section className={classes}>
      <header className="panel__header">
        <div className="panel__heading">
          <h2 className="panel__title">{title}</h2>
          {subtitle ? <p className="panel__subtitle">{subtitle}</p> : null}
        </div>
        {actions ? <div className="panel__actions">{actions}</div> : null}
      </header>
      <div className={flush ? 'panel__body panel__body--flush' : 'panel__body'}>{children}</div>
    </section>
  )
}
