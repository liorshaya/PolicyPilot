import { useState } from 'react'
import type { FieldSchema, Rule, RuleSetDocument } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { parseCell, type Leaf } from './cellGrammar'
import { DecisionTag } from '../../shared/ui/StatusTag'
import { bandedRows, columnsOf, decisionOf, type Cell, type Row } from './tableModel'
import './DecisionTable.css'

interface DecisionTableProps {
  document: RuleSetDocument
  /** The rule whose row is selected; its source paragraph and its drawer follow it. */
  selectedRuleId: string | null
  onSelect: (ruleId: string) => void
  /** An edit of one cell, already parsed; absent on a published version, which is read-only. */
  onEditCell?: (ruleId: string, previous: Leaf, next: Leaf) => void
  /** The JSON pointers the API refused (Document 2, 422 with the error list), shown on the cells they name. */
  problems?: { path: string; problem: string }[]
}

/**
 * The decision table: rows are rules in evaluation order, columns are the fields the rule set uses, cells are the
 * comparisons in the cell grammar of Document 3. A published version is read; a draft is edited cell by cell, and
 * the table writes the change back into the same document the engine runs.
 */
export function DecisionTable({
  document,
  selectedRuleId,
  onSelect,
  onEditCell,
  problems = [],
}: DecisionTableProps) {
  const columns = columnsOf(document)
  const bands = bandedRows(document)
  const language = document.language
  const refusedRules = new Set(
    problems
      .map((problem) => /^\/rules\/(\d+)/.exec(problem.path)?.[1])
      .filter((index) => index !== undefined),
  )

  return (
    <div className="table-scroll">
      <table className="table">
        <caption className="sr-only">
          The rules of this version in evaluation order, with the fields each one compares
        </caption>
        <thead>
          <tr>
            <th scope="col" className="table__id">
              Rule
            </th>
            <th scope="col" className="table__priority">
              Priority
            </th>
            {columns.map((column) => (
              <th scope="col" key={column.name} className="table__field">
                <span className="table__field-name">{column.name}</span>
                {column.derived ? <span className="table__derived">derived</span> : null}
              </th>
            ))}
            <th scope="col">Source</th>
            <th scope="col" className="table__action">
              Action
            </th>
          </tr>
        </thead>
        {bands.map((band) => (
          <tbody key={band.band}>
            <tr className="table__band">
              {/* Document 3, Recommended priority bands: the decision table displays them as groups */}
              <th scope="colgroup" colSpan={columns.length + 4}>
                {band.band}
              </th>
            </tr>
            {band.rows.map((row) => (
              <tr
                key={row.rule.id}
                className={[
                  'table__row',
                  row.rule.id === selectedRuleId ? 'table__row--selected' : '',
                  row.rule.enabled === false ? 'table__row--disabled' : '',
                  refusedRules.has(String(document.rules.indexOf(row.rule)))
                    ? 'table__row--refused'
                    : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                aria-current={row.rule.id === selectedRuleId ? 'true' : undefined}
                onClick={() => onSelect(row.rule.id)}
              >
                <th scope="row" className="table__id">
                  <button
                    type="button"
                    className="table__rule"
                    onClick={() => onSelect(row.rule.id)}
                  >
                    <span className="mono">{row.rule.id}</span>
                    <bdi className="table__label" dir="auto">
                      {row.rule.label}
                    </bdi>
                  </button>
                </th>
                <td className="table__priority tabular">{row.rule.priority}</td>
                {columns.map((column) => (
                  <TableCell
                    key={column.name}
                    cell={row.cells.get(column.name)}
                    column={column}
                    rule={row.rule}
                    onEditCell={onEditCell}
                  />
                ))}
                <td className="table__source">
                  <Source rule={row.rule} language={language} />
                </td>
                <td className="table__action" title={row.action}>
                  <Action row={row} />
                </td>
              </tr>
            ))}
          </tbody>
        ))}
      </table>
    </div>
  )
}

/** What the rule does: a decision in the decision colours, anything else in plain words. */
function Action({ row }: { row: Row }) {
  const decision = decisionOf(row.rule)
  return decision === null ? <>{row.action}</> : <DecisionTag status={decision} quiet />
}

function TableCell({
  cell,
  column,
  rule,
  onEditCell,
}: {
  cell: Cell | undefined
  column: FieldSchema
  rule: Rule
  onEditCell?: (ruleId: string, previous: Leaf, next: Leaf) => void
}) {
  const [draft, setDraft] = useState<string | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const editable = cell?.editable === true && onEditCell !== undefined
  const leaf = cell?.leaves[0]

  if (cell === undefined || cell.text === '') {
    return <td className="table__cell table__cell--empty" aria-label="no comparison" />
  }

  if (!editable || leaf === undefined) {
    return (
      <td className="table__cell">
        <span className="table__cell-text">{cell.text}</span>
      </td>
    )
  }

  function commit(text: string) {
    const parsed = parseCell(text, column)
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

  return (
    <td className={`table__cell table__cell--editable${problem ? ' table__cell--invalid' : ''}`}>
      <input
        className="table__input"
        value={draft ?? cell.text}
        aria-label={`${rule.id}, ${column.name}`}
        aria-invalid={problem ? true : undefined}
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
      {problem ? <span className="table__cell-problem">{problem}</span> : null}
    </td>
  )
}

/** The source of a rule: the paragraph it cites, or the person who wrote it (Document 3, Provenance). */
function Source({ rule, language }: { rule: Rule; language: ContentLanguage }) {
  if (rule.provenance.kind === 'quoted') {
    return (
      <span className="table__provenance">
        <span className="table__paragraph mono">¶{rule.provenance.paragraph}</span>
        <bdi className="table__quote" {...contentAttributes(language)}>
          {rule.provenance.quote}
        </bdi>
      </span>
    )
  }
  if (rule.provenance.kind === 'analyst') {
    return (
      <span className="table__provenance">
        <span className="table__analyst">Analyst</span>
        <bdi className="table__quote" dir="auto">
          {rule.provenance.note}
        </bdi>
      </span>
    )
  }
  return (
    <span className="table__provenance">
      <span className="table__pending">Pending approval</span>
    </span>
  )
}
