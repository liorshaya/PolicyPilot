import { describe, expect, it } from 'vitest'
import { markerLabel, segments } from './markers'

/**
 * The citation markers as the screen shows them (Document 4, Citation marker protocol: [[p:7]], [[r:R-330]],
 * [[d:17]], [[sim:d17:has_guarantor=true]]). The API has already removed every marker it could not vouch for, so the
 * screen only splits the text and labels the chips. 100% coverage (Document 6).
 */
describe('segments', () => {
  it('splits an answer into text and the markers it carries, in order', () => {
    expect(segments('Term: 84 months.[[p:2]] Referred by R-330.[[r:R-330]][[d:17]]')).toEqual([
      { kind: 'text', at: 0, text: 'Term: 84 months.' },
      { kind: 'marker', at: 16, id: 'p:2' },
      { kind: 'text', at: 23, text: ' Referred by R-330.' },
      { kind: 'marker', at: 42, id: 'r:R-330' },
      { kind: 'marker', at: 53, id: 'd:17' },
    ])
  })

  it('keeps a simulation marker whole', () => {
    expect(segments('Approved.[[sim:d17:has_guarantor=true]]')).toEqual([
      { kind: 'text', at: 0, text: 'Approved.' },
      { kind: 'marker', at: 9, id: 'sim:d17:has_guarantor=true' },
    ])
  })

  it('leaves text without markers, and brackets that are not markers, as they are', () => {
    expect(segments('a [b] c [[x:1]]')).toEqual([{ kind: 'text', at: 0, text: 'a [b] c [[x:1]]' }])
    expect(segments('')).toEqual([])
  })
})

describe('markerLabel', () => {
  it('labels each kind the way the reader names it', () => {
    expect(markerLabel('p:7')).toBe('¶ 7')
    expect(markerLabel('r:R-330')).toBe('R-330')
    expect(markerLabel('d:17')).toBe('Application 17')
    expect(markerLabel('sim:d17:has_guarantor=true')).toBe('What if has_guarantor=true')
  })
})
