import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { Chip } from './Chip'
import { Icon } from './Icon'
import { Kbd } from './Kbd'
import { findIn, PALETTE_HINT, WHAT, type PaletteItem } from './paletteQuery'
import './Palette.css'

interface PaletteProps<Target> {
  /** What the palette can reach, as the screens have read it. */
  items: PaletteItem<Target>[]
  /** Whether this session has run the cases: before a run, a number reaches no case (the owner's answer). */
  ran: boolean
  onOpen: (item: PaletteItem<Target>) => void
  onClose: () => void
}

/**
 * The command palette (the spec, section 08, "Go to anything"): an input, then what it names grouped by kind, each row
 * the object's own chip, its state and what opening does. The arrow keys move the selection and come round at the
 * ends, Enter opens it, a click opens its row; Escape or a click outside closes it, and the focus goes back where it
 * was.
 */
export function Palette<Target>({ items, ran, onOpen, onClose }: PaletteProps<Target>) {
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState(0)
  const boxRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const returnRef = useRef(document.activeElement)
  const id = useId()
  const listId = `${id}-list`
  const rowId = (index: number) => `${id}-row-${String(index)}`

  // the latest onClose, so the listeners set once on opening call the one the parent passes now
  const closeRef = useRef(onClose)
  useEffect(() => {
    closeRef.current = onClose
  }, [onClose])

  const found = findIn(query, items, { ran })
  const groups = found.kind === 'groups' ? found.groups : []
  const listed = groups.filter((group) => group.items.length > 0)
  const rows = listed.flatMap((group) => group.items)
  // where each group's rows start in the one selection that runs through them all
  const starts = listed.map((_, at) =>
    listed.slice(0, at).reduce((count, group) => count + group.items.length, 0),
  )
  const at = rows.length === 0 ? -1 : Math.min(selected, rows.length - 1)

  useEffect(() => {
    inputRef.current?.focus()
    const back = returnRef.current
    const onPointer = (event: PointerEvent) => {
      if (!boxRef.current?.contains(event.target as Node)) {
        closeRef.current()
      }
    }
    document.addEventListener('pointerdown', onPointer)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      if (back instanceof HTMLElement && back.isConnected) {
        back.focus()
      }
    }
  }, [])

  function open(item: PaletteItem<Target>) {
    onOpen(item)
    onClose()
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      if (rows.length > 0) {
        const step = event.key === 'ArrowDown' ? 1 : -1
        setSelected((at + step + rows.length) % rows.length)
      }
    } else if (event.key === 'Enter') {
      event.preventDefault()
      const row = rows[at]
      if (row !== undefined) {
        open(row)
      }
    } else if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
    }
  }

  return (
    <div className="palette-layer">
      <div ref={boxRef} className="palette" role="dialog" aria-label="Go to">
        <div className="palette__input">
          <Icon name="search" />
          <input
            ref={inputRef}
            role="combobox"
            aria-label="Go to"
            aria-expanded={rows.length > 0}
            aria-controls={listId}
            aria-activedescendant={at < 0 ? undefined : rowId(at)}
            aria-autocomplete="list"
            autoComplete="off"
            spellCheck={false}
            value={query}
            onChange={(event) => {
              setQuery(event.target.value)
              setSelected(0)
            }}
            onKeyDown={onKeyDown}
          />
          <Kbd>Esc</Kbd>
        </div>
        {found.kind === 'hint' ? <p className="palette__note">{PALETTE_HINT}</p> : null}
        {found.kind === 'none' ? (
          <p className="palette__note">{`Nothing matches ${found.query}.`}</p>
        ) : null}
        {groups
          .filter((group) => group.note !== undefined)
          .map((group) => (
            <p key={group.title} className="palette__note">
              {group.note}
            </p>
          ))}
        <div className="palette__list" id={listId} role="listbox" aria-label="Matches">
          {listed.map((group, groupAt) => (
            <div key={group.title} role="group" aria-labelledby={`${id}-group-${String(groupAt)}`}>
              <div className="palette__group" id={`${id}-group-${String(groupAt)}`}>
                {group.title}
              </div>
              {group.items.map((item, itemAt) => {
                const position = starts[groupAt]! + itemAt
                return (
                  <div
                    key={`${item.kind}:${item.code}`}
                    id={rowId(position)}
                    className="palette__row"
                    role="option"
                    aria-selected={position === at}
                    onMouseDown={(event) => event.preventDefault()}
                    onMouseMove={() => setSelected(position)}
                    onClick={() => open(item)}
                  >
                    <Chip kind={item.kind === 'paragraph' ? 'para' : 'id'}>{item.code}</Chip>
                    {item.state}
                    <span className="what">{WHAT[item.kind]}</span>
                  </div>
                )
              })}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
