import { useEffect, useId, useRef, type CSSProperties, type ReactNode } from 'react'
import { Button } from '../ui/Button'
import type { ScreenId } from './screens'
import { useDrawer } from './useDrawer'
import { useMarginWidth } from './useMarginWidth'
import { usePhone } from './usePhone'
import { useWide } from './useWide'
import './SplitView.css'

interface SplitViewProps {
  /** The work itself, on the sheet: the table, the document, the list. */
  main: ReactNode
  /** What annotates the selection, in the paper margin beside the sheet. */
  side?: ReactNode
  /** Whether the margin is open; closed, the sheet takes the whole width. */
  sideOpen: boolean
  /** The wider margin, for a trace. */
  wide?: boolean
  /** The sheet's section fills it and its table is the only scroller, as in the spec's composed screens. */
  fill?: boolean
  /** The margin holds a sheet of its own, for a trace or a review (the spec's Cases screen: `margin--sheet`). */
  sideSheet?: boolean
  /** What the margin holds, which names it as a landmark: "Paragraph 2". */
  sideLabel?: string
  /**
   * Shuts the margin: Esc does at every width ("in a table, ↵ opens the margin, Esc closes it", the spec, section 08),
   * and below 1200px, where the margin is a drawer, its own Close where `closeButton` asks for one (v3.9).
   */
  onCloseSide?: () => void
  /** The drawer carries its Close at its head; a margin that holds a Close of its own, as a trace does, leaves it out. */
  closeButton?: boolean
  /** What the margin is about, such as the chosen row: on a phone a new one brings the margin into view. */
  sideKey?: string | null
  /** The screen this is the body of, under which this browser remembers the width its margin was drawn out to (v3.11). */
  screen?: ScreenId
}

/** Whether Esc belongs to the element it was pressed in: a field being typed in keeps it, as a dialog over it does. */
function keepsEscape(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false
  }
  const typing =
    target.isContentEditable ||
    target instanceof HTMLTextAreaElement ||
    target instanceof HTMLSelectElement ||
    (target instanceof HTMLInputElement && !['checkbox', 'radio', 'button'].includes(target.type))
  return typing || target.closest('[role="dialog"]') !== null
}

/**
 * The body of a working screen (the spec, section 08): the sheet, the only white area, and the paper margin beside it at
 * 340px, or 440px for a trace. Each scrolls on its own under the header, and Esc shuts it. Below 1200px the margin is a
 * drawer over the sheet, which the screen opens on what the reader asks for; Esc or its Close shuts it and gives the
 * focus back to what opened it. On a phone the margin is the next section of the page: a new row brings it into view,
 * and closing it takes the reader back to where they were (the spec, sections 08 and 10, v3.9). From 1200px the edge
 * between the sheet and the margin is a handle, which widens the margin as the sheet narrows (v3.11).
 */
export function SplitView({
  main,
  side,
  sideOpen,
  wide = false,
  fill = false,
  sideSheet = false,
  sideLabel,
  onCloseSide,
  closeButton = false,
  sideKey = null,
  screen,
}: SplitViewProps) {
  const open = sideOpen && side !== undefined && side !== null
  const layout = !open ? ' ws-body--single' : wide ? ' ws-body--wide-margin' : ''
  const drawer = useDrawer()
  const phone = usePhone()
  const beside = useWide()
  const bodyRef = useRef<HTMLDivElement>(null)
  const marginRef = useRef<HTMLElement>(null)
  const marginId = useId()
  const width = useMarginWidth(bodyRef, { beside: open && beside, wide, screen })
  // what had the focus when the margin opened, which has it again when the margin closes with the focus inside it
  const openerRef = useRef<HTMLElement | null>(null)
  const wasOpenRef = useRef(open)
  // on a phone, where the page was before the margin was brought into view
  const returnRef = useRef<number | null>(null)

  useEffect(() => {
    if (open && !wasOpenRef.current) {
      const active = document.activeElement
      openerRef.current = active instanceof HTMLElement && active !== document.body ? active : null
    }
    if (!open && wasOpenRef.current) {
      const active = document.activeElement
      if ((active === null || active === document.body) && openerRef.current?.isConnected) {
        openerRef.current.focus()
      }
      openerRef.current = null
    }
    wasOpenRef.current = open
  }, [open])

  useEffect(() => {
    if (!open || onCloseSide === undefined) {
      return undefined
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !event.defaultPrevented && !keepsEscape(event.target)) {
        onCloseSide()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onCloseSide])

  useEffect(() => {
    if (!phone || !open || sideKey === null) {
      return
    }
    returnRef.current ??= window.scrollY
    marginRef.current?.scrollIntoView({ block: 'start' })
  }, [phone, open, sideKey])

  useEffect(() => {
    if (phone && !open && returnRef.current !== null) {
      window.scrollTo({ top: returnRef.current })
      returnRef.current = null
    }
  }, [phone, open])

  return (
    <div
      ref={bodyRef}
      className={`ws-body${layout}${width.set === null ? '' : ' ws-body--set'}`}
      style={
        width.set === null
          ? undefined
          : ({ '--margin-set': `${String(width.set)}px` } as CSSProperties)
      }
    >
      {fill ? (
        <section className="sheet sheet--fill">{main}</section>
      ) : (
        <section className="sheet">
          <div className="sheet__scroll">{main}</div>
        </section>
      )}
      {width.handle ? (
        <div
          className={`ws-handle${width.handle.held ? ' ws-handle--held' : ''}`}
          role="separator"
          aria-orientation="vertical"
          aria-label="Width of the margin"
          aria-controls={marginId}
          aria-valuenow={width.handle.now}
          aria-valuemin={width.handle.own}
          aria-valuemax={width.handle.most}
          tabIndex={0}
          title="Drag to widen the margin; double-click to put it back"
          onPointerDown={width.handle.onPointerDown}
          onPointerMove={width.handle.onPointerMove}
          onPointerUp={width.handle.onPointerUp}
          onPointerCancel={width.handle.onPointerUp}
          onDoubleClick={width.handle.onDoubleClick}
          onKeyDown={width.handle.onKeyDown}
        />
      ) : null}
      {open ? (
        <aside
          ref={marginRef}
          id={marginId}
          className={`margin${sideSheet ? ' margin--sheet' : ''}`}
          aria-label={sideLabel}
        >
          {drawer && closeButton && onCloseSide ? (
            <div className="margin__close">
              <Button variant="quiet" size="sm" onClick={onCloseSide}>
                Close
              </Button>
            </div>
          ) : null}
          {side}
        </aside>
      ) : null}
    </div>
  )
}
