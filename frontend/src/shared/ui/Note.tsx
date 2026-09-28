import type { ReactNode } from 'react'
import { Actor, type ActorKind } from './Actor'
import './Note.css'

interface NoteProps {
  /** Ink by default; amber for what waits, red for what failed, dashed for what a model proposes. */
  tone?: 'info' | 'warning' | 'error' | 'proposal'
  /** Who says it; the system by default, the model for a proposal. */
  actor?: ActorKind
  /** What a note the screen keeps in place is about ("Budget"), which names it as a note; an error is an alert. */
  label?: string
  children: ReactNode
}

/**
 * What a screen must say in place (the spec, section 08): no fill, a 2px bar and the mark of who speaks. The house
 * phrases are used as they are: "Publishing waits: …", "Nothing was stored.", "Try again in a moment."
 */
export function Note({ tone = 'info', actor, label, children }: NoteProps) {
  return (
    <div
      className={tone === 'info' ? 'note' : `note note--${tone}`}
      role={tone === 'error' ? 'alert' : label !== undefined ? 'note' : undefined}
      aria-label={tone === 'error' ? undefined : label}
    >
      <Actor kind={actor ?? (tone === 'proposal' ? 'model' : 'system')} />
      <span>{children}</span>
    </div>
  )
}
