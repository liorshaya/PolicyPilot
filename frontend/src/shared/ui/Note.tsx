import type { ReactNode } from 'react'
import { Actor, type ActorKind } from './Actor'
import './Note.css'

interface NoteProps {
  /** Ink by default; amber for what waits, red for what failed, dashed for what a model proposes. */
  tone?: 'info' | 'warning' | 'error' | 'proposal'
  /** Who says it; the system by default, the model for a proposal. */
  actor?: ActorKind
  children: ReactNode
}

/**
 * What a screen must say in place (the spec, section 08): no fill, a 2px bar and the mark of who speaks. The house
 * phrases are used as they are: "Publishing waits: …", "Nothing was stored.", "Try again in a moment."
 */
export function Note({ tone = 'info', actor, children }: NoteProps) {
  return (
    <div
      className={tone === 'info' ? 'note' : `note note--${tone}`}
      role={tone === 'error' ? 'alert' : undefined}
    >
      <Actor kind={actor ?? (tone === 'proposal' ? 'model' : 'system')} />
      <span>{children}</span>
    </div>
  )
}
