import type { ReactNode } from 'react'
import './Actor.css'

export type ActorKind = 'model' | 'engine' | 'person' | 'system'

/** The word a mark carries when it stands alone. */
const NAMES: Record<ActorKind, string> = {
  model: 'Model',
  engine: 'Engine',
  person: 'Person',
  system: 'System',
}

interface ActorProps {
  kind: ActorKind
  /** Who, and what they did: "Engine · 1.0.0 · decided in 61 µs". A model is never named as an author. */
  children?: ReactNode
  large?: boolean
}

/**
 * Who did what (the spec, section 04): the model's dotted circle, the engine's solid square, a person's silhouette, the
 * system's outlined square. The mark is drawn for the eye; the word beside it, or the title of a mark alone, says it.
 */
export function Actor({ kind, children, large = false }: ActorProps) {
  return (
    <span
      className={`actor actor--${kind}${large ? ' actor--lg' : ''}`}
      title={children === undefined ? NAMES[kind] : undefined}
    >
      <span className="actor__mark" aria-hidden="true" />
      {children}
    </span>
  )
}
