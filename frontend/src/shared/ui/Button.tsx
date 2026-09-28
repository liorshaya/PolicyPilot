import type { ButtonHTMLAttributes, MouseEvent, ReactNode } from 'react'
import { Icon, type IconName } from './Icon'
import './Button.css'

export type ButtonVariant = 'primary' | 'secondary' | 'quiet' | 'danger' | 'link'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  /** 28px inside toolbars, table rows and findings; 36px for the one primary action of a workspace header. */
  size?: 'sm' | 'md' | 'lg'
  /** Working: the spinner stands in for the label, which stays to keep the width, and a click does nothing. */
  busy?: boolean
  /** A glyph only where it adds meaning (add, open externally); never on Run, Publish or Ask. */
  icon?: IconName
  children: ReactNode
}

/** The verbs that never carry a glyph (the spec, section 05). */
const PLAIN_VERBS = /^(Run|Publish|Ask)\b/

/**
 * The control of the Register (the spec, section 05): one primary per screen in ink on paper, paper on ink in the dark
 * theme; a bordered secondary; a quiet one of text alone; a danger in red text; and a link, for an action the spec
 * writes in running text as a link. Its label is a verb, with the object when the verb alone is ambiguous.
 */
export function Button({
  variant = 'secondary',
  size = 'md',
  busy = false,
  icon,
  children,
  className,
  onClick,
  type = 'button',
  ...rest
}: ButtonProps) {
  const classes = [
    'btn',
    `btn--${variant}`,
    size === 'md' ? '' : `btn--${size}`,
    busy ? 'btn--busy' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ')
  const glyph = icon !== undefined && !(typeof children === 'string' && PLAIN_VERBS.test(children))
  return (
    <button
      {...rest}
      type={type}
      className={classes}
      aria-busy={busy || undefined}
      onClick={(event: MouseEvent<HTMLButtonElement>) => {
        if (!busy) {
          onClick?.(event)
        }
      }}
    >
      {glyph ? <Icon name={icon} /> : null}
      {children}
    </button>
  )
}
