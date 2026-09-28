import { createElement, useState, type ReactNode } from 'react'
import type { Diff, DiffChange, FieldSchema, Provenance, Rule, RuleAction } from '../../api/types'
import {
  contentAttributes,
  directionOfText,
  isolated,
  type ContentLanguage,
} from '../../shared/i18n/direction'
import { Button } from '../../shared/ui/Button'
import { Chip } from '../../shared/ui/Chip'
import { DecisionTag } from '../../shared/ui/StatusTag'
import { conditionText, literalText } from '../rules/cellGrammar'
import { actionsText } from '../rules/tableModel'
import {
  changedAttributes,
  diffRows,
  diffSummary,
  labelChange,
  SIGNS,
  unifiedRows,
  valueText,
  type DiffRow,
  type LabelSide,
  type UnifiedRow,
} from './diffRows'
import '../../shared/ui/Table.css'
import './DiffView.css'

interface DiffViewProps {
  diff: Diff
  /** The rule set's language: its labels, reasons and quotes turn with it, the chrome does not. */
  language: ContentLanguage
  /** The rule set's fields, for the units of the numbers; without them a number is written bare. */
  fields?: FieldSchema[]
  /** The earlier version's rules, so the rules that did not change collapse into one row with their count. */
  rules?: Rule[]
  /** What the two sides are called: "Published v1" and "Proposed", or two version numbers. */
  beforeLabel: string
  afterLabel: string
}

type View = 'unified' | 'side-by-side'

/** The view the reader chose, remembered in this browser (the spec, section 09: "remembered per person"). */
const VIEW_KEY = 'pp-diff-view'

function storedView(): View {
  try {
    return localStorage.getItem(VIEW_KEY) === 'side-by-side' ? 'side-by-side' : 'unified'
  } catch {
    // a browser that refuses its storage starts on the unified view
    return 'unified'
  }
}

function rememberView(view: View): void {
  try {
    localStorage.setItem(VIEW_KEY, view)
  } catch {
    // the choice then lasts as long as the page
  }
}

const KIND_LABELS: Record<DiffRow['kind'], string> = {
  added: 'Added',
  removed: 'Removed',
  modified: 'Modified',
}

/**
 * The diff of two versions (the spec, section 09, "The change request": "one row per changed cell (rule · field ·
 * before → after) with the changed value tinted, the whole rule struck when removed; unchanged rules collapse;
 * side-by-side is the alternative, remembered per person"; Document 3, Structural diff). It renders the diff's JSON as
 * the API or an audit entry gives it, and computes nothing of its own beyond laying it out.
 */
export function DiffView({
  diff,
  language,
  fields = [],
  rules,
  beforeLabel,
  afterLabel,
}: DiffViewProps) {
  const [view, setView] = useState<View>(storedView)
  const rows = diffRows(diff)
  if (rows.length === 0) {
    return (
      <p className="diff__same">{`${beforeLabel} and ${afterLabel} have the same rules, fields and defaults.`}</p>
    )
  }
  const choose = (next: View) => {
    setView(next)
    rememberView(next)
  }
  return (
    <section className="diff" aria-label={`Changes from ${beforeLabel} to ${afterLabel}`}>
      {view === 'unified' ? (
        <Unified
          diff={diff}
          language={language}
          rules={rules}
          beforeLabel={beforeLabel}
          afterLabel={afterLabel}
          onSideBySide={() => choose('side-by-side')}
        />
      ) : (
        <SideBySide
          rows={rows}
          language={language}
          fields={fields}
          beforeLabel={beforeLabel}
          afterLabel={afterLabel}
          onUnified={() => choose('unified')}
        />
      )}
    </section>
  )
}

/** The unified view: a row per changed cell, then the rules that did not change, collapsed to their count. */
function Unified({
  diff,
  language,
  rules,
  beforeLabel,
  afterLabel,
  onSideBySide,
}: {
  diff: Diff
  language: ContentLanguage
  rules?: Rule[]
  beforeLabel: string
  afterLabel: string
  onSideBySide: () => void
}) {
  const [showUnchanged, setShowUnchanged] = useState(false)
  const changed = new Set([
    ...diff.rules.modified.map((rule) => rule.id),
    ...diff.rules.removed.map((rule) => (rule as Rule).id),
  ])
  const unchanged = (rules ?? []).filter((rule) => !changed.has(rule.id))
  return (
    <div className="table-scroll">
      <div className="udiff">
        <div className="udiff__row udiff__row--head">
          <span>Rule</span>
          <span>Field</span>
          <span>{beforeLabel}</span>
          <span />
          <span>{afterLabel}</span>
        </div>
        {unifiedRows(diff).map((row) => (
          <UnifiedRowView
            key={`${row.key}:${row.field}:${row.kind}`}
            row={row}
            language={language}
          />
        ))}
        {showUnchanged
          ? unchanged.map((rule) => (
              <div key={rule.id} className="udiff__row udiff__row--he">
                <Chip>{rule.id}</Chip>
                <span className="udiff__kind">unchanged</span>
                <span className="step__label" {...contentAttributes(language)}>
                  {isolated(rule.label, language)}
                </span>
              </div>
            ))
          : null}
        <div className="udiff__row udiff__row--collapsed">
          {unchanged.length > 0 ? (
            <>
              {`${String(unchanged.length)} unchanged rule${unchanged.length === 1 ? '' : 's'}`} ·{' '}
              <Button variant="link" onClick={() => setShowUnchanged(!showUnchanged)}>
                {showUnchanged ? 'Hide' : 'Show'}
              </Button>{' '}
              ·{' '}
            </>
          ) : null}
          <Button variant="link" onClick={onSideBySide}>
            Side by side
          </Button>
        </div>
      </div>
    </div>
  )
}

/** One changed cell: the rule or the field, what changed in it, and its two values with the change tinted. */
function UnifiedRowView({ row, language }: { row: UnifiedRow; language: ContentLanguage }) {
  const who =
    row.section === 'rule' ? (
      <Chip>{row.key}</Chip>
    ) : row.section === 'field' ? (
      <Chip kind="field">{row.key}</Chip>
    ) : (
      <span>Defaults</span>
    )
  if (row.kind === 'added' || row.kind === 'removed') {
    const item = (row.kind === 'added' ? row.after : row.before) as Rule | FieldSchema
    const words =
      row.section === 'rule' ? (
        <span className="step__label" {...contentAttributes(language)}>
          {isolated((item as Rule).label, language)}
        </span>
      ) : (
        <span className="mono">{(item as FieldSchema).type}</span>
      )
    return (
      <div
        className={`udiff__row udiff__row--he${row.kind === 'removed' ? ' udiff__row--removed' : ''}`}
      >
        {who}
        <span className="udiff__kind">{row.kind === 'added' ? 'Added' : 'Removed'}</span>
        {row.kind === 'added' ? <span className="add">{words}</span> : words}
      </div>
    )
  }
  const [before, after] = sides(row, language)
  return (
    <div className="udiff__row">
      {who}
      <span className="t-field">{row.field}</span>
      {before}
      <span className="udiff__arrow">→</span>
      {after}
    </div>
  )
}

/** The two values of a changed cell, each with what changed in it tinted: removed on the left, added on the right. */
function sides(row: UnifiedRow, language: ContentLanguage): [ReactNode, ReactNode] {
  switch (row.kind) {
    case 'condition':
      return [
        <Comparison key="before" leaf={row.before} other={row.after} mark="del" />,
        <Comparison key="after" leaf={row.after} other={row.before} mark="add" />,
      ]
    case 'label': {
      // a rule's label is a string on both sides of the diff
      const change = labelChange(
        typeof row.before === 'string' ? row.before : '',
        typeof row.after === 'string' ? row.after : '',
      )
      return [
        <Changed key="before" side={change.before} mark="del" language={language} />,
        <Changed key="after" side={change.after} mark="add" language={language} />,
      ]
    }
    case 'action':
      return actionSides(row.before as RuleAction[], row.after as RuleAction[], language)
    case 'source':
      return [
        <Source key="before" provenance={row.before as Provenance} />,
        <Source key="after" provenance={row.after as Provenance} />,
      ]
    case 'defaults': {
      const [from, to] = [row.before, row.after] as { outcome: 'approve' | 'reject' | 'refer' }[]
      return [
        <span key="before" className="del">
          <DecisionTag status={from!.outcome} quiet />
        </span>,
        <span key="after" className="add">
          <DecisionTag status={to!.outcome} quiet />
        </span>,
      ]
    }
    default:
      return [
        <span key="before" className="del">
          {valueText(row.before)}
        </span>,
        <span key="after" className="add">
          {valueText(row.after)}
        </span>,
      ]
  }
}

/** A comparison as the rules table writes it, "< 8,000" or "[8,000 .. 9,000]", each part that changed tinted. */
function Comparison({ leaf, other, mark }: { leaf: unknown; other: unknown; mark: 'del' | 'add' }) {
  const node = leaf as { op?: string; value?: unknown } | undefined
  const against = other as { op?: string; value?: unknown } | undefined
  if (node?.op === undefined) {
    return <span className="t-cmp">{node === undefined ? '' : conditionText(node, new Map())}</span>
  }
  const tinted = (text: string, changed: boolean) => (
    <span className={changed ? mark : undefined}>{text}</span>
  )
  if (node.op === 'between' && Array.isArray(node.value)) {
    const [low, high] = node.value as [unknown, unknown]
    const [otherLow, otherHigh] = Array.isArray(against?.value) ? (against.value as unknown[]) : []
    return (
      <span className="t-cmp">
        <span className="val">
          [{tinted(literalText(low), low !== otherLow)} ..{' '}
          {tinted(literalText(high), high !== otherHigh)}]
        </span>
      </span>
    )
  }
  const sign = SIGNS[node.op] ?? node.op
  const valueChanged = JSON.stringify(node.value) !== JSON.stringify(against?.value)
  return (
    <span className="t-cmp">
      <span className={`op${node.op !== against?.op ? ` ${mark}` : ''}`}>{sign}</span>
      <span className={`val${valueChanged ? ` ${mark}` : ''}`}>{valueText(node.value)}</span>
    </span>
  )
}

/** A label or a reason that changed: the words around the change kept, the rest cut, and the change tinted. */
function Changed({
  side,
  mark,
  language,
}: {
  side: LabelSide
  mark: 'del' | 'add'
  language: ContentLanguage
}) {
  return (
    <span className="step__label" {...contentAttributes(language)}>
      {side.cut ? '…' : ''}
      {isolated(side.head, language)}
      <span className={mark}>{isolated(side.changed, language)}</span>
      {isolated(side.tail, language)}
      {side.cutEnd ? '…' : ''}
    </span>
  )
}

/** A changed action: the reason's change when the action still decides the same, or the actions whole otherwise. */
function actionSides(
  before: RuleAction[],
  after: RuleAction[],
  language: ContentLanguage,
): [ReactNode, ReactNode] {
  const [from] = before
  const [to] = after
  const sameAction =
    before.length === 1 &&
    after.length === 1 &&
    from!.type === to!.type &&
    from!.outcome === to!.outcome &&
    from!.reason !== undefined &&
    to!.reason !== undefined
  if (sameAction) {
    const change = labelChange(from!.reason!, to!.reason!)
    return [
      <span key="before" className="udiff__action">
        {actionsText(before)} <Changed side={change.before} mark="del" language={language} />
      </span>,
      <span key="after" className="udiff__action">
        {actionsText(after)} <Changed side={change.after} mark="add" language={language} />
      </span>,
    ]
  }
  return [
    <span key="before" className="del">
      {actionsText(before)}
    </span>,
    <span key="after" className="add">
      {actionsText(after)}
    </span>,
  ]
}

/** Where a rule comes from (Document 3, Provenance): the paragraph it quotes, the analyst, or Pending. */
function Source({ provenance }: { provenance: Provenance | undefined }) {
  switch (provenance?.kind) {
    case 'quoted':
      return (
        <span className="t-cmp">
          <span className="val">
            <Chip kind="para">{provenance.paragraph}</Chip>
          </span>
        </span>
      )
    case 'analyst':
      return <span>Analyst</span>
    case 'pending':
      return <span className="vstatus vstatus--pending">Pending</span>
    default:
      return <span />
  }
}

/**
 * The side-by-side view (Document 2, Frontend Architecture: "diff view (side by side, rule level)"; Document 3,
 * Structural diff): one row per field, rule or the defaults, an added one only after, a removed one only before, a
 * modified one on both sides with its changed attributes struck out on the left and inserted on the right, and under
 * it each change by its JSON pointer.
 */
function SideBySide({
  rows,
  language,
  fields,
  beforeLabel,
  afterLabel,
  onUnified,
}: {
  rows: DiffRow[]
  language: ContentLanguage
  fields: FieldSchema[]
  beforeLabel: string
  afterLabel: string
  onUnified: () => void
}) {
  const units = new Map(fields.map((field) => [field.name, field]))
  return (
    <>
      <p className="diff__summary">
        <span>{diffSummary(rows)}</span> ·{' '}
        <Button variant="link" onClick={onUnified}>
          Unified
        </Button>
      </p>
      <div className="table-scroll">
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
    </>
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
      <span>{actionsText(rule.actions)}</span>
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
