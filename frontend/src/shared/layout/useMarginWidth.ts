import {
  useEffect,
  useRef,
  useState,
  type KeyboardEvent,
  type PointerEvent,
  type RefObject,
} from 'react'
import type { ScreenId } from './screens'

/** The width a screen's margin was drawn out to, remembered in this browser under the screen's id, as the theme is. */
const WIDTH_KEY = 'pp-margin-'

/** The stored width; none when nothing is stored, when it is no width, or when the browser refuses its storage. */
function storedWidth(screen: ScreenId | undefined): number | null {
  if (screen === undefined) {
    return null
  }
  try {
    const width = Number(localStorage.getItem(WIDTH_KEY + screen))
    return Number.isFinite(width) && width > 0 ? width : null
  } catch {
    return null
  }
}

function rememberWidth(screen: ScreenId, width: number | null): void {
  try {
    if (width === null) {
      localStorage.removeItem(WIDTH_KEY + screen)
    } else {
      localStorage.setItem(WIDTH_KEY + screen, String(width))
    }
  } catch {
    // a browser that refuses its storage still widens the margin, for this visit
  }
}

/** A length of the frame in pixels, as tokens.css sets it on the root. */
function frame(token: string): number {
  return Number.parseFloat(getComputedStyle(document.documentElement).getPropertyValue(token))
}

/** The ends of the handle: the margin's own width, and the most it takes, which leaves the sheet its floor. */
interface Ends {
  own: number
  most: number
}

/** What the handle is drawn with: the separator's values, and what the pointer and the keyboard do to it. */
interface MarginHandle {
  now: number
  own: number
  most: number
  held: boolean
  onPointerDown: (event: PointerEvent<HTMLElement>) => void
  onPointerMove: (event: PointerEvent<HTMLElement>) => void
  onPointerUp: () => void
  onDoubleClick: () => void
  onKeyDown: (event: KeyboardEvent<HTMLElement>) => void
}

interface MarginWidth {
  /** The width the reader set, which the stylesheet holds between the margin's ends; null at the margin's own. */
  set: number | null
  /** The handle, once the body is measured. */
  handle: MarginHandle | null
}

/**
 * The width of the margin beside the sheet (the spec, section 08, Sheet and margin, v3.11). From 1200px the edge between
 * them is a handle: drawn toward the sheet it widens the margin, never under the margin's own width and never past what
 * leaves the sheet --sheet-min; ← and → move it a step of --s-4, Home and End take it to its ends, a double click puts
 * the margin back. The width is remembered per screen in this browser and stands from the first frame; the body is
 * measured by a ResizeObserver, when it starts and whenever the window changes, and the handle waits for it.
 */
export function useMarginWidth(
  body: RefObject<HTMLElement | null>,
  { beside, wide, screen }: { beside: boolean; wide: boolean; screen: ScreenId | undefined },
): MarginWidth {
  const [asked, setAsked] = useState(() => storedWidth(screen))
  const [ends, setEnds] = useState<Ends | null>(null)
  const [held, setHeld] = useState(false)
  // where the pointer took the handle, and the margin's width then
  const takenRef = useRef<{ x: number; width: number } | null>(null)

  useEffect(() => {
    const element = body.current
    if (!beside || element === null || typeof ResizeObserver === 'undefined') {
      return undefined
    }
    const observer = new ResizeObserver(() => {
      const own = frame(wide ? '--margin-w-wide' : '--margin-w')
      const most = Math.max(
        own,
        Math.floor(element.getBoundingClientRect().width - frame('--sheet-min')),
      )
      setEnds((were) => (were?.own === own && were.most === most ? were : { own, most }))
    })
    observer.observe(element)
    return () => {
      observer.disconnect()
      // the handle goes with the measuring, and a hold on it with the handle
      takenRef.current = null
      setHeld(false)
    }
  }, [body, beside, wide])

  useEffect(() => {
    if (screen !== undefined && beside && !held) {
      rememberWidth(screen, asked)
    }
  }, [screen, beside, held, asked])

  if (!beside) {
    return { set: null, handle: null }
  }
  if (ends === null) {
    return { set: asked, handle: null }
  }
  const { own, most } = ends
  const now = asked === null ? own : Math.min(Math.max(asked, own), most)
  /** Sets the margin to a width between its ends; at its own width it is set to nothing. */
  const draw = (width: number) => {
    const next = Math.min(Math.max(Math.round(width), own), most)
    setAsked(next === own ? null : next)
  }

  return {
    set: asked,
    handle: {
      now,
      own,
      most,
      held,
      onPointerDown(event) {
        if (event.button !== 0) {
          return
        }
        // the press is the handle's alone: it selects none of the text the pointer crosses and moves no focus, and the
        // pointer is the handle's until it is let go, wherever it moves
        event.preventDefault()
        event.currentTarget.setPointerCapture(event.pointerId)
        takenRef.current = { x: event.clientX, width: now }
        setHeld(true)
      },
      onPointerMove(event) {
        const taken = takenRef.current
        if (taken !== null) {
          draw(taken.width + taken.x - event.clientX)
        }
      },
      onPointerUp() {
        takenRef.current = null
        setHeld(false)
      },
      onDoubleClick() {
        setAsked(null)
      },
      onKeyDown(event) {
        if (event.altKey || event.ctrlKey || event.metaKey) {
          // the browser's own: ⌘← goes back a page
          return
        }
        const to =
          event.key === 'ArrowLeft'
            ? now + frame('--s-4')
            : event.key === 'ArrowRight'
              ? now - frame('--s-4')
              : event.key === 'Home'
                ? own
                : event.key === 'End'
                  ? most
                  : null
        if (to !== null) {
          event.preventDefault()
          draw(to)
        }
      },
    },
  }
}
