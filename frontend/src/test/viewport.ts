import { vi } from 'vitest'

/**
 * A window of one width, as matchMedia reports it: jsdom has no matchMedia, so a test that depends on the layout's
 * breakpoints (the phone below 720px, the drawer from 721 to 1199px, the margin beside the sheet from 1200px; the spec,
 * sections 08 and 10) says how wide the window is. A query of min-width and max-width terms joined by "and" is answered
 * from the width; any other feature does not match. Undone with vi.unstubAllGlobals().
 */
export function windowAt(width: number): void {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: query.split(/\s+and\s+/).every((term) => {
      const bound = /^\((min|max)-width:\s*(\d+)px\)$/.exec(term.trim())
      if (bound === null) {
        return false
      }
      const pixels = Number(bound[2])
      return bound[1] === 'min' ? width >= pixels : width <= pixels
    }),
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }))
}
