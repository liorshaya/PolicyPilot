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
}

const NO_BREAK_SPACE = String.fromCodePoint(0x00a0)

/**
 * Three silhouettes (the spec, section 06): a mono rectangle is any identifier, a serif pill is a paragraph of the policy,
 * an outlined glyph is a tool call. A chip that opens something is focusable: a link or a button, never a bare span.
 */
export function Chip({ kind = 'id', children, href, onClick, active = false, title }: ChipProps) {
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
  if (href !== undefined) {
    return (
      <a className={className} href={href} title={title} aria-current={current}>
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
        aria-current={current}
      >
        {content}
      </button>
    )
  }
  return (
    <span className={className} title={title}>
      {content}
    </span>
  )
}
