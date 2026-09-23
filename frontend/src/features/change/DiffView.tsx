import { createElement, type ReactNode } from 'react'
import type { Diff, DiffChange, FieldSchema, Rule, RuleAction } from '../../api/types'
import {
  contentAttributes,
  directionOfText,
  type ContentLanguage,
} from '../../shared/i18n/direction'
import { DecisionTag } from '../../shared/ui/StatusTag'
import { conditionText, literalText } from '../rules/cellGrammar'
import { actionText } from '../rules/tableModel'
import { changedAttributes, diffRows, diffSummary, type DiffRow } from './diffRows'
import './DiffView.css'

interface DiffViewProps {
  diff: Diff
  /** The rule set's language: its labels, reasons and quotes turn with it, the chrome does not. */
  language: ContentLanguage
  /** The rule set's fields, for the units of the numbers; without them a number is written bare. */
  fields?: FieldSchema[]
  /** What the two sides are called: "Version 1" and "Proposed", or two version numbers. */
  beforeLabel: string
  afterLabel: string
}

const KIND_LABELS: Record<DiffRow['kind'], string> = {
  added: 'Added',
  removed: 'Removed',
  modified: 'Modified',
}

/**
 * The side-by-side diff of two versions (Document 2, Frontend Architecture: "diff view (side by side, rule level)";
 * Document 3, Structural diff). One row per field, rule or the defaults: an added one only after, a removed one only
 * before, a modified one on both sides with its changed attributes struck out on the left and inserted on the right,
 * and under it each change by its JSON pointer, so `/condition/value 8,000 → 9,000` reads without the whole rule. It
 * renders the diff's JSON as the API or an audit entry gives it, and computes nothing of its own.
 */
export function DiffView({ diff, language, fields = [], beforeLabel, afterLabel }: DiffViewProps) {
  const rows = diffRows(diff)
  const units = new Map(fields.map((field) => [field.name, field]))
  if (rows.length === 0) {
    return (
      <p className="diff__same">{`${beforeLabel} and ${afterLabel} have the same rules, fields and defaults.`}</p>
    )
  }
  return (
    <section className="diff" aria-label={`Changes from ${beforeLabel} to ${afterLabel}`}>
      <p className="diff__summary">{diffSummary(rows)}</p>
      <div className="diff__scroll">
        <table className="diff__table">
          <thead>
            <tr>
              <th scope="col" className="diff__key-head">
                Item
              </th>
              <th scope="col">{beforeLabel}</th>
              <th scope="col">{afterLabel}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <RowOfDiff
                key={`${row.section}:${row.key}`}
                row={row}
                language={language}
                units={units}
                beforeLabel={beforeLabel}
                afterLabel={afterLabel}
              />
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

interface RowProps {
  row: DiffRow
  language: ContentLanguage
  units: Map<string, FieldSchema>
  beforeLabel: string
  afterLabel: string
}

function RowOfDiff({ row, language, units, beforeLabel, afterLabel }: RowProps) {
  const changed = changedAttributes(row.changes)
  const side = (item: unknown, mark: 'del' | 'ins', absentFrom: string) => {
    if (item === null) {
      return <span className="diff__absent">Not in {absentFrom}</span>
    }
    // an added or removed item, and the defaults (compared whole), are marked whole; a modified one by attribute
    const whole = row.kind !== 'modified' || row.section === 'defaults'
    const content = (
      <ItemOfDiff
        row={row}
        item={item}
        mark={whole ? null : mark}
        changed={changed}
        language={language}
        units={units}
      />
    )
    return whole
      ? createElement(mark, { className: 'diff__mark diff__mark--whole' }, content)
      : content
  }
  return (
    <>
      <tr className={`diff__row diff__row--${row.kind}`}>
        <th scope="row" className="diff__key">
          <span className={row.section === 'defaults' ? undefined : 'mono'}>
            {row.section === 'defaults' ? 'Defaults' : row.key}
          </span>{' '}
          <span className="diff__kind">{KIND_LABELS[row.kind]}</span>
        </th>
        <td className="diff__side">{side(row.before, 'del', beforeLabel)}</td>
        <td className="diff__side">{side(row.after, 'ins', afterLabel)}</td>
      </tr>
      {row.changes.length > 0 ? (
        <tr className="diff__changes-row">
          <td colSpan={3}>
            <ul className="diff__changes" aria-label={`What changed in ${row.key}`}>
              {row.changes.map((change) => (
                <li key={change.path}>
                  <code className="diff__pointer">{change.path}</code>{' '}
                  <ChangeValue change={change} />
                </li>
              ))}
            </ul>
          </td>
        </tr>
      ) : null}
    </>
  )
}

interface ItemProps {
  row: DiffRow
  item: unknown
  /** The element a changed attribute is wrapped in on this side, or null when the side is marked whole. */
  mark: 'del' | 'ins' | null
  changed: Set<string>
  language: ContentLanguage
  units: Map<string, FieldSchema>
}

function ItemOfDiff({ row, item, mark, changed, language, units }: ItemProps) {
  const content = contentAttributes(language)
  const marked = (attribute: string, value: ReactNode): ReactNode =>
    mark !== null && changed.has(attribute)
      ? createElement(mark, { className: 'diff__mark' }, value)
      : value
  if (row.section === 'defaults') {
    const defaults = item as { outcome: 'approve' | 'reject' | 'refer'; reason: string }
    return (
      <dl className="diff__item">
        <div className="diff__attribute">
          <dt>Outcome</dt>
          <dd>
            <DecisionTag status={defaults.outcome} quiet />
          </dd>
        </div>
        <div className="diff__attribute">
          <dt>Reason</dt>
          <dd>
            <span {...content}>{defaults.reason}</span>
          </dd>
        </div>
      </dl>
    )
  }
  if (row.section === 'field') {
    const field = item as FieldSchema
    return (
      <dl className="diff__item">
        {(
          [
            ['type', field.type],
            ['unit', field.unit],
            ['required', field.required === undefined ? undefined : String(field.required)],
            ['derived', field.derived === undefined ? undefined : String(field.derived)],
            ['minimum', field.minimum === undefined ? undefined : literalText(field.minimum)],
            ['maximum', field.maximum === undefined ? undefined : literalText(field.maximum)],
            ['values', field.values?.join(', ')],
          ] as const
        )
          .filter(([, value]) => value !== undefined)
          .map(([attribute, value]) => (
            <div key={attribute} className="diff__attribute">
              <dt>{attribute}</dt>
              <dd>{marked(attribute, <span className="mono">{value}</span>)}</dd>
            </div>
          ))}
      </dl>
    )
  }
  const rule = item as Rule
  return (
    <dl className="diff__item">
      <div className="diff__attribute">
        <dt>Label</dt>
        <dd>{marked('label', <span {...content}>{rule.label}</span>)}</dd>
      </div>
      <div className="diff__attribute">
        <dt>Priority</dt>
        <dd className="tabular">{marked('priority', String(rule.priority))}</dd>
      </div>
      {rule.enabled === false ? (
        <div className="diff__attribute">
          <dt>Enabled</dt>
          <dd>{marked('enabled', 'No')}</dd>
        </div>
      ) : null}
      <div className="diff__attribute">
        <dt>Condition</dt>
        <dd>
          {marked(
            'condition',
            <span dir="ltr" className="mono">
              {conditionText(rule.condition, units)}
            </span>,
          )}
        </dd>
      </div>
      <div className="diff__attribute">
        <dt>Action</dt>
        <dd>{marked('actions', <ActionsOf rule={rule} language={language} />)}</dd>
      </div>
      <div className="diff__attribute">
        <dt>Source</dt>
        <dd>{marked('provenance', <SourceOf rule={rule} language={language} />)}</dd>
      </div>
    </dl>
  )
}

/** What the rule does, and the reason or message it gives, in the policy's language. */
function ActionsOf({ rule, language }: { rule: Rule; language: ContentLanguage }) {
  const said = rule.actions
    .map((action: RuleAction) => action.reason ?? action.message)
    .filter((text): text is string => text !== undefined)
  return (
    <span className="diff__actions">
      <span>{actionText(rule)}</span>
      {said.map((text) => (
        <span key={text} className="diff__said" {...contentAttributes(language)}>
          {text}
        </span>
      ))}
    </span>
  )
}

/** Where the rule comes from (Document 3, Provenance): the paragraph and its quote, the analyst, or pending. */
function SourceOf({ rule, language }: { rule: Rule; language: ContentLanguage }) {
  const provenance = rule.provenance
  const content = contentAttributes(language)
  switch (provenance.kind) {
    case 'quoted':
      return (
        <span className="diff__source">
          <span className="tabular">{`¶ ${provenance.paragraph}`}</span>
          <span className="diff__said" {...content}>
            {provenance.quote}
          </span>
        </span>
      )
    case 'analyst':
      return (
        <span className="diff__source">
          <span>
            Analyst <span className="mono">{provenance.actor}</span>
          </span>
          <span className="diff__said" {...content}>
            {provenance.note}
          </span>
        </span>
      )
    case 'pending':
      return (
        <span className="diff__source">
          <span>Pending approval</span>
          <span className="diff__said" {...content}>
            {provenance.rationale}
          </span>
        </span>
      )
  }
}

/** A change's two values: a number grouped, a word or a sentence isolated, anything larger said to be replaced. */
function ChangeValue({ change }: { change: DiffChange }) {
  const scalar = (value: unknown) =>
    value === null || value === undefined || ['number', 'string', 'boolean'].includes(typeof value)
  if (!scalar(change.from) || !scalar(change.to)) {
    return <span className="diff__whole">replaced as a whole</span>
  }
  return (
    <>
      <ScalarValue value={change.from} /> → <ScalarValue value={change.to} />
    </>
  )
}

function ScalarValue({ value }: { value: unknown }) {
  if (value === null || value === undefined) {
    return <span className="diff__none">none</span>
  }
  if (typeof value !== 'string') {
    return <span className="tabular">{literalText(value)}</span>
  }
  // a Hebrew label keeps its own order between the arrows of a left-to-right line; an enum value stays as it is
  const dir = directionOfText(value)
  return (
    <bdi dir={dir} lang={dir === 'rtl' ? 'he' : undefined}>
      {value}
    </bdi>
  )
}
