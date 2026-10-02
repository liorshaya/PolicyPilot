import { useMedia } from './useMedia'

/**
 * The widths at which the margin is a drawer over the sheet: below 1200px and above a phone's 720px, where it is the
 * next section of the page instead (the spec, sections 08 and 10; SplitView.css).
 */
const DRAWER = '(min-width: 721px) and (max-width: 1199px)'

/** Whether the margin is a drawer over the sheet, which starts closed (the spec, section 08, v3.9). */
export function useDrawer(): boolean {
  return useMedia(DRAWER)
}
