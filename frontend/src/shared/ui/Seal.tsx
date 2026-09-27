import { useEffect, useRef, useState, type ReactNode } from 'react'
import './Seal.css'

interface SealProps {
  /** What a person did; the seal writes it in the only capitals of the product. */
  kicker: 'Published' | 'Approved' | 'Rejected' | 'Acknowledged'
  /** What and when: "v1 · 2026-09-22 14:02", or on the one line of an inline seal, who as well. */
  line: ReactNode
  /** Who, on the third line of a full seal. */
  by?: string
  /** The one-line form, for a row or a sentence. */
  inline?: boolean
  /** Lands with the one signature motion when the act has just happened. */
  stamp?: boolean
}

function prefersReducedMotion(): boolean {
  return (
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/**
 * A person's approval (the spec, section 04): one rule with a second rule inside it, in the brand blue and mono, where a
 * person's authority becomes a fact. It stamps once when it arrives, over --t-seal, and not at all for a reader who
 * prefers reduced motion.
 */
export function Seal({ kicker, line, by, inline = false, stamp = false }: SealProps) {
  const [stamping, setStamping] = useState(() => stamp && !prefersReducedMotion())
  const sealRef = useRef<HTMLElement>(null)
  useEffect(() => {
    const element = sealRef.current
    if (!stamping || element === null) {
      return undefined
    }
    const done = () => setStamping(false)
    element.addEventListener('animationend', done)
    return () => element.removeEventListener('animationend', done)
  }, [stamping])
  const classes = [
    'seal',
    inline ? 'seal--inline' : '',
    kicker === 'Rejected' ? 'seal--rejected' : '',
    stamping ? 'seal--stamp' : '',
  ]
    .filter(Boolean)
    .join(' ')
  const Tag = inline ? 'span' : 'div'
  return (
    <Tag className={classes} ref={sealRef as never}>
      <span className="seal__kicker">{kicker}</span>
      <span className="seal__line">{line}</span>
      {by !== undefined && !inline ? <span className="seal__by">by {by}</span> : null}
    </Tag>
  )
}
