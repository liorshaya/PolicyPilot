import type { ReactElement } from 'react'
import './Provenance.css'

interface ProvenanceProps {
  /** The line's segments in order: what, its state, how much, when, by whom; an element carries its own key. */
  segments: (string | ReactElement)[]
  /** When the line must be shorter, the segments beyond this many are dropped, from the end. */
  maxSegments?: number
}

/**
 * The provenance line (the spec, section 04): one line in mono that never wraps; the separators are drawn by the CSS
 * before each segment's own box, so none can dangle and none sits inside a tag's border; when it does not fit, it ends
 * in an ellipsis, and a shorter one drops its last segments first.
 */
export function Provenance({ segments, maxSegments }: ProvenanceProps) {
  const shown = maxSegments === undefined ? segments : segments.slice(0, maxSegments)
  return (
    <div className="prov">
      {shown.map((segment) => (
        <span key={typeof segment === 'string' ? segment : String(segment.key)}>{segment}</span>
      ))}
    </div>
  )
}
