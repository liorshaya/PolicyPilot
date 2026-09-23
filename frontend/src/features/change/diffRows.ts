import type { Diff, DiffChange } from '../../api/types'

/**
 * The structural diff as the rows of the side-by-side view (Document 3, Structural diff: fields by name, rules by id,
 * the defaults as a whole). The rows are read from the diff's JSON alone and never recomputed from the documents,
 * because an approval's audit entry stores that JSON and the view shows it as it was stored.
 */

export type DiffSection = 'field' | 'rule' | 'defaults'
export type DiffKind = 'added' | 'removed' | 'modified'

export interface DiffRow {
  section: DiffSection
  /** The field's name, the rule's id, or `defaults`. */
  key: string
  kind: DiffKind
  /** The item in the earlier version, whole; null for an added one. */
  before: unknown
  /** The item in the later version, whole; null for a removed one. */
  after: unknown
  /** What changed, by JSON pointer into the item; the defaults are compared whole, so theirs is empty. */
  changes: DiffChange[]
}

const KINDS: DiffKind[] = ['added', 'removed', 'modified']

/** A rule id's number, so R-1000 comes after R-410 (Document 3: `R-` and two to four digits). */
function ruleNumber(id: string): number {
  const match = /^R-(\d+)$/.exec(id)
  return match === null ? Number.MAX_SAFE_INTEGER : Number(match[1])
}

function byKey(section: DiffSection): (left: DiffRow, right: DiffRow) => number {
  return (left, right) => {
    if (section === 'rule') {
      const byNumber = ruleNumber(left.key) - ruleNumber(right.key)
      if (byNumber !== 0) {
        return byNumber
      }
    }
    return left.key < right.key ? -1 : left.key > right.key ? 1 : 0
  }
}

function keyOf(item: unknown, key: 'name' | 'id'): string {
  const value = (item as Record<string, unknown> | null)?.[key]
  return typeof value === 'string' ? value : ''
}

/** The rows of a diff: fields first, then rules, then the defaults; within a section by name, rules by number. */
export function diffRows(diff: Diff): DiffRow[] {
  const whole =
    (section: DiffSection, key: 'name' | 'id', kind: 'added' | 'removed') =>
    (item: unknown): DiffRow => ({
      section,
      key: keyOf(item, key),
      kind,
      before: kind === 'removed' ? item : null,
      after: kind === 'added' ? item : null,
      changes: [],
    })
  const fields: DiffRow[] = [
    ...diff.fields.added.map(whole('field', 'name', 'added')),
    ...diff.fields.removed.map(whole('field', 'name', 'removed')),
    ...diff.fields.modified.map((item) => ({
      section: 'field' as const,
      key: item.name,
      kind: 'modified' as const,
      before: item.from,
      after: item.to,
      changes: item.changes,
    })),
  ].sort(byKey('field'))
  const rules: DiffRow[] = [
    ...diff.rules.added.map(whole('rule', 'id', 'added')),
    ...diff.rules.removed.map(whole('rule', 'id', 'removed')),
    ...diff.rules.modified.map((item) => ({
      section: 'rule' as const,
      key: item.id,
      kind: 'modified' as const,
      before: item.from,
      after: item.to,
      changes: item.changes,
    })),
  ].sort(byKey('rule'))
  const sides = diff.defaults as { from: unknown; to: unknown } | null
  const defaults: DiffRow[] =
    sides === null
      ? []
      : [
          {
            section: 'defaults',
            key: 'defaults',
            kind: 'modified',
            before: sides.from,
            after: sides.to,
            changes: [],
          },
        ]
  return [...fields, ...rules, ...defaults]
}

/** One line over the view: the rules, then the fields, counted by kind, then whether the defaults changed. */
export function diffSummary(rows: DiffRow[]): string {
  const parts: string[] = []
  for (const section of ['rule', 'field'] as const) {
    for (const kind of KINDS) {
      const count = rows.filter((row) => row.section === section && row.kind === kind).length
      if (count > 0) {
        parts.push(`${count} ${section}${count === 1 ? '' : 's'} ${kind}`)
      }
    }
  }
  if (rows.some((row) => row.section === 'defaults')) {
    parts.push('the defaults changed')
  }
  return parts.join(' · ')
}

/** The top-level attributes the changes fall in: `/condition/value/0` is the condition. */
export function changedAttributes(changes: DiffChange[]): Set<string> {
  return new Set(
    changes.map((change) =>
      (change.path.split('/')[1] ?? '').replaceAll('~1', '/').replaceAll('~0', '~'),
    ),
  )
}
