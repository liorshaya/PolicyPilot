import { useMedia } from './useMedia'

/**
 * The width from which the margin stands beside the sheet rather than over it as a drawer (the spec, section 08:
 * "below 1200px the margin becomes a drawer over the sheet").
 */
const WIDE = '(min-width: 1200px)'

/** Whether the window is wide enough for the margin to stand beside the sheet, following it as it is resized. */
export function useWide(): boolean {
  return useMedia(WIDE)
}
