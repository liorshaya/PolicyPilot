import { Palette } from '../../shared/ui/Palette'
import type { PaletteTarget } from './paletteItems'
import { usePaletteItems } from './usePaletteItems'

/** The palette over what this workspace has read; opening a row goes where its target says. */
export function GoToPalette({
  rulesetId,
  onGoTo,
  onClose,
}: {
  /** The rule set the workspace is on. */
  rulesetId: string | null
  onGoTo: (target: PaletteTarget) => void
  onClose: () => void
}) {
  const { items, ran } = usePaletteItems(rulesetId)
  return (
    <Palette items={items} ran={ran} onOpen={(item) => onGoTo(item.target)} onClose={onClose} />
  )
}
