import type { ReactNode } from 'react'
import { Icon } from './Icon'
import './Chip.css'

type ChipKind = 'id' | 'para' | 'tool' | 'field'

interface ChipProps {
  /** An identifier (the default), a paragraph of the policy, a tool call, or a field name inside prose. */
  kind?: ChipKind
  /** The identifier, the paragraph's number, the tool call or the field name. */
  children: ReactNode
  /** A chip that opens something is a link to it... */
  href?: string
  /** ...or a button that opens it. */
  onClick?: () => void
  /** The chip of what is open or selected. */
  active?: boolean
  /** What hovering it shows, such as the text it cites. */
  title?: string
  /** What a screen reader names a chip that opens something, when its marks alone do not: "Paragraph 8" for ¶ 8. */
  label?: string
  /** An identifier a change considered and left as it was, which the change screen draws in ink-3 (section 09). */
  unchanged?: boolean
}

const NO_BREAK_SPACE = String.fromCodePoint(0x00a0)

/**
 * Three silhouettes (the spec, section 06): a mono rectangle is any identifier, a serif pill is a paragraph of the policy,
 * an outlined glyph is a tool call. A chip that opens something is focusable: a link or a button, never a bare span.
 */
export function Chip({
  kind = 'id',
  children,
  href,
  onClick,
  active = false,
  title,
  label,
  unchanged = false,
}: ChipProps) {
  const className = `chip chip--${kind}${active ? ' chip--active' : ''}`
  const content =
    kind === 'para' ? (
      <>
        <span className="pilcrow">¶</span>
        {NO_BREAK_SPACE}
        {children}
      </>
    ) : kind === 'tool' ? (
      <>
        <Icon name="flask" />
        {children}
      </>
    ) : (
      children
    )
  const current = active ? 'true' : undefined
  const state = unchanged ? 'unchanged' : undefined
  if (href !== undefined) {
    return (
      <a
        className={className}
        href={href}
        title={title}
        aria-label={label}
        aria-current={current}
        data-state={state}
      >
        {content}
      </a>
    )
  }
  if (onClick !== undefined) {
    return (
      <button
        type="button"
        className={className}
        onClick={onClick}
        title={title}
        aria-label={label}
        aria-current={current}
        data-state={state}
      >
        {content}
      </button>
    )
  }
  return (
    <span className={className} title={title} data-state={state}>
      {content}
    </span>
  )
}
