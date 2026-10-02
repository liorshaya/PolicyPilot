import { useId, useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../../api/client'
import { useDecideCase } from '../../api/queries'
import type { Decision, FieldSchema } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { Button } from '../../shared/ui/Button'
import { Field } from '../../shared/ui/Field'
import { Refusal } from '../../shared/ui/Refusal'
import { fieldSummary } from '../rules/fieldSchema'
import { caseOf, fieldOfPointer, problemText } from './caseInput'
import './CaseForm.css'

interface CaseFormProps {
  /** The version's fields, every one; a derived field is the engine's to compute and is never asked for. */
  fields: FieldSchema[]
  /** The rule set's language, which its fields' descriptions are written in. */
  language: ContentLanguage
  /** The published version that decides the case. */
  version: { id: string; versionNo: number }
  /** The decision, recorded like any other, whose trace the screen opens next. */
  onDecided: (decision: Decision) => void
  onClose: () => void
}

/**
 * Decide a case, the credit officer's form (the spec, section 09, "Decide a case, and the field schema"): one field per
 * line in the schema's order, the field's description first and its name with its type, unit and domain under it, an
 * enum as a select and a boolean as a checkbox with its sentence. Decide sends the case to the published version
 * (Document 2, decide), and the engine decides it; a case the schema refuses is refused whole (CASE_INVALID), each
 * field named under its input, and nothing is stored.
 */
export function CaseForm({ fields, language, version, onDecided, onClose }: CaseFormProps) {
  const titleId = useId()
  const formId = useId()
  const decide = useDecideCase(version)
  const [values, setValues] = useState<Record<string, string | boolean>>({})
  const asked = fields.filter((field) => field.derived !== true)
  const refusal = decide.error instanceof ApiError ? decide.error : null
  // a refused case names its fields by pointer: each one shown stands under its input, any other in the refusal
  const named = new Map<string, string>()
  const unnamed: { pointer: string; problem: ReactNode }[] = []
  for (const detail of refusal?.code === 'CASE_INVALID' ? refusal.details : []) {
    const name = fieldOfPointer(detail)
    const field = asked.find((one) => one.name === name && one.type !== 'boolean')
    if (field) {
      named.set(field.name, detail.problem)
    } else {
      unnamed.push({ pointer: detail.path, problem: detail.problem })
    }
  }
  const set = (name: string, value: string | boolean) =>
    setValues((current) => ({ ...current, [name]: value }))

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    decide.mutate(caseOf(fields, values), { onSuccess: onDecided })
  }

  return (
    <section className="margin__section" aria-labelledby={titleId}>
      <div className="margin__title">
        <span id={titleId}>Decide a case</span>
        <Button variant="quiet" size="sm" onClick={onClose}>
          Close
        </Button>
      </div>
      <form className="case-form" onSubmit={submit} noValidate>
        {asked.map((field) => {
          const id = `${formId}-${field.name}`
          const description = (
            <bdi className="he-label" {...contentAttributes(language)}>
              {field.description ?? field.name}
            </bdi>
          )
          if (field.type === 'boolean') {
            return (
              <label key={field.name} className="check">
                <input
                  id={id}
                  type="checkbox"
                  checked={values[field.name] === true}
                  onChange={(event) => set(field.name, event.target.checked)}
                />{' '}
                {description} <span className="mono muted">{field.name}</span>
              </label>
            )
          }
          const problem = named.get(field.name)
          const label = (
            <>
              {description}
              <span className="field__id">
                <span className="mono">{field.name}</span>
                {` · ${fieldSummary(field)}`}
              </span>
            </>
          )
          const error =
            problem === undefined ? undefined : (
              <>
                {problemText(problem, field)} <span className="mono">{problem}</span>
              </>
            )
          return (
            <Field key={field.name} label={label} htmlFor={id} error={error}>
              {field.type === 'enum' ? (
                <select
                  id={id}
                  className="select"
                  value={typeof values[field.name] === 'string' ? String(values[field.name]) : ''}
                  onChange={(event) => set(field.name, event.target.value)}
                  aria-invalid={error ? true : undefined}
                >
                  <option value="" />
                  {(field.values ?? []).map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  id={id}
                  className="input input--mono"
                  // a phone's keypad: the decimal point for a number, the digits for an integer (v3.9)
                  inputMode={
                    field.type === 'number'
                      ? 'decimal'
                      : field.type === 'integer'
                        ? 'numeric'
                        : undefined
                  }
                  value={typeof values[field.name] === 'string' ? String(values[field.name]) : ''}
                  onChange={(event) => set(field.name, event.target.value)}
                  aria-invalid={error ? true : undefined}
                />
              )}
            </Field>
          )
        })}
        {refusal ? (
          <Refusal
            code={refusal.code}
            title={
              refusal.code === 'CASE_INVALID'
                ? 'The case was refused whole.'
                : 'The case was not decided.'
            }
            rows={unnamed}
          />
        ) : null}
        <div className="case-form__foot">
          <span className="muted">
            Decided by <b>{`v${String(version.versionNo)}`}</b>, the published version, and recorded
            like any other decision.
          </span>
          <Button type="submit" variant="primary" busy={decide.isPending}>
            Decide
          </Button>
        </div>
      </form>
    </section>
  )
}
