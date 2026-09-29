import { useSyncExternalStore } from 'react'

/**
 * The width from which the margin stands beside the sheet rather than over it as a drawer (the spec, section 08:
 * "below 1200px the margin becomes a drawer over the sheet").
 */
const WIDE = '(min-width: 1200px)'

function subscribe(onChange: () => void): () => void {
  if (typeof window.matchMedia !== 'function') {
    return () => undefined
  }
  const query = window.matchMedia(WIDE)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

function matches(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(WIDE).matches
}

/** Whether the window is wide enough for the margin to stand beside the sheet, following it as it is resized. */
export function useWide(): boolean {
  return useSyncExternalStore(subscribe, matches, () => false)
}
