import type { ButtonHTMLAttributes, ReactNode } from 'react'
import './Button.css'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  /** A taller control for the one action a screen is about. */
  size?: 'md' | 'lg'
  /** Shows the control as working; the button stays in the page and keeps its width. */
  loading?: boolean
  /** Optional leading icon; used only where it helps to recognise the action. */
  icon?: ReactNode
  children: ReactNode
}

/**
 * The one button of the interface (the brief's control system): primary is the blue action, secondary is a bordered
 * white action, ghost is a marginal action, danger is visibly separate. Every state is visible: hover, focus,
 * active, disabled and loading.
 */
export function Button({
  variant = 'secondary',
  size = 'md',
  loading = false,
  icon,
  children,
  className,
  disabled,
  type = 'button',
  ...rest
}: ButtonProps) {
  const classes = ['button', `button--${variant}`, `button--${size}`, className]
    .filter(Boolean)
    .join(' ')
  return (
    <button
      {...rest}
      type={type}
      className={classes}
      disabled={disabled === true || loading}
      aria-busy={loading || undefined}
    >
      {loading ? <span className="button__spinner" aria-hidden="true" /> : icon}
      <span>{children}</span>
    </button>
  )
}
