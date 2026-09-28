import { Fragment, useEffect, useRef, useState, type RefObject } from 'react'
import type {
  FieldSchema,
  Finding,
  Review,
  Rule,
  RuleAction,
  RuleSetDocument,
} from '../../api/types'
import { contentAttributes } from '../../shared/i18n/direction'
import { Actor } from '../../shared/ui/Actor'
import { Chip } from '../../shared/ui/Chip'
import { DecisionTag } from '../../shared/ui/StatusTag'
import { cellText, parseCell, type CellParts, type Leaf } from './cellGrammar'
import { tableCounts, tableMarks, underlinedCells, type TableMark } from './findings'
import {
  bandedRows,
  columnsOf,
  fieldTitle,
  fieldsOutOfView,
  headerUnit,
  pointedCell,
  type Cell,
  type Row,
} from './tableModel'
import { useInView } from '../../shared/ui/useInView'
// the gutter's marks and the strip's counts are the severity marks of section 06
import '../../shared/ui/Severity.css'
import '../../shared/ui/Table.css'

interface DecisionTableProps {
  document: RuleSetDocument
  /** The rule whose row is selected; its source paragraph and its drawer follow it. */
  selectedRuleId: string | null
  /** The rule another screen or the palette opened: its row is brought into view and flashes once (section 02). */
  openedRuleId?: string | null
  onSelect: (ruleId: string) => void
  /** An edit of one cell, already parsed; absent on a published version, which is read-only. */
  onEditCell?: (ruleId: string, previous: Leaf, next: Leaf) => void
  /** The JSON pointers the API refused (Document 2, 422 with the error list), shown on the cells they name. */
  problems?: { path: string; problem: string }[]
  /** The reviewer's findings (Document 2, Flow 1), marked on the rows they name and counted above the table. */
  review?: Review
  /** The validator's findings (Document 3, Static Validation), marked on their rows and under the cells they name. */
  findings?: Finding[]
  /** Only the rules that carry this tag. */
  tag?: string | null
}

/**
 * The decision table (the Register spec, section 07; Document 3, Decision Table Rendering): rows are rules in
 * evaluation order, grouped into the DSL's priority bands; columns are the fields the rule set compares; a cell is the
 * comparison in the cell grammar of Document 3. The strip above it states the hit policy, the findings and how many
 * fields are outside the view. A published version is read; a draft is edited cell by cell, and the table writes the
 * change back into the same document the engine runs.
 */
export function DecisionTable({
  document,
  selectedRuleId,
  openedRuleId = null,
  onSelect,
  onEditCell,
  problems = [],
  review,
  findings = [],
  tag = null,
}: DecisionTableProps) {
  const columns = columnsOf(document)
  const bands = bandedRows(document, tag)
  const marks = tableMarks(review, findings)
  const counts = tableCounts(review, findings)
  const underlines = underlinedCells(findings)
  const scrollRef = useRef<HTMLDivElement>(null)
  const toTheRight = useFieldsToTheRight(scrollRef)
  // the gutter, the rule, its priority, a column per field, the source and the action
  const span = columns.length + 5

  return (
    <>
      <div className="table__strip">
        <span>
          <b>First hit</b> · by priority; the first terminal decision stands
        </span>
        <span className="spacer" />
        {counts.block > 0 ? (
          <span className="sev sev--error">{`${String(counts.block)} block`}</span>
        ) : null}
        {counts.warn > 0 ? (
          <span className="sev sev--warning">{`${String(counts.warn)} warn`}</span>
        ) : null}
        {toTheRight > 0 ? (
          <span className="table__more">
            {`${String(toTheRight)} ${toTheRight === 1 ? 'field' : 'fields'} to the right ›`}
          </span>
        ) : null}
      </div>
      <div className="table-scroll" ref={scrollRef}>
        <table className="table table--decision table--last-borderless">
          <caption className="sr-only">
            The rules of this version in evaluation order, with the fields each one compares
          </caption>
          <thead>
            <tr>
              <th className="t-gutter" rowSpan={2} scope="col">
                <span className="sr-only">Findings</span>
              </th>
              <th className="t-group t-frozen t-frozen--shadow" scope="colgroup">
                Rule
              </th>
              <th className="t-group" />
              <th className="t-group" colSpan={columns.length} scope="colgroup">
                Conditions
              </th>
              <th className="t-head2" rowSpan={2} scope="col">
                Source
              </th>
              <th className="t-head2 t-frozen-end" rowSpan={2} scope="col">
                Action
              </th>
            </tr>
            <tr>
              <th className="t-rulehead t-frozen t-frozen--shadow" scope="col">
                Label and id
              </th>
              <th className="t-prio" scope="col">
                Priority
              </th>
              {columns.map((field) => (
                <FieldHeader key={field.name} field={field} title={fieldTitle(field, document)} />
              ))}
            </tr>
          </thead>
          {bands.map(({ band, rows }) => (
            // a band left and entered again is two groups: each keyed by the first rule it holds
            <tbody key={rows[0]?.rule.id ?? band.name}>
              <tr className="t-band">
                {/* Document 3, Recommended priority bands: the decision table displays them as groups */}
                <th
                  colSpan={span}
                  scope="colgroup"
                  aria-label={band.range === undefined ? undefined : `${band.name} ${band.range}`}
                >
                  {band.name}
                  {band.range === undefined ? null : (
                    <span className="t-band__count">{band.range}</span>
                  )}
                </th>
              </tr>
              {rows.map((row) => (
                <RuleRow
                  key={row.rule.id}
                  row={row}
                  columns={columns}
                  document={document}
                  selected={row.rule.id === selectedRuleId}
                  opened={row.rule.id === openedRuleId}
                  marks={marks.get(row.rule.id) ?? []}
                  underlines={underlines.get(row.rule.id)}
                  problems={problems}
                  onSelect={onSelect}
                  onEditCell={onEditCell}
                />
              ))}
            </tbody>
          ))}
        </table>
      </div>
    </>
  )
}

/**
 * How many field columns end beyond the visible part of the table, measured from the view's width and scroll: the
 * frozen action column covers the view's end, so a column under it is out of view too. A ResizeObserver measures it
 * when it starts and whenever the view or the table changes size; without one, as in jsdom, the strip says nothing.
 */
function useFieldsToTheRight(scrollRef: RefObject<HTMLDivElement | null>): number {
  const [count, setCount] = useState(0)
  useEffect(() => {
    const view = scrollRef.current
    if (view === null || typeof ResizeObserver === 'undefined') {
      return undefined
    }
    const measure = () => {
      const action = view.querySelector('thead th.t-frozen-end')?.getBoundingClientRect().width ?? 0
      const ends = [...view.querySelectorAll('th.t-field')].map(
        (header) => header.getBoundingClientRect().right,
      )
      setCount(fieldsOutOfView(ends, view.getBoundingClientRect().right - action))
    }
    const observer = new ResizeObserver(measure)
    observer.observe(view)
    const table = view.querySelector('table')
    if (table !== null) {
      observer.observe(table)
    }
    view.addEventListener('scroll', measure, { passive: true })
    return () => {
      view.removeEventListener('scroll', measure)
      observer.disconnect()
    }
  }, [scrollRef])
  return count
}

/** A name that may break after each underscore, as the spec breaks field names and action codes: requested_<wbr>amount. */
function Breakable({ text }: { text: string }) {
  // each part keyed by where it starts in the name, which a repeated part cannot share
  const parts = text
    .split('_')
    .reduce<{ part: string; at: number }[]>(
      (all, part) => [...all, { part, at: all.reduce((sum, one) => sum + one.part.length + 1, 0) }],
      [],
    )
  const last = parts.length - 1
  return (
    <>
      {parts.map(({ part, at }) =>
        at < (parts[last]?.at ?? 0) ? (
          <Fragment key={at}>
            {`${part}_`}
            <wbr />
          </Fragment>
        ) : (
          <Fragment key={at}>{part}</Fragment>
        ),
      )}
    </>
  )
}

/**
 * A field's column header: its name in mono, the unit beside it, a dashed "derived" tag after a derived field, and on
 * hover the field's Hebrew description, type, unit, values and paragraph (the spec, section 07). Its accessible name
 * says the unit and the tag, which the eye reads from the small type beside the name.
 */
function FieldHeader({ field, title }: { field: FieldSchema; title: string }) {
  const unit = headerUnit(field)
  const derived = field.derived === true
  const named = [field.name, unit, derived ? 'derived' : undefined].filter(
    (part) => part !== undefined,
  )
  return (
    <th
      className="t-field"
      scope="col"
      title={title}
      aria-label={named.length > 1 ? named.join(', ') : undefined}
    >
      <Breakable text={field.name} />
      {unit === undefined ? null : <span className="unit">{unit}</span>}
      {derived ? <span className="derived">derived</span> : null}
    </th>
  )
}

interface RuleRowProps {
  row: Row
  columns: FieldSchema[]
  document: RuleSetDocument
  selected: boolean
  /** Opened from elsewhere: brought into view and flashed once. */
  opened: boolean
  marks: TableMark[]
  underlines: Map<string, 'err' | 'warn'> | undefined
  problems: { path: string; problem: string }[]
  onSelect: (ruleId: string) => void
  onEditCell?: (ruleId: string, previous: Leaf, next: Leaf) => void
}

/**
 * One rule: the findings' marks in the gutter, the Hebrew label first with the id under it, the priority, a cell per
 * field, the source and the action. A refused pointer shows under the cell it names when that cell is edited here, and
 * as a mark on the rule otherwise.
 */
function RuleRow({
  row,
  columns,
  document,
  selected,
  opened,
  marks,
  underlines,
  problems,
  onSelect,
  onEditCell,
}: RuleRowProps) {
  const { rule } = row
  const ref = useInView<HTMLTableRowElement>(opened)
  const inactive = rule.enabled === false
  const cellProblems = new Map<string, string>()
  const ruleProblems: TableMark[] = []
  for (const { path, problem } of problems) {
    const pointed = pointedCell(document, path)
    if (pointed?.ruleId !== rule.id) {
      continue
    }
    const cell = pointed.field === undefined ? undefined : row.cells.get(pointed.field)
    if (pointed.field !== undefined && cell?.editable === true && onEditCell !== undefined) {
      cellProblems.set(pointed.field, problem)
    } else {
      ruleProblems.push({ mark: 'error', label: problem })
    }
  }

  return (
    <tr
      ref={ref}
      className={
        [selected ? 't-selected' : '', inactive ? 't-disabled' : '', opened ? 'flash' : '']
          .filter(Boolean)
          .join(' ') || undefined
      }
      aria-current={selected ? 'true' : undefined}
      onClick={() => onSelect(rule.id)}
    >
      <td className="t-gutter">
        {[...marks, ...ruleProblems].map((mark) => (
          <span
            key={`${mark.mark} ${mark.label}`}
            className={`sev sev--${mark.mark}`}
            title={mark.label}
          >
            <span className="sr-only">{mark.label}</span>
          </span>
        ))}
      </td>
      {/* the spec draws the rule cell as a cell; it heads its row for assistive technology */}
      <td role="rowheader" className="t-frozen t-frozen--shadow">
        <button
          type="button"
          className="t-rule"
          onClick={(event) => {
            // the row answers the click too; the button is the rule's way in for a keyboard
            event.stopPropagation()
            onSelect(rule.id)
          }}
        >
          <bdi className="t-rule__label" {...contentAttributes(document.language)}>
            {rule.label}
          </bdi>
          <span className="t-rule__id">{rule.id}</span>
        </button>
      </td>
      <td className="t-prio">{rule.priority}</td>
      {columns.map((field) => (
        <TableCell
          key={field.name}
          cell={row.cells.get(field.name)}
          field={field}
          rule={rule}
          underline={underlines?.get(field.name)}
          refused={cellProblems.get(field.name)}
          onEditCell={onEditCell}
        />
      ))}
      <td>
        <Source rule={rule} />
      </td>
      <td className="t-frozen-end">
        <Actions rule={rule} inactive={inactive} />
      </td>
    </tr>
  )
}

/** One comparison of a cell: the operator in ink-3, the operand in ink, an expression marked ƒ (the spec, section 07). */
function Comparison({ parts }: { parts: CellParts }) {
  const value = (
    <span
      className={['val', parts.token ? 'enum' : '', parts.set ? 'enum--set' : '']
        .filter(Boolean)
        .join(' ')}
    >
      {parts.value}
    </span>
  )
  const op = parts.op === undefined ? null : <span className="op">{parts.op}</span>
  if (parts.expression) {
    return (
      <span className="fx-expr">
        <span className="fx">ƒ</span>
        {op}
        {value}
      </span>
    )
  }
  if (parts.set) {
    // a set and its operator in one box as wide as the cell, the set taking what the operator leaves (Table.css)
    return (
      <span className="t-cmp__set">
        {op}
        {value}
      </span>
    )
  }
  return (
    <>
      {op}
      {value}
    </>
  )
}

interface TableCellProps {
  cell: Cell | undefined
  field: FieldSchema
  rule: Rule
  /** A finding of the validator names this cell. */
  underline: 'err' | 'warn' | undefined
  /** What the API refused about this cell, shown under it. */
  refused: string | undefined
  onEditCell?: (ruleId: string, previous: Leaf, next: Leaf) => void
}

function TableCell({ cell, field, rule, underline, refused, onEditCell }: TableCellProps) {
  const [draft, setDraft] = useState<string | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const underlined = underline === undefined ? '' : `t-underline-${underline}`
  const leaf = cell?.leaves[0]

  if (cell === undefined) {
    return <td className="t-empty" aria-label="no comparison" />
  }

  if (!cell.editable || onEditCell === undefined || leaf === undefined) {
    const complex = cell.parts.some((parts) => parts.expression)
    return (
      <td
        className={['t-cmp', complex ? 't-cmp--complex' : '', underlined].filter(Boolean).join(' ')}
      >
        {cell.parts.map((parts) => (
          <Fragment key={cellText(parts)}>
            {parts === cell.parts[0] ? null : ', '}
            <Comparison parts={parts} />
          </Fragment>
        ))}
      </td>
    )
  }

  function commit(text: string) {
    const parsed = parseCell(text, field)
    if (!parsed.ok) {
      setProblem(parsed.problem)
      return
    }
    setProblem(null)
    setDraft(null)
    if (leaf !== undefined && onEditCell !== undefined) {
      onEditCell(rule.id, leaf, parsed.leaf)
    }
  }

  const shown = problem ?? refused
  return (
    <td
      className={['t-cell-edit', shown === undefined ? '' : 't-cell-invalid', underlined]
        .filter(Boolean)
        .join(' ')}
    >
      <input
        className="input"
        value={draft ?? cell.text}
        size={Math.max(4, (draft ?? cell.text).length + 1)}
        aria-label={`${rule.id}, ${field.name}`}
        aria-invalid={shown === undefined ? undefined : true}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={(event) => commit(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            commit(event.currentTarget.value)
          }
          if (event.key === 'Escape') {
            setDraft(null)
            setProblem(null)
          }
        }}
      />
      {shown === undefined ? null : <span className="t-cell-problem">{shown}</span>}
    </td>
  )
}

/**
 * The source of a rule (Document 3, Provenance; the spec, section 07): the paragraph chip, which shows the quote on
 * hover, the person mark for a rule an analyst wrote, or Pending for a rule a change request touched.
 */
function Source({ rule }: { rule: Rule }) {
  if (rule.provenance.kind === 'quoted') {
    return (
      <Chip kind="para" title={rule.provenance.quote}>
        {String(rule.provenance.paragraph)}
      </Chip>
    )
  }
  if (rule.provenance.kind === 'analyst') {
    return <Actor kind="person" title="Written by an analyst" />
  }
  return <span className="muted">Pending</span>
}

/** One action: a decision as its tag in the words of the action column, a derivation or a flag as its mono code. */
function ActionView({ action, quiet }: { action: RuleAction; quiet: boolean }) {
  if (action.type === 'decide') {
    return (
      <>
        <DecisionTag status={action.outcome ?? 'refer'} action quiet={quiet} />
        {action.terminal === false ? (
          <>
            {' '}
            <span className="muted t-inactive">candidate</span>
          </>
        ) : null}
      </>
    )
  }
  const [verb, name] =
    action.type === 'set' ? ['set', action.field ?? ''] : ['flag', action.code ?? '']
  return (
    <span className="mono t-action t-action--code" title={`${verb} ${name}`}>
      {verb} <Breakable text={name} />
    </span>
  )
}

/**
 * What a rule does (the spec, section 07): each action, several on their own lines; an inactive rule's decision in the
 * quiet tag with "inactive" beside it, because it behaves as if absent and nothing is struck through.
 */
function Actions({ rule, inactive }: { rule: Rule; inactive: boolean }) {
  const note = inactive ? (
    <>
      {' '}
      <span className="muted t-inactive">inactive</span>
    </>
  ) : null
  const [only, ...others] = rule.actions
  if (only !== undefined && others.length === 0) {
    return (
      <>
        <ActionView action={only} quiet={inactive} />
        {note}
      </>
    )
  }
  return (
    <>
      {rule.actions.map((action) => (
        <span key={JSON.stringify(action)} className="t-action-line">
          <ActionView action={action} quiet={inactive} />
          {action === rule.actions.at(-1) ? note : null}
        </span>
      ))}
    </>
  )
}
