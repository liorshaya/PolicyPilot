import type { ReactNode } from 'react'
import './Field.css'

interface FieldProps {
  /** The label stays above the control and never becomes a placeholder (the brief's form rules). */
  label: string
  htmlFor: string
  /** A short line of guidance under the label. */
  hint?: ReactNode
  /** The problem with this field, named exactly; it replaces the hint while it stands. */
  error?: ReactNode
  children: ReactNode
}

/** A labelled control with its guidance and its error, wired for a screen reader. */
export function Field({ label, htmlFor, hint, error, children }: FieldProps) {
  return (
    <div className="field">
      <label className="field__label" htmlFor={htmlFor}>
        {label}
      </label>
      {children}
      {error ? (
        <p className="field__error" id={`${htmlFor}-error`} role="alert">
          {error}
        </p>
      ) : hint ? (
        <p className="field__hint" id={`${htmlFor}-hint`}>
          {hint}
        </p>
      ) : null}
    </div>
  )
}
