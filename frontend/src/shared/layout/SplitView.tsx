import type { ReactNode } from 'react'
import './SplitView.css'

interface SplitViewProps {
  /** The work itself, on the sheet: the table, the document, the list. */
  main: ReactNode
  /** What annotates the selection, in the paper margin beside the sheet. */
  side?: ReactNode
  /** Whether the margin is open; closed, the sheet takes the whole width. */
  sideOpen: boolean
  /** The wider margin, for a trace. */
  wide?: boolean
}

/**
 * The body of a working screen (the spec, section 08): the sheet, the only white area, and the paper margin beside it at
 * 340px, or 440px for a trace. Each scrolls on its own under the header; below 1200px the margin is a drawer over the
 * sheet.
 */
export function SplitView({ main, side, sideOpen, wide = false }: SplitViewProps) {
  const open = sideOpen && side !== undefined && side !== null
  const layout = !open ? ' ws-body--single' : wide ? ' ws-body--wide-margin' : ''
  return (
    <div className={`ws-body${layout}`}>
      <section className="sheet">
        <div className="sheet__scroll">{main}</div>
      </section>
      {open ? <aside className="margin">{side}</aside> : null}
    </div>
  )
}
