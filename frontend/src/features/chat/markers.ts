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

/** What a chip says: a paragraph by its number, a rule by its id, an application, or a simulation's change. */
export function markerLabel(id: string): string {
  const [kind, ...rest] = id.split(':')
  const value = rest.join(':')
  switch (kind) {
    case 'p':
      return `¶ ${value}`
    case 'd':
      return `Application ${value}`
    case 'sim':
      return `What if ${rest.slice(1).join(':')}`
    default:
      return value
  }
}
