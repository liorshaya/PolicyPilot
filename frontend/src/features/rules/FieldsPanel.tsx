import { useId } from 'react'
import type { Finding, RuleSetDocument } from '../../api/types'
import { contentAttributes } from '../../shared/i18n/direction'
import { Chip } from '../../shared/ui/Chip'
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
}

/**
 * Fields, the analyst's view of the schema the version declares (the spec, section 09, "Decide a case, and the field
 * schema"): every field's name with its type, unit and presence, its meaning in the policy's language, the values of an
 * enum, and the paragraph that implied it, which is where a field the policy never implies would show; the validator's
 * FIELD_UNUSED stands beside a field no rule reads (Document 3). A derived field says which rule derives it.
 */
export function FieldsPanel({ document, findings, onShowParagraph }: FieldsPanelProps) {
  const titleId = useId()
  const derived = document.fields.filter((field) => field.derived === true).length
  const supplied = document.fields.length - derived
  return (
    <section className="margin__section" aria-labelledby={titleId}>
      <div className="margin__title">
        <span id={titleId}>Fields</span>{' '}
        <span className="quiet">{`${String(supplied)} case field${supplied === 1 ? '' : 's'}, ${String(derived)} derived`}</span>
      </div>
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
            </div>
          )
        })}
      </div>
    </section>
  )
}
