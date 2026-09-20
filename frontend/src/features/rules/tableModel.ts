import type { FieldSchema, Rule, RuleSetDocument } from '../../api/types'
import type { DecisionStatus } from '../../shared/ui/decisionLabels'
import { isEditable, renderCell, type Leaf } from './cellGrammar'

/**
 * The decision table as a view of the DSL document (Document 3, Decision Table Rendering): one row per rule, one
 * column per field the rule set uses, a cell per comparison on that field, and an action column. The table reads
 * the document and writes it back; it never holds a second copy of the rules.
 */

/** What one cell of the table holds: the text, and whether the table may edit it. */
export interface Cell {
  /** The rendered comparison, or an empty string when this rule says nothing about the column. */
  text: string
  /** The leaves this cell stands for; an edit replaces them in the rule's condition. */
  leaves: Leaf[]
  editable: boolean
}

/** One row: the rule, its cells by field name, and how the row is shown. */
export interface Row {
  rule: Rule
  cells: Map<string, Cell>
  /** The band the rule's priority falls in (Document 3, Recommended priority bands). */
  band: string
  action: string
}

/** The bands of Document 3, used to group the rows the way the policy reads. */
export function bandOf(priority: number): string {
  if (priority < 100) return 'Derivations'
  if (priority < 200) return 'Eligibility'
  if (priority < 300) return 'Affordability and risk'
  if (priority < 400) return 'Referral'
  if (priority < 900) return 'Advisory'
  return 'Approval'
}

/** The action column: what the rule does, in the words of Document 3's action table. */
export function actionText(rule: Rule): string {
  return rule.actions
    .map((action) => {
      if (action.type === 'decide') {
        const outcome = action.outcome ?? 'refer'
        const label =
          outcome === 'approve' ? 'Approve' : outcome === 'reject' ? 'Decline' : 'Manual review'
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

/** The leaves of a condition that speak about one field each, and whether the tree is a flat list of them. */
export function leavesOf(condition: unknown): { leaves: Leaf[]; flat: boolean } {
  if (isLeaf(condition)) {
    return { leaves: [condition], flat: true }
  }
  const node = condition as { all?: unknown[] }
  if (Array.isArray(node.all)) {
    const leaves = node.all.filter(isLeaf)
    return { leaves, flat: leaves.length === node.all.length }
  }
  return { leaves: [], flat: false }
}

/** The columns the table shows: every field any rule compares or sets, in the document's field order. */
export function columnsOf(document: RuleSetDocument): FieldSchema[] {
  const used = new Set<string>()
  for (const rule of document.rules) {
    for (const leaf of leavesOf(rule.condition).leaves) {
      used.add(leaf.field)
    }
    for (const action of rule.actions) {
      if (action.type === 'set' && action.field !== undefined) {
        used.add(action.field)
      }
    }
  }
  return document.fields.filter((field) => used.has(field.name))
}

/** The rows of the table, in evaluation order: priority first, then rule id (Document 3, step 3). */
export function rowsOf(document: RuleSetDocument): Row[] {
  const fields = new Map(document.fields.map((field) => [field.name, field]))
  return [...document.rules]
    .sort((left, right) => left.priority - right.priority || left.id.localeCompare(right.id))
    .map((rule) => {
      const { leaves, flat } = leavesOf(rule.condition)
      const cells = new Map<string, Cell>()
      for (const leaf of leaves) {
        const cell = cells.get(leaf.field) ?? { text: '', leaves: [], editable: flat }
        const text = renderCell(leaf, fields.get(leaf.field))
        cells.set(leaf.field, {
          text: cell.text === '' ? text : `${cell.text}, ${text}`,
          leaves: [...cell.leaves, leaf],
          editable: flat && cell.leaves.length === 0 && isEditable(leaf),
        })
      }
      return { rule, cells, band: bandOf(rule.priority), action: actionText(rule) }
    })
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

function isLeaf(candidate: unknown): candidate is Leaf {
  if (candidate === null || typeof candidate !== 'object') {
    return false
  }
  const node = candidate as { field?: unknown; op?: unknown }
  return typeof node.field === 'string' && typeof node.op === 'string'
}
