import { useSyncExternalStore } from 'react'

/** The width below which the rail becomes the phone's top bar (the spec, section 08: "below 720px"). */
const PHONE = '(max-width: 720px)'

function subscribe(onChange: () => void): () => void {
  if (typeof window.matchMedia !== 'function') {
    return () => undefined
  }
  const query = window.matchMedia(PHONE)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

function matches(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(PHONE).matches
}

/** Whether the window is a phone's (720px or narrower), following it as it is resized or turned. */
export function usePhone(): boolean {
  return useSyncExternalStore(subscribe, matches, () => false)
}
