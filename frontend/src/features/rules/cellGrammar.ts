import type { FieldSchema } from '../../api/types'

/**
 * The cell grammar of Document 3 (Decision Table Rendering): a comparison leaf rendered with a fixed vocabulary, so
 * the model's output and a person's edit look identical, and the same text parsed back into the leaf it came from.
 * The table is a lossless view of the JSON: anything this file cannot render stays read-only and is edited in the
 * rule drawer.
 */

/** A comparison leaf: one field, one operator, one operand (Document 3, Conditions). */
export interface Leaf {
  field: string
  op: string
  value?: unknown
}

/** The operators the grammar writes as a sign. */
const SIGNS: Record<string, string> = {
  eq: '=',
  ne: '≠',
  lt: '<',
  lte: '≤',
  gt: '>',
  gte: '≥',
}

/** The operators the grammar writes as a word or a shape. */
const WORDS = new Set(['present', 'absent'])

const SIGN_TO_OP = new Map(Object.entries(SIGNS).map(([op, sign]) => [sign, op]))

/** A group of a match that has already matched; the group is there, so reading it needs no fallback. */
const captured = (match: RegExpExecArray, index: number): string => match[index]!

/** The operators an expression writes between its two operands, and how tightly each binds. */
const INFIX: Record<string, { sign: string; binding: number }> = {
  add: { sign: '+', binding: 1 },
  sub: { sign: '−', binding: 1 },
  mul: { sign: '×', binding: 2 },
  div: { sign: '/', binding: 2 },
}

/**
 * The infix form of an expression operand, as Document 3 writes it: {@code 78 − term_months / 12}. An operand is
 * bracketed only where its operator binds less tightly than the one around it, or equally on the right of a
 * difference or a quotient, so the text reads as the tree it came from and no more.
 */
export function expressionText(value: unknown): string {
  return infixOf(value).text
}

function infixOf(value: unknown): { text: string; binding: number } {
  const atom = (text: string) => ({ text, binding: 3 })
  if (typeof value === 'number' || typeof value === 'string') {
    return atom(String(value))
  }
  if (value !== null && typeof value === 'object') {
    const node = value as { fn?: string; args?: unknown[]; field?: string }
    if (typeof node.field === 'string') {
      return atom(node.field)
    }
    if (typeof node.fn === 'string' && Array.isArray(node.args)) {
      const infix = INFIX[node.fn]
      if (infix !== undefined && node.args.length === 2) {
        const [left, right] = node.args.map(infixOf) as [
          { text: string; binding: number },
          { text: string; binding: number },
        ]
        const ordered = node.fn === 'sub' || node.fn === 'div'
        const leftText = left.binding < infix.binding ? `(${left.text})` : left.text
        const rightText =
          right.binding < infix.binding || (ordered && right.binding === infix.binding)
            ? `(${right.text})`
            : right.text
        return { text: `${leftText} ${infix.sign} ${rightText}`, binding: infix.binding }
      }
      return atom(`${node.fn}(${node.args.map(expressionText).join(', ')})`)
    }
  }
  return atom(String(value))
}

/** A literal as a cell writes it: numbers grouped, booleans and strings as they are. */
export function literalText(value: unknown, field?: FieldSchema): string {
  if (typeof value === 'number') {
    const grouped = new Intl.NumberFormat('en-US', { maximumFractionDigits: 12 }).format(value)
    return field?.unit ? `${grouped} ${field.unit}` : grouped
  }
  if (typeof value === 'boolean') {
    return value ? 'true' : 'false'
  }
  return String(value)
}

/**
 * One leaf with the field's unit after each number, for a view with no column to carry the unit: the diff reads a
 * rule whole and the trace writes what a rule expected. The decision table writes its cells with {@link cellParts}.
 */
export function renderCell(leaf: Leaf, field?: FieldSchema): string {
  if (WORDS.has(leaf.op)) {
    return leaf.op
  }
  if (leaf.op === 'between' && Array.isArray(leaf.value)) {
    const [low, high] = leaf.value as [unknown, unknown]
    return `[${literalText(low, field)} .. ${literalText(high, field)}]`
  }
  if ((leaf.op === 'in' || leaf.op === 'not_in') && Array.isArray(leaf.value)) {
    const values = (leaf.value as unknown[]).map((value) => literalText(value, field)).join(', ')
    return `${leaf.op === 'in' ? '∈' : '∉'} {${values}}`
  }
  if (leaf.op === 'matches') {
    return `~ /${String(leaf.value)}/`
  }
  const sign = SIGNS[leaf.op]
  if (sign === undefined) {
    return `${leaf.op} ${literalText(leaf.value, field)}`
  }
  if (isLiteral(leaf.value)) {
    return `${sign} ${literalText(leaf.value, field)}`
  }
  return `${sign} ${expressionText(leaf.value)}`
}

/** Whether a cell can be edited in the table at all: an expression or a field reference is read-only. */
export function isEditable(leaf: Leaf): boolean {
  return WORDS.has(leaf.op) || isLiteral(leaf.value) || Array.isArray(leaf.value)
}

/** How the table's column names a unit (the spec, section 07): the shekel sign for ILS, any other unit as it is written. */
export function unitLabel(unit: string): string {
  return unit === 'ILS' ? '₪' : unit
}

/**
 * One cell of the decision table in the grammar of Document 3, in the parts the table draws: the operator in ink-3
 * and the operand in ink (the spec, section 07). The field is the column and its header names the unit, so the cell
 * carries neither: {@code ≥ 21}, {@code ∈ {salaried, self_employed}}, {@code [10,000 .. 150,000]}, {@code absent}.
 */
export interface CellParts {
  /** The operator's sign; a range and the words present and absent have none. */
  op?: string
  /** The operand as the cell writes it. */
  value: string
  /** A machine token, an enum value or a boolean, set in mono. */
  token: boolean
  /** A set of values, which may wrap inside its cell. */
  set: boolean
  /** An expression operand: marked ƒ, and edited in the margin, never in the cell. */
  expression: boolean
}

interface CellOptions {
  /** A not around the comparison, written with the negated sign (the spec, section 07). */
  negated?: boolean
  /** The decimals every number of the column is written with: one precision per column (the spec, section 03). */
  precision?: number
}

/** The negated sign of each comparison a not may stand around in a cell: ∉ [a .. b], ≠ v, ∉ {a, b}. */
const NEGATED: Record<string, string> = { between: '∉', eq: '≠', in: '∉' }

/**
 * A leaf as its cell writes it. A not around it is written with the negated sign when the leaf is a range, an
 * equality or a set, and has no cell otherwise (null): any other not reads whole in the margin.
 */
export function cellParts(leaf: Leaf, options: CellOptions = {}): CellParts | null {
  const { negated = false, precision } = options
  const number = (value: unknown) => numberText(value, precision)
  const parts = (
    op: string | undefined,
    value: string,
    kind: Partial<CellParts> = {},
  ): CellParts => ({
    op,
    value,
    token: false,
    set: false,
    expression: false,
    ...kind,
  })
  if (negated && NEGATED[leaf.op] === undefined) {
    return null
  }
  if (WORDS.has(leaf.op)) {
    return parts(undefined, leaf.op)
  }
  if (leaf.op === 'between' && Array.isArray(leaf.value)) {
    const [low, high] = leaf.value as [unknown, unknown]
    return parts(negated ? NEGATED.between : undefined, `[${number(low)} .. ${number(high)}]`)
  }
  if ((leaf.op === 'in' || leaf.op === 'not_in') && Array.isArray(leaf.value)) {
    const values = (leaf.value as unknown[]).map(number).join(', ')
    const sign = leaf.op === 'in' && !negated ? '∈' : '∉'
    return parts(sign, `{${values}}`, { token: true, set: true })
  }
  if (leaf.op === 'matches') {
    return parts('~', `/${String(leaf.value)}/`)
  }
  const sign = negated ? NEGATED[leaf.op] : SIGNS[leaf.op]
  if (isLiteral(leaf.value)) {
    return parts(sign ?? leaf.op, number(leaf.value), { token: typeof leaf.value !== 'number' })
  }
  return parts(sign ?? leaf.op, expressionText(leaf.value), {
    expression: typeof leaf.value === 'object' && leaf.value !== null,
  })
}

/** A cell on one line: the operator and the operand, as the input of an edited cell holds it. */
export function cellText(parts: CellParts): string {
  return parts.op === undefined ? parts.value : `${parts.op} ${parts.value}`
}

/** A number grouped by thousands in the column's precision; anything else as it is. */
function numberText(value: unknown, precision?: number): string {
  if (typeof value !== 'number') {
    return literalText(value)
  }
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: precision ?? 0,
    maximumFractionDigits: precision ?? 12,
  }).format(value)
}

/**
 * The decimals a number is written with, for the precision of its column: at most the DSL's twelve (Document 3, Types),
 * read from the fixed form, since a small number's own string is an exponent (0.0000001 is "1e-7").
 */
export function decimalsOf(value: unknown): number {
  if (typeof value !== 'number' || Number.isInteger(value)) {
    return 0
  }
  // a fixed form always has its point; the zeros after the last digit are no decimals
  const fixed = value.toFixed(12).replace(/0+$/, '')
  return fixed.length - fixed.indexOf('.') - 1
}

/** What went wrong with an edited cell, in the words the cell shows under itself. */
export type ParseResult = { ok: true; leaf: Leaf } | { ok: false; problem: string }

/**
 * Parses a cell back into its leaf, keeping the field of the column (Document 3, Editing round-trip). Numbers are
 * read back without their grouping; the unit is the column's, so a cell that writes it is refused with what to write.
 */
export function parseCell(text: string, field: FieldSchema): ParseResult {
  const trimmed = text.trim()
  if (trimmed === '') {
    return {
      ok: false,
      problem: 'The cell is empty; write a comparison or clear the rule in the drawer.',
    }
  }
  if (WORDS.has(trimmed)) {
    return { ok: true, leaf: { field: field.name, op: trimmed } }
  }
  const unit = field.unit
  if (unit !== undefined && [unit, unitLabel(unit)].some((written) => trimmed.includes(written))) {
    return { ok: false, problem: `Write the number alone; the column is in ${unitLabel(unit)}.` }
  }
  const between = /^\[(.+?)\.\.(.+?)]$/.exec(trimmed)
  if (between) {
    const low = parseLiteral(captured(between, 1), field)
    const high = parseLiteral(captured(between, 2), field)
    if (low === undefined || high === undefined) {
      return { ok: false, problem: `Write a range as [low .. high] in ${field.type} values.` }
    }
    return { ok: true, leaf: { field: field.name, op: 'between', value: [low, high] } }
  }
  const set = /^([∈∉])\s*\{(.*)}$/.exec(trimmed)
  if (set) {
    const values = captured(set, 2)
      .split(',')
      .map((value) => parseLiteral(value, field))
      .filter((value) => value !== undefined)
    if (values.length === 0) {
      return { ok: false, problem: 'Write a set as ∈ {value, value}.' }
    }
    const op = captured(set, 1) === '∈' ? 'in' : 'not_in'
    return { ok: true, leaf: { field: field.name, op, value: values } }
  }
  const pattern = /^~\s*\/(.*)\/$/.exec(trimmed)
  if (pattern) {
    return { ok: true, leaf: { field: field.name, op: 'matches', value: captured(pattern, 1) } }
  }
  const compared = /^(≠|≤|≥|=|<|>)\s*(.+)$/.exec(trimmed)
  if (compared) {
    const op = SIGN_TO_OP.get(captured(compared, 1))
    const value = parseLiteral(captured(compared, 2), field)
    if (op === undefined || value === undefined) {
      return {
        ok: false,
        problem: `Write a comparison as = value, ≥ value or ≤ value in ${field.type} values.`,
      }
    }
    return { ok: true, leaf: { field: field.name, op, value } }
  }
  return {
    ok: false,
    problem: 'Write = value, ≥ value, [low .. high], ∈ {a, b}, ~ /pattern/, present or absent.',
  }
}

/** A literal read back from a cell: the grouping and the unit the cell added are removed first. */
function parseLiteral(text: string, field: FieldSchema): unknown {
  const value = text.trim()
  if (value === '') {
    return undefined
  }
  if (field.type === 'boolean') {
    return value === 'true' ? true : value === 'false' ? false : undefined
  }
  if (field.type === 'number' || field.type === 'integer') {
    const numeric = Number(value.replaceAll(',', ''))
    if (!Number.isFinite(numeric)) {
      return undefined
    }
    return field.type === 'integer' && !Number.isInteger(numeric) ? undefined : numeric
  }
  return value
}

function isLiteral(value: unknown): boolean {
  return typeof value === 'number' || typeof value === 'string' || typeof value === 'boolean'
}

/** Whether a node of a condition is a comparison leaf: one field, one operator (Document 3, Conditions). */
export function isLeaf(candidate: unknown): candidate is Leaf {
  if (candidate === null || typeof candidate !== 'object') {
    return false
  }
  const node = candidate as { field?: unknown; op?: unknown }
  return typeof node.field === 'string' && typeof node.op === 'string'
}

/**
 * A whole condition on one line, for a view that reads a rule whole, such as the diff (Document 3, Decision Table
 * Rendering, the Structure column: "a compact rendering of any and not ... NOT [amount ∈ [10,000 .. 150,000]]"). A
 * leaf is its field and its cell, `all` joins its children with AND and `any` with OR, each in brackets inside another
 * combinator, `not` is NOT [...] and the constant condition is `always`; anything else is written as its JSON.
 */
export function conditionText(condition: unknown, fields: Map<string, FieldSchema>): string {
  return nodeText(condition, fields, false)
}

function nodeText(node: unknown, fields: Map<string, FieldSchema>, nested: boolean): string {
  if (isLeaf(node)) {
    const cell = renderCell(node, fields.get(node.field))
    return node.op === 'between' ? `${node.field} ∈ ${cell}` : `${node.field} ${cell}`
  }
  const tree = (node ?? {}) as { all?: unknown; any?: unknown; not?: unknown; always?: unknown }
  const joined = Array.isArray(tree.all)
    ? tree.all.map((child) => nodeText(child, fields, true)).join(' AND ')
    : Array.isArray(tree.any)
      ? tree.any.map((child) => nodeText(child, fields, true)).join(' OR ')
      : null
  if (joined !== null) {
    return nested ? `(${joined})` : joined
  }
  if (tree.not !== undefined) {
    return `NOT [${nodeText(tree.not, fields, false)}]`
  }
  return tree.always === true ? 'always' : String(JSON.stringify(node))
}
