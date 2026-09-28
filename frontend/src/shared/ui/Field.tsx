import type { ReactNode } from 'react'
import { Icon } from './Icon'
import './Field.css'

interface FieldProps {
  /** The label stays above the control and never becomes a placeholder; a case's field puts its name under it. */
  label: ReactNode
  htmlFor: string
  /** A short line of guidance under the control. */
  hint?: ReactNode
  /** The problem with this field, named exactly; it stands in place of the hint. */
  error?: ReactNode
  /** The characters used against the limit the API states, always shown, amber from 90% of it. */
  counter?: { value: number; max: number }
  children: ReactNode
}

const COUNT = new Intl.NumberFormat('en-US')

/** A labelled control with its guidance, its error and its counter, wired for a screen reader (the spec, section 05). */
export function Field({ label, htmlFor, hint, error, counter, children }: FieldProps) {
  const near = counter !== undefined && counter.value >= 0.9 * counter.max
  return (
    <div className="field">
      <label className="field__label" htmlFor={htmlFor}>
        {label}
      </label>
      {children}
      {error ? (
        <p className="field__error" id={`${htmlFor}-error`} role="alert">
          <Icon name="warn" />
          {error}
        </p>
      ) : hint || counter ? (
        <div className="field__foot">
          {hint ? (
            <p className="field__hint" id={`${htmlFor}-hint`}>
              {hint}
            </p>
          ) : (
            <span />
          )}
          {counter ? (
            <span className={near ? 'counter counter--near' : 'counter'}>
              {`${COUNT.format(counter.value)} / ${COUNT.format(counter.max)}`}
            </span>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
