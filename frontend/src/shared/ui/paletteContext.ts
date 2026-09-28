import { createContext } from 'react'

/**
 * What opens the palette (the spec, section 08), for a control that stands deep in a screen, such as the "Go to ⌘K" of
 * the Rules and Cases headers; null where no palette can open.
 */
export const PaletteContext = createContext<(() => void) | null>(null)
