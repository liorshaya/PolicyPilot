import type { ReactNode } from 'react'

/**
 * Go to anything (the spec, section 08): "Everything in the product has an identifier, so everything can be reached by
 * typing it: a case number, R-330, ¶ 7, CR-0001, F-1, v1. The palette groups matches by kind and says what opening
 * does." This is the part that reads a query; Palette.tsx draws what it finds.
 */

/** The six kinds of identifier the palette reaches. */
export type PaletteKind = 'case' | 'rule' | 'paragraph' | 'finding' | 'change' | 'version'

/** One thing the palette can reach, with what opening it needs to know. */
export interface PaletteItem<Target = unknown> {
  kind: PaletteKind
  /** The identifier as its chip writes it: "Case 17", "R-170", "4" for ¶ 4, "F-1", "CR-0001", "v1". */
  code: string
  /** The identifier's number as it is written, which a query's digits are matched against: "17", "010", "0001". */
  digits: string
  /** Its state beside its chip (the spec: "a decision tag, a severity, a status"), or a rule's label. */
  state?: ReactNode
  target: Target
}

export interface PaletteGroup<Target = unknown> {
  title: string
  items: PaletteItem<Target>[]
  /** What the group says in place of rows, when a query could name one of its kind and none is here to reach. */
  note?: string
}

export type PaletteResult<Target = unknown> =
  | { kind: 'hint' }
  | { kind: 'none'; query: string }
  | { kind: 'groups'; groups: PaletteGroup<Target>[] }

/** What the palette says while nothing is typed: the spec's own list of identifiers. */
export const PALETTE_HINT = 'A case number, R-330, ¶ 7, CR-0001, F-1 or v1'

/** What a number says before this session has run the cases (the owner's answer to phase 6's second question). */
export const RUN_FIRST = 'Run the cases to reach a case by its number'

/** What opening a row does, in the words of the spec's rows. */
export const WHAT: Record<PaletteKind, string> = {
  case: 'open the trace',
  rule: 'open in the table',
  paragraph: 'open in the policy',
  finding: 'open in the review',
  change: 'open in the audit log',
  version: 'open in the table',
}

/** The groups, in the order the spec draws them, and the kinds each holds in its order. */
const GROUPS: { title: string; kinds: PaletteKind[] }[] = [
  { title: 'Cases', kinds: ['case'] },
  { title: 'Rules', kinds: ['rule'] },
  { title: 'Paragraphs, changes, findings', kinds: ['paragraph', 'change', 'finding'] },
  { title: 'Versions', kinds: ['version'] },
]

/** Rows a group shows at most: typing more of a number narrows it. */
const PER_GROUP = 5

/** The words a query may start with to name a kind, as the identifiers are written. */
const PREFIXES: [string, PaletteKind][] = [
  ['case', 'case'],
  ['cr', 'change'],
  ['r', 'rule'],
  ['¶', 'paragraph'],
  ['f', 'finding'],
  ['v', 'version'],
]

/** A query as a kind, when it names one, and the digits it carries. */
function parse(query: string): { kind: PaletteKind | null; digits: string } | null {
  const text = query.trim().toLowerCase()
  for (const [prefix, kind] of PREFIXES) {
    if (text.startsWith(prefix)) {
      const rest = /^[\s-]*(\d*)$/.exec(text.slice(prefix.length))
      if (rest !== null) {
        return { kind, digits: rest[1]! }
      }
    }
  }
  return /^\d+$/.test(text) ? { kind: null, digits: text } : null
}

const withoutZeros = (digits: string) => digits.replace(/^0+(?=\d)/, '')

/**
 * What a query names: the items of the kind it names, or of every kind for a bare number, whose number starts with its
 * digits or is that very number without its leading zeros; the very number first, then in the order of the numbers.
 */
export function findIn<Target>(
  query: string,
  items: PaletteItem<Target>[],
  { ran }: { ran: boolean },
): PaletteResult<Target> {
  if (query.trim() === '') {
    return { kind: 'hint' }
  }
  const parsed = parse(query)
  if (parsed === null) {
    return { kind: 'none', query: query.trim() }
  }
  const exact = (item: PaletteItem<Target>) =>
    parsed.digits !== '' && withoutZeros(item.digits) === withoutZeros(parsed.digits)
  const matches = (item: PaletteItem<Target>) =>
    (parsed.kind === null || item.kind === parsed.kind) &&
    (item.digits.startsWith(parsed.digits) || exact(item))
  const groups: PaletteGroup<Target>[] = []
  for (const { title, kinds } of GROUPS) {
    const found = kinds.flatMap((kind) =>
      items
        .filter((item) => item.kind === kind && matches(item))
        .sort(
          (one, other) =>
            Number(exact(other)) - Number(exact(one)) || Number(one.digits) - Number(other.digits),
        ),
    )
    const couldBeACase =
      kinds.includes('case') && !ran && parsed.digits !== '' && [null, 'case'].includes(parsed.kind)
    if (found.length > 0) {
      groups.push({ title, items: found.slice(0, PER_GROUP) })
    } else if (couldBeACase) {
      groups.push({ title, items: [], note: RUN_FIRST })
    }
  }
  return groups.length === 0 ? { kind: 'none', query: query.trim() } : { kind: 'groups', groups }
}
