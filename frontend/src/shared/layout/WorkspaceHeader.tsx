import { use, useId, type ReactElement, type ReactNode } from 'react'
import { Button } from '../ui/Button'
import { Kbd } from '../ui/Kbd'
import { PaletteContext } from '../ui/paletteContext'
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
  /** The palette's "Go to ⌘K", first on the end, as the Rules and Cases headers carry it (the spec, section 10). */
  goTo?: boolean
  /** At most one secondary action. */
  secondary?: ReactNode
  /** Why the primary is disabled, written beside it rather than hidden in a tooltip. */
  reason?: ReactNode
  /** The one primary action of the screen, at 36px. */
  primary?: ReactNode
}

/**
 * The top of the workspace (the spec, section 08): the screen's title at 20/600 with the version's state beside it and
 * the provenance line under it; on the end the palette's Go to where the screen carries it, at most one secondary
 * action, the reason when the primary is disabled, and the primary. It stays put while the sheet and the margin scroll.
 */
export function WorkspaceHeader({
  title,
  version,
  provenance,
  controls,
  goTo = false,
  secondary,
  reason,
  primary,
}: WorkspaceHeaderProps) {
  const titleId = useId()
  const openPalette = use(PaletteContext)
  const goToButton =
    goTo && openPalette !== null ? (
      <Button icon="search" onClick={openPalette}>
        Go to <Kbd>⌘K</Kbd>
      </Button>
    ) : null
  return (
    <header className="ws-header">
      <div className="ws-header__text">
        <h1 className="ws-header__title" aria-labelledby={titleId}>
          <span id={titleId}>{title}</span>
          {version ? <> {version}</> : null}
        </h1>
        {provenance && provenance.length > 0 ? <Provenance segments={provenance} /> : null}
      </div>
      {controls || goToButton || secondary || reason || primary ? (
        <div className="ws-header__side">
          {controls}
          {goToButton}
          {secondary}
          {reason ? <span className="reason">{reason}</span> : null}
          {primary}
        </div>
      ) : null}
    </header>
  )
}
