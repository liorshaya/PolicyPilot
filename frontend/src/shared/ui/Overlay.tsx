import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Button } from './Button'
import './Overlay.css'

interface DialogProps {
  /** The dialog asks: "Publish version 2?". A title that is not a question opens no dialog. */
  title: `${string}?`
  /** What cannot be undone, said plainly: the only reason a dialog exists. */
  cannotUndo: ReactNode
  /** What changes, and the facts it rests on. */
  children?: ReactNode
  /** Cancel, then the act itself. */
  actions: ReactNode
}

/** Only for the irreversible (the spec, section 08): a question for a title, and what cannot be undone. */
export function Dialog({ title, cannotUndo, children, actions }: DialogProps) {
  const id = useId()
  if (!title.endsWith('?')) {
    return null
  }
  return (
    <div className="dialog" role="dialog" aria-modal="true" aria-labelledby={id}>
      <div className="dialog__head">
        <div className="dialog__title" id={id}>
          {title}
        </div>
      </div>
      <div className="dialog__body">
        {children}
        <p>{cannotUndo}</p>
      </div>
      <div className="dialog__foot">{actions}</div>
    </div>
  )
}

interface PopoverProps {
  /** The control that opened it, which it stands beside. */
  anchor: HTMLElement
  /** What it is, for a screen reader. */
  label: string
  /** The edge of the control it lines up with: its start, or its end for a control at the window's end edge. */
  align?: 'start' | 'end'
  onClose: () => void
  children: ReactNode
}

/**
 * A popover (the spec, section 08), placed from the control that opened it: under it in the upper half of the window,
 * over it in the lower half, at its start edge, or at its end edge for a control at the window's end; the gap is the
 * CSS's. Escape closes it and gives the focus back.
 */
export function Popover({ anchor, label, align = 'start', onClose, children }: PopoverProps) {
  const boxRef = useRef<HTMLDivElement>(null)
  const rect = anchor.getBoundingClientRect()
  const below = rect.top < window.innerHeight / 2

  useEffect(() => {
    boxRef.current?.focus()
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
        anchor.focus()
      }
    }
    const onPointer = (event: PointerEvent) => {
      const target = event.target as Node
      if (!boxRef.current?.contains(target) && !anchor.contains(target)) {
        onClose()
      }
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('pointerdown', onPointer)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('pointerdown', onPointer)
    }
  }, [anchor, onClose])

  return (
    <div
      ref={boxRef}
      className="popover"
      role="dialog"
      aria-label={label}
      tabIndex={-1}
      data-side={below ? 'below' : 'above'}
      style={{
        ...(align === 'start'
          ? { left: rect.left }
          : { right: document.documentElement.clientWidth - rect.right }),
        ...(below ? { top: rect.bottom } : { bottom: window.innerHeight - rect.top }),
      }}
    >
      {children}
    </div>
  )
}

interface ToastProps {
  /** The confirmation, or what failed and that nothing was stored. */
  children: ReactNode
  /** The next step it offers. */
  action?: { label: string; onClick: () => void }
  /** An error stays until it is closed. */
  tone?: 'error'
  /** How long a confirmation stands before it goes; the screen that raises it chooses. */
  lifetimeMs?: number
  onClose: () => void
}

/**
 * A toast (the spec, section 08): it confirms and offers the next step, then goes; an error toast stays until it is
 * closed. It is never used for something the screen should say in place.
 */
export function Toast({ children, action, tone, lifetimeMs, onClose }: ToastProps) {
  const error = tone === 'error'
  useEffect(() => {
    if (error || lifetimeMs === undefined) {
      return undefined
    }
    const timer = window.setTimeout(onClose, lifetimeMs)
    return () => window.clearTimeout(timer)
  }, [error, lifetimeMs, onClose])

  return (
    <div className={error ? 'toast toast--error' : 'toast'} role={error ? 'alert' : 'status'}>
      <span>{children}</span>
      {action ? (
        <Button variant="quiet" onClick={action.onClick}>
          {action.label}
        </Button>
      ) : null}
      {error ? (
        <Button variant="quiet" onClick={onClose}>
          Close
        </Button>
      ) : null}
    </div>
  )
}
