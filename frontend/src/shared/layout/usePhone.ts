import { useMedia } from './useMedia'

/** The width below which the rail becomes the phone's top bar (the spec, section 08: "below 720px"). */
const PHONE = '(max-width: 720px)'

/** Whether the window is a phone's (720px or narrower), following it as it is resized or turned. */
export function usePhone(): boolean {
  return useMedia(PHONE)
}
