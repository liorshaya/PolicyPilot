import { describe, expect, it } from 'vitest'
import { findIn, type PaletteItem, type PaletteKind } from './paletteQuery'

/**
 * Go to anything (the spec, section 08): everything in the product has an identifier, so everything can be reached by
 * typing it, "a case number, R-330, ¶ 7, CR-0001, F-1, v1", and the palette groups what matches by kind. What a case
 * number reaches is this session's run of the cases (the owner's answer to phase 6's second question): before a run, a
 * number reaches no case, and the palette says how to.
 */

const item = (kind: PaletteKind, code: string, digits: string): PaletteItem<string> => ({
  kind,
  code,
  digits,
  target: code,
})

/** The 200 seeded cases, the lending policy's rules with ids in the hundreds, nine paragraphs, five findings, a change, a version. */
const ITEMS: PaletteItem<string>[] = [
  ...Array.from({ length: 200 }, (_, at) => item('case', `Case ${String(at + 1)}`, String(at + 1))),
  ...['010', '020', '100', '110', '170', '330', '410'].map((digits) =>
    item('rule', `R-${digits}`, digits),
  ),
  ...Array.from({ length: 9 }, (_, at) => item('paragraph', String(at + 1), String(at + 1))),
  ...Array.from({ length: 5 }, (_, at) => item('finding', `F-${String(at + 1)}`, String(at + 1))),
  item('change', 'CR-0001', '0001'),
  item('version', 'v1', '1'),
]

/** What each group of a result lists, by its chips. */
function listed(query: string, ran = true): [string, string[], string | undefined][] {
  const found = findIn(query, ITEMS, { ran })
  return found.kind === 'groups'
    ? found.groups.map((group) => [group.title, group.items.map((one) => one.code), group.note])
    : []
}

describe('palette · finding what a query names', () => {
  it("lists the spec's example: 17 is Case 17 first, the cases whose number starts with it, and R-170", () => {
    expect(listed('17')).toStrictEqual([
      ['Cases', ['Case 17', 'Case 170', 'Case 171', 'Case 172', 'Case 173'], undefined],
      ['Rules', ['R-170'], undefined],
    ])
  })

  it('keeps a query to one kind when it names it: R-170, ¶ 4, F-1, CR-0001, v1 and case 17', () => {
    expect(listed('R-170')).toStrictEqual([['Rules', ['R-170'], undefined]])
    expect(listed('¶ 4')).toStrictEqual([['Paragraphs, changes, findings', ['4'], undefined]])
    expect(listed('F-1')).toStrictEqual([['Paragraphs, changes, findings', ['F-1'], undefined]])
    expect(listed('CR-0001')).toStrictEqual([
      ['Paragraphs, changes, findings', ['CR-0001'], undefined],
    ])
    expect(listed('v1')).toStrictEqual([['Versions', ['v1'], undefined]])
    expect(listed('case 17')).toStrictEqual([
      ['Cases', ['Case 17', 'Case 170', 'Case 171', 'Case 172', 'Case 173'], undefined],
    ])
  })

  it('reads a kind in any case, with or without its hyphen and its space', () => {
    for (const query of ['r170', 'R 170', 'r-170', ' R-170 ']) {
      expect(listed(query), query).toStrictEqual([['Rules', ['R-170'], undefined]])
    }
    expect(listed('¶4')).toStrictEqual([['Paragraphs, changes, findings', ['4'], undefined]])
    expect(listed('cr-1')).toStrictEqual([
      ['Paragraphs, changes, findings', ['CR-0001'], undefined],
    ])
    expect(listed('F1')).toStrictEqual([['Paragraphs, changes, findings', ['F-1'], undefined]])
  })

  it('matches digits the identifier starts with, or its very number without leading zeros, that one first', () => {
    expect(listed('R-010')).toStrictEqual([['Rules', ['R-010'], undefined]])
    expect(listed('R-10')).toStrictEqual([['Rules', ['R-010', 'R-100'], undefined]])
    expect(listed('CR-1')).toStrictEqual([
      ['Paragraphs, changes, findings', ['CR-0001'], undefined],
    ])
  })

  it('groups a bare number by kind, in the order the spec draws them, at most five to a group', () => {
    expect(listed('1').map(([title, codes]) => [title, codes])).toStrictEqual([
      ['Cases', ['Case 1', 'Case 10', 'Case 11', 'Case 12', 'Case 13']],
      ['Rules', ['R-100', 'R-110', 'R-170']],
      ['Paragraphs, changes, findings', ['1', 'CR-0001', 'F-1']],
      ['Versions', ['v1']],
    ])
  })

  it('lists the first of a kind when only the kind is typed', () => {
    expect(listed('F')).toStrictEqual([
      ['Paragraphs, changes, findings', ['F-1', 'F-2', 'F-3', 'F-4', 'F-5'], undefined],
    ])
    expect(listed('v')).toStrictEqual([['Versions', ['v1'], undefined]])
  })

  it('reaches no case before a run, and says how to: "Run the cases to reach a case by its number"', () => {
    const before = ITEMS.filter((one) => one.kind !== 'case')
    const found = findIn('17', before, { ran: false })

    expect(found.kind === 'groups' ? found.groups : []).toStrictEqual([
      { title: 'Cases', items: [], note: 'Run the cases to reach a case by its number' },
      { title: 'Rules', items: [before.find((one) => one.code === 'R-170')] },
    ])
  })

  it('asks for an identifier while nothing is typed, and says when nothing matches', () => {
    expect(findIn('', ITEMS, { ran: true })).toStrictEqual({ kind: 'hint' })
    expect(findIn('   ', ITEMS, { ran: true })).toStrictEqual({ kind: 'hint' })
    expect(findIn('R-999', ITEMS, { ran: true })).toStrictEqual({ kind: 'none', query: 'R-999' })
    expect(findIn('rules', ITEMS, { ran: true })).toStrictEqual({ kind: 'none', query: 'rules' })
  })
})
