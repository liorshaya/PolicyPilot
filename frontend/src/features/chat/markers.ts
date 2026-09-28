/**
 * The citation markers of an answer (Document 4, Citation marker protocol) as the screen shows them: the text split
 * around each marker, and the label of its chip. The API removes every marker whose source it cannot vouch for before
 * a token leaves it, so what arrives here is text and markers the answer may carry.
 */

/** A piece of the answer and where it starts in the text, which is also what tells two pieces apart. */
export type Segment =
  { kind: 'text'; at: number; text: string } | { kind: 'marker'; at: number; id: string }

/** The four kinds of Document 4 and their ids. */
const MARKER = /\[\[(p:\d{1,4}|r:R-\d{2,4}|d:\d{1,9}|sim:d\d{1,9}:[^\]\s]+)]]/g

/** A run of markers written one after the other: one claim's sources, the first of them the chip inline. */
export interface MarkerRun {
  kind: 'markers'
  at: number
  ids: string[]
}

/** The answer as text and markers, in the order they are written. */
export function segments(text: string): Segment[] {
  const parts: Segment[] = []
  let at = 0
  for (const match of text.matchAll(MARKER)) {
    if (match.index > at) {
      parts.push({ kind: 'text', at, text: text.slice(at, match.index) })
    }
    parts.push({ kind: 'marker', at: match.index, id: match[1]! })
    at = match.index + match[0].length
  }
  if (at < text.length) {
    parts.push({ kind: 'text', at, text: text.slice(at) })
  }
  return parts
}

/** The punctuation a chip follows rather than precedes (the spec, section 03, the bidi law, clause 6). */
const PUNCTUATION = /^[.,;:!?]+/

/**
 * Where the chips stand (the spec, section 03, the bidi law, clause 6): after the sentence's punctuation, so a marker
 * the model wrote before a period moves behind it; and a run of markers is one claim, whose first source is the chip
 * inline and whose others are left to the sources strip. A run holds the spaces between its markers, as Document 4's
 * answer prompt writes them ("[[d:17]] [[r:R-330]] [[p:7]]").
 */
export function placed(parts: Segment[]): ((Segment & { kind: 'text' }) | MarkerRun)[] {
  const result: ((Segment & { kind: 'text' }) | MarkerRun)[] = []
  let index = 0
  while (index < parts.length) {
    const part = parts[index]!
    if (part.kind === 'text') {
      result.push(part)
      index++
      continue
    }
    const run: MarkerRun = { kind: 'markers', at: part.at, ids: [] }
    for (let current = parts[index]; current !== undefined; current = parts[index]) {
      if (current.kind === 'marker') {
        run.ids.push(current.id)
      } else if (current.text.trim() !== '' || parts[index + 1]?.kind !== 'marker') {
        break
      }
      index++
    }
    const next = parts[index]
    const punctuation = next?.kind === 'text' ? (PUNCTUATION.exec(next.text)?.[0] ?? '') : ''
    if (next?.kind === 'text' && punctuation !== '') {
      const previous = result[result.length - 1]
      if (previous?.kind === 'text') {
        // the space the model left before the run goes with it, so the mark closes its sentence
        result[result.length - 1] = { ...previous, text: previous.text.trimEnd() + punctuation }
      } else {
        result.push({ kind: 'text', at: next.at, text: punctuation })
      }
      result.push(run)
      const rest = next.text.slice(punctuation.length)
      if (rest !== '') {
        result.push({ kind: 'text', at: next.at + punctuation.length, text: rest })
      }
      index++
    } else {
      result.push(run)
    }
  }
  return result
}

/**
 * What a chip says: a paragraph by its number, a rule by its id, a case by its number (the spec's glossary: Case, never
 * Application, in the chrome), and a simulation as the tool chip's "what-if", which its title spells out.
 */
export function markerLabel(id: string): string {
  const [kind, ...rest] = id.split(':')
  const value = rest.join(':')
  switch (kind) {
    case 'p':
      return `¶ ${value}`
    case 'd':
      return `Case ${value}`
    case 'sim':
      return 'what-if'
    default:
      return value
  }
}
