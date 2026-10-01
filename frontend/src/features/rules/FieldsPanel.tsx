import { useId } from 'react'
import type { Finding, RuleSetDocument } from '../../api/types'
import { contentAttributes } from '../../shared/i18n/direction'
import { Chip } from '../../shared/ui/Chip'
import { Note } from '../../shared/ui/Note'
import '../../shared/ui/Field.css'
import '../../shared/ui/Severity.css'
import { derivedBy, fieldSummary } from './fieldSchema'
import './FieldsPanel.css'

interface FieldsPanelProps {
  document: RuleSetDocument
  /** The validator's findings on the version, of which FIELD_UNUSED names a field no rule reads. */
  findings: Finding[]
  /** Opens a paragraph of the policy in the margin's Policy view. */
  onShowParagraph: (index: number) => void
  /**
   * Marks a case field required or optional again, on a draft only (the spec, section 09, v3.8); absent on a published
   * or seeded version, which nobody edits.
   */
  onRequire?: (field: string, required: boolean) => void
}

/** A case field the analyst may require: one the case supplies (not derived) with no default to stand in for it. */
function requirable(field: RuleSetDocument['fields'][number]): boolean {
  return field.derived !== true && field.default === undefined
}

/** The note over a draft's fields while any of them may be left out (the spec, section 09, v3.8). */
function optionalNote(count: number): string {
  return count === 1
    ? '1 case field is optional and has no default: a case without it is not refused, and every comparison on it reads false.'
    : `${String(count)} case fields are optional and have no default: a case without one is not refused, and every comparison on it reads false.`
}

/**
 * Fields, the analyst's view of the schema the version declares (the spec, section 09, "Decide a case, and the field
 * schema"): every field's name with its type, unit and presence, its meaning in the policy's language, the values of an
 * enum, and the paragraph that implied it, which is where a field the policy never implies would show; the validator's
 * FIELD_UNUSED stands beside a field no rule reads (Document 3). A derived field says which rule derives it. On a draft
 * a case field with no default carries Required, because an optional one that a case leaves out makes every comparison
 * on it false (Document 3, Missing values), so an approving rule decides a case that lacks what the policy asks for.
 */
export function FieldsPanel({ document, findings, onShowParagraph, onRequire }: FieldsPanelProps) {
  const titleId = useId()
  const hintId = useId()
  const derived = document.fields.filter((field) => field.derived === true).length
  const supplied = document.fields.length - derived
  const optional = document.fields.filter(
    (field) => requirable(field) && field.required !== true,
  ).length
  return (
    <section className="margin__section" aria-labelledby={titleId}>
      <div className="margin__title">
        <span id={titleId}>Fields</span>{' '}
        <span className="quiet">{`${String(supplied)} case field${supplied === 1 ? '' : 's'}, ${String(derived)} derived`}</span>
      </div>
      {onRequire !== undefined && optional > 0 ? <Note>{optionalNote(optional)}</Note> : null}
      {onRequire !== undefined && document.fields.some(requirable) ? (
        <p className="field__hint" id={hintId}>
          A case without it is refused; unchecked, every comparison on it reads false
        </p>
      ) : null}
      <div className="schema">
        {document.fields.map((field) => {
          const unused = findings.find(
            (finding) => finding.code === 'FIELD_UNUSED' && finding.fieldNames.includes(field.name),
          )
          return (
            <div key={field.name} className="schema__row">
              <div className="schema__head">
                <span className="mono">{field.name}</span>
                <span className="muted">
                  {fieldSummary(
                    field,
                    field.derived === true ? derivedBy(document, field.name) : undefined,
                  )}
                </span>
                {field.derived === true ? <span className="derived-tag">derived</span> : null}
                {field.source ? (
                  <Chip
                    kind="para"
                    label={`Paragraph ${String(field.source.paragraph)}`}
                    onClick={() => onShowParagraph(field.source!.paragraph)}
                  >
                    {field.source.paragraph}
                  </Chip>
                ) : null}
                {unused ? (
                  <span className="sev sev--warning" title={unused.message}>
                    FIELD_UNUSED
                  </span>
                ) : null}
              </div>
              {field.description === undefined ? null : (
                <bdi className="he-label" {...contentAttributes(document.language)}>
                  {field.description}
                </bdi>
              )}
              {field.type === 'enum' && field.values !== undefined ? (
                <span className="mono muted schema__values">{field.values.join(' · ')}</span>
              ) : null}
              {onRequire !== undefined && requirable(field) ? (
                <label className="check">
                  <input
                    type="checkbox"
                    checked={field.required === true}
                    onChange={(event) => onRequire(field.name, event.target.checked)}
                    aria-describedby={hintId}
                  />
                  Required
                </label>
              ) : null}
            </div>
          )
        })}
      </div>
    </section>
  )
}
