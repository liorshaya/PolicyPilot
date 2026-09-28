import type { Diff, FieldSchema, Rule, RuleAction, RuleSetDocument } from '../../api/types'
import { ACTION_LABELS, type DecisionStatus } from '../../shared/ui/decisionLabels'
import {
  cellParts,
  cellText,
  decimalsOf,
  isEditable,
  isLeaf,
  literalText,
  unitLabel,
  type CellParts,
  type Leaf,
} from './cellGrammar'

/**
 * The decision table as a view of the DSL document (Document 3, Decision Table Rendering; the Register spec, section
 * 07): one row per rule, one column per field the rule set compares, a cell per comparison on that field, and an
 * action column. The table reads the document and writes it back; it never holds a second copy of the rules.
 */

/** What one cell of the table holds: its comparisons, and whether the table may edit it. */
export interface Cell {
  /** Each comparison on the column's field, in the parts the cell draws. */
  parts: CellParts[]
  /** The comparisons on one line, as the cell's input holds them: "≥ 21", or "≥ 21, ≤ 70". */
  text: string
  /** The leaves this cell stands for; an edit replaces them in the rule's condition. */
  leaves: Leaf[]
  editable: boolean
}

/** A band of Document 3's recommended priorities, and its range as the table heads it: "100–199". */
export interface Band {
  name: string
  range?: string
}

/** One row: the rule, its cells by field name, and the band its priority falls in. */
export interface Row {
  rule: Rule
  cells: Map<string, Cell>
  band: Band
  action: string
}

/**
 * The recommended priority bands of Document 3, which the decision table displays as groups. A priority outside
 * every band is said to be outside them rather than folded into a neighbour.
 */
const BANDS: { from: number; to: number; name: string }[] = [
  { from: 1, to: 99, name: 'Derivations' },
  { from: 100, to: 199, name: 'Hard eligibility gates' },
  { from: 200, to: 299, name: 'Affordability and risk limits' },
  { from: 300, to: 399, name: 'Referral conditions' },
  { from: 400, to: 499, name: 'Advisory' },
  { from: 900, to: 999, name: 'Positive outcome' },
]

export function bandOf(priority: number): Band {
  const band = BANDS.find((one) => priority >= one.from && priority <= one.to)
  return band === undefined
    ? { name: 'Outside the recommended bands' }
    : { name: band.name, range: `${String(band.from)}–${String(band.to)}` }
}

/** The action column in words: the words of a rule's action column for a decision, the action itself otherwise. */
export function actionText(rule: Rule): string {
  return actionsText(rule.actions)
}

/** A rule's actions in words, as its action column writes them; the diff writes a changed action with them. */
export function actionsText(actions: RuleAction[]): string {
  return actions
    .map((action) => {
      if (action.type === 'decide') {
        const label = ACTION_LABELS[action.outcome ?? 'refer']
        return action.terminal === false ? `${label} (candidate)` : label
      }
      if (action.type === 'set') {
        return `set ${action.field ?? ''}`
      }
      return `flag ${action.code ?? ''}`
    })
    .join(' · ')
}

/**
 * The outcome a rule decides, when it decides one: the action column shows that in the decision colours, and
 * everything else — a derivation, a flag — stays plain text, so only a business outcome is ever a tag.
 */
export function decisionOf(rule: Rule): DecisionStatus | null {
  if (rule.actions.length !== 1) {
    return null
  }
  const action = rule.actions[0]
  return action?.type === 'decide' && action.terminal !== false ? (action.outcome ?? 'refer') : null
}

/** A comparison a cell can show: a leaf, or a not around one. */
export interface Comparison {
  leaf: Leaf
  negated: boolean
}

/** A leaf, or a not around one; null for any other node. */
function comparisonOf(node: unknown): Comparison | null {
  if (isLeaf(node)) {
    return { leaf: node, negated: false }
  }
  const negation = (node ?? {}) as { not?: unknown }
  return isLeaf(negation.not) ? { leaf: negation.not, negated: true } : null
}

/**
 * The comparisons of a condition the table can lay out, and whether the condition is a flat list of plain leaves,
 * the one shape a cell may edit (Document 3, Editing round-trip). A condition, or a child of its top-level all, that
 * is neither a leaf nor a not around one stays out of the cells: the margin reads it whole.
 */
export function comparisonsOf(condition: unknown): { comparisons: Comparison[]; flat: boolean } {
  const children = Array.isArray((condition as { all?: unknown[] } | null)?.all)
    ? (condition as { all: unknown[] }).all
    : [condition]
  const comparisons = children
    .map(comparisonOf)
    .filter((comparison) => comparison !== null)
    .filter(({ leaf, negated }) => cellParts(leaf, { negated }) !== null)
  const plain = comparisons.filter((comparison) => !comparison.negated)
  return { comparisons, flat: plain.length > 0 && plain.length === children.length }
}

/** Every field a condition compares, wherever in its tree the comparison stands. */
function fieldsCompared(condition: unknown): string[] {
  if (isLeaf(condition)) {
    return [condition.field]
  }
  if (condition === null || typeof condition !== 'object') {
    return []
  }
  return Object.values(condition as Record<string, unknown>).flatMap((child) =>
    Array.isArray(child) ? child.flatMap(fieldsCompared) : fieldsCompared(child),
  )
}

/**
 * The columns the table shows (Document 3, Layout): every field a rule compares, the case fields in the document's
 * order and the derived fields after them, which the dashed "derived" tag heads (Document 9, Known tensions).
 */
export function columnsOf(document: RuleSetDocument): FieldSchema[] {
  const compared = new Set(document.rules.flatMap((rule) => fieldsCompared(rule.condition)))
  const columns = document.fields.filter((field) => compared.has(field.name))
  return [
    ...columns.filter((field) => field.derived !== true),
    ...columns.filter((field) => field.derived === true),
  ]
}

/** The decimals each column's numbers are written with: the most any of its numbers needs (the spec, section 03). */
function precisions(document: RuleSetDocument): Map<string, number> {
  const found = new Map<string, number>()
  for (const rule of document.rules) {
    for (const { leaf } of comparisonsOf(rule.condition).comparisons) {
      const values = Array.isArray(leaf.value) ? (leaf.value as unknown[]) : [leaf.value]
      const decimals = Math.max(0, ...values.map(decimalsOf))
      found.set(leaf.field, Math.max(found.get(leaf.field) ?? 0, decimals))
    }
  }
  return found
}

/** The rows of the table, in evaluation order: priority first, then rule id (Document 3, step 3). */
export function rowsOf(document: RuleSetDocument): Row[] {
  const precision = precisions(document)
  return [...document.rules]
    .sort((left, right) => left.priority - right.priority || left.id.localeCompare(right.id))
    .map((rule) => {
      const { comparisons, flat } = comparisonsOf(rule.condition)
      const cells = new Map<string, Cell>()
      for (const { leaf, negated } of comparisons) {
        const parts = cellParts(leaf, {
          negated,
          precision: precision.get(leaf.field),
        })
        if (parts === null) {
          continue
        }
        const cell = cells.get(leaf.field)
        cells.set(leaf.field, {
          parts: [...(cell?.parts ?? []), parts],
          text: cell === undefined ? cellText(parts) : `${cell.text}, ${cellText(parts)}`,
          leaves: [...(cell?.leaves ?? []), leaf],
          editable: flat && cell === undefined && isEditable(leaf),
        })
      }
      return { rule, cells, band: bandOf(rule.priority), action: actionText(rule) }
    })
}

/**
 * The rows of the table in their bands, in priority order; a band with no rule of its own is not shown. With a tag,
 * only the rules that carry it (Document 3, Rule attributes: the free grouping the UI filter reads).
 */
export function bandedRows(
  document: RuleSetDocument,
  tag: string | null = null,
): { band: Band; rows: Row[] }[] {
  const bands: { band: Band; rows: Row[] }[] = []
  for (const row of rowsOf(document)) {
    if (tag !== null && !(row.rule.tags ?? []).includes(tag)) {
      continue
    }
    const last = bands[bands.length - 1]
    if (last?.band.name === row.band.name) {
      last.rows.push(row)
    } else {
      bands.push({ band: row.band, rows: [row] })
    }
  }
  return bands
}

/** Every tag of the rule set once, in alphabetical order, for the table's tag filter. */
export function tagsOf(document: RuleSetDocument): string[] {
  return [...new Set(document.rules.flatMap((rule) => rule.tags ?? []))].sort()
}

/** A number of a field's domain, grouped by thousands. */
const grouped = (value: number) => new Intl.NumberFormat('en-US').format(value)

/**
 * What a field header says on hover (the spec, section 07): the DSL's Hebrew description first, the business reader's
 * name for the column, then the type with its unit, domain and absence, or the values of an enum, then the paragraph
 * that implied it; a derived field names the rules that set it instead.
 */
export function fieldTitle(field: FieldSchema, document: RuleSetDocument): string {
  const parts = field.description === undefined ? [] : [field.description]
  if (field.derived === true) {
    const setters = document.rules
      .filter((rule) =>
        rule.actions.some((action) => action.type === 'set' && action.field === field.name),
      )
      .map((rule) => rule.id)
    parts.push(`derived by ${setters.join(', ')}`)
    return parts.join(' · ')
  }
  const kind =
    field.type === 'enum' && field.values !== undefined ? field.values.join(', ') : field.type
  const domain =
    field.minimum !== undefined && field.maximum !== undefined
      ? `${grouped(field.minimum)} to ${grouped(field.maximum)}`
      : field.minimum !== undefined && field.minimum !== 0
        ? `at least ${grouped(field.minimum)}`
        : field.maximum !== undefined
          ? `at most ${grouped(field.maximum)}`
          : undefined
  const absent =
    field.required === true
      ? undefined
      : field.default !== undefined
        ? `${literalText(field.default)} when absent`
        : 'optional'
  parts.push(
    [kind, field.unit === undefined ? undefined : unitLabel(field.unit), domain, absent]
      .filter((part) => part !== undefined)
      .join(', '),
  )
  if (field.source !== undefined) {
    parts.push(`¶ ${String(field.source.paragraph)}`)
  }
  return parts.join(' · ')
}

/** The unit a field header names beside it (the spec, section 07), unless the field's name already says it. */
export function headerUnit(field: FieldSchema): string | undefined {
  if (field.unit === undefined || field.name.endsWith(`_${field.unit}`)) {
    return undefined
  }
  return unitLabel(field.unit)
}

/**
 * The rule, and the field when there is one, a JSON pointer of the API's 422 names (Document 2, the error list):
 * "/rules/2/condition/value" is the age of the third rule of the document. A pointer outside the rules names nothing.
 */
export function pointedCell(
  document: RuleSetDocument,
  path: string,
): { ruleId: string; field?: string } | null {
  const [, rules, index, ...rest] = path.split('/')
  const rule = rules === 'rules' ? document.rules[Number(index)] : undefined
  if (rule === undefined) {
    return null
  }
  if (rest[0] !== 'condition') {
    return { ruleId: rule.id }
  }
  let node: unknown = rule.condition
  for (const step of rest.slice(1)) {
    if (comparisonOf(node) !== null) {
      break
    }
    node = (node as Record<string, unknown> | undefined)?.[step]
  }
  const comparison = comparisonOf(node)
  return comparison === null
    ? { ruleId: rule.id }
    : { ruleId: rule.id, field: comparison.leaf.field }
}

/** How many columns end beyond the visible edge of the table: the strip's "N fields to the right". */
export function fieldsOutOfView(columnEnds: number[], visibleEnd: number): number {
  return columnEnds.filter((end) => end > visibleEnd).length
}

/**
 * The document with one leaf replaced (Document 3, Editing round-trip): the edited leaf is swapped where it stands
 * and the rest of the tree is untouched, so an edit never rewrites a rule it did not touch.
 */
export function withLeaf(
  document: RuleSetDocument,
  ruleId: string,
  previous: Leaf,
  next: Leaf,
): RuleSetDocument {
  return {
    ...document,
    rules: document.rules.map((rule) =>
      rule.id === ruleId
        ? { ...rule, condition: replaceLeaf(rule.condition, previous, next) }
        : rule,
    ),
  }
}

function replaceLeaf(condition: unknown, previous: Leaf, next: Leaf): unknown {
  if (isLeaf(condition)) {
    return sameLeaf(condition, previous) ? next : condition
  }
  const node = condition as { all?: unknown[] }
  if (Array.isArray(node.all)) {
    return { ...node, all: node.all.map((child) => replaceLeaf(child, previous, next)) }
  }
  return condition
}

function sameLeaf(candidate: Leaf, previous: Leaf): boolean {
  return (
    candidate.field === previous.field &&
    candidate.op === previous.op &&
    JSON.stringify(candidate.value ?? null) === JSON.stringify(previous.value ?? null)
  )
}

/**
 * The version a rule stands in as it is now, for the margin's "Since" (the spec, section 10: "v1 · unchanged"): the
 * version before when the structural diff between them leaves the rule alone, the version itself when it changed or is
 * new there, and the first version for a rule set that has no version before.
 */
export function sinceOf(
  ruleId: string,
  versionNo: number,
  previous: { versionNo: number; diff: Diff } | null,
): string {
  if (previous === null) {
    return `v${String(versionNo)} · first version`
  }
  const named = (rules: unknown[]) => rules.some((rule) => (rule as Rule).id === ruleId)
  if (named(previous.diff.rules.added)) {
    return `v${String(versionNo)} · new`
  }
  if (previous.diff.rules.modified.some((rule) => rule.id === ruleId)) {
    return `v${String(versionNo)} · changed from v${String(previous.versionNo)}`
  }
  return `v${String(previous.versionNo)} · unchanged`
}

/** The document with one rule switched on or off, every other rule as it was (Document 3: a rule not enabled is skipped). */
export function withEnabled(
  document: RuleSetDocument,
  ruleId: string,
  enabled: boolean,
): RuleSetDocument {
  return {
    ...document,
    rules: document.rules.map((rule) => (rule.id === ruleId ? { ...rule, enabled } : rule)),
  }
}
