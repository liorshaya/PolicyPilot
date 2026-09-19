import type { ReactNode } from 'react'
import './SplitView.css'

interface SplitViewProps {
  /** The table or the document: the work itself. */
  main: ReactNode
  /** The details of whatever is selected; it opens and closes without losing the main view. */
  side?: ReactNode
  /** Whether the side panel is open; when it is closed the main view takes the whole width. */
  sideOpen: boolean
}

/** The split the working screens use: content in the middle, details beside it (the brief, Layout). */
export function SplitView({ main, side, sideOpen }: SplitViewProps) {
  return (
    <div className={`split${sideOpen ? ' split--open' : ''}`}>
      <div className="split__main">{main}</div>
      {sideOpen && side ? <aside className="split__side">{side}</aside> : null}
    </div>
  )
}
