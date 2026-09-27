import { useId, type ReactElement, type ReactNode } from 'react'
import { Provenance } from '../ui/Provenance'
import './WorkspaceHeader.css'

interface WorkspaceHeaderProps {
  title: string
  /** The version's state, beside the title. */
  version?: ReactNode
  /** The provenance line's segments: what is open, where it comes from, how much of it, when. */
  provenance?: (string | ReactElement)[]
  /** Pickers that stand in the header until their phase moves them into the sheet's toolbar (Document 9). */
  controls?: ReactNode
  /** At most one secondary action. */
  secondary?: ReactNode
  /** Why the primary is disabled, written beside it rather than hidden in a tooltip. */
  reason?: ReactNode
  /** The one primary action of the screen, at 36px. */
  primary?: ReactNode
}

/**
 * The top of the workspace (the spec, section 08): the screen's title at 20/600 with the version's state beside it and
 * the provenance line under it; on the end at most one secondary action, the reason when the primary is disabled, and
 * the primary. It stays put while the sheet and the margin scroll.
 */
export function WorkspaceHeader({
  title,
  version,
  provenance,
  controls,
  secondary,
  reason,
  primary,
}: WorkspaceHeaderProps) {
  const titleId = useId()
  return (
    <header className="ws-header">
      <div className="ws-header__text">
        <h1 className="ws-header__title" aria-labelledby={titleId}>
          <span id={titleId}>{title}</span>
          {version ? <> {version}</> : null}
        </h1>
        {provenance && provenance.length > 0 ? <Provenance segments={provenance} /> : null}
      </div>
      {controls || secondary || reason || primary ? (
        <div className="ws-header__side">
          {controls}
          {secondary}
          {reason ? <span className="reason">{reason}</span> : null}
          {primary}
        </div>
      ) : null}
    </header>
  )
}
