import { describe, expect, it } from 'vitest'
import { markerLabel, placed, segments } from './markers'

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
  // the spec's glossary: Case, never Application, in the chrome; a simulation is the tool chip's "what-if"
  it('labels each kind the way the reader names it', () => {
    expect(markerLabel('p:7')).toBe('¶ 7')
    expect(markerLabel('r:R-330')).toBe('R-330')
    expect(markerLabel('d:17')).toBe('Case 17')
    expect(markerLabel('sim:d17:has_guarantor=true')).toBe('what-if')
  })
})

/**
 * Where a chip stands (the spec, section 03, the bidi law, clause 6): after the sentence's punctuation, at most one per
 * claim inline, the rest in the sources strip. A run of markers is one claim.
 */
describe('placed', () => {
  it('moves a chip written before its punctuation to after it', () => {
    expect(placed(segments('תקופת ההחזר היא 84 חודשים[[p:2]].'))).toEqual([
      { kind: 'text', at: 0, text: 'תקופת ההחזר היא 84 חודשים.' },
      { kind: 'markers', at: 25, ids: ['p:2'] },
    ])
  })

  it('leaves a chip already after its punctuation where it is', () => {
    expect(placed(segments('Referred.[[d:17]] Next.'))).toEqual([
      { kind: 'text', at: 0, text: 'Referred.' },
      { kind: 'markers', at: 9, ids: ['d:17'] },
      { kind: 'text', at: 17, text: ' Next.' },
    ])
  })

  it('holds a run of markers as one claim, in the order they were written', () => {
    expect(placed(segments('בקשה 17 הופנתה לבדיקה.[[d:17]][[p:7]]'))).toEqual([
      { kind: 'text', at: 0, text: 'בקשה 17 הופנתה לבדיקה.' },
      { kind: 'markers', at: 22, ids: ['d:17', 'p:7'] },
    ])
  })

  it('moves every punctuation mark that follows a run, and keeps the words after it', () => {
    expect(placed(segments('Approved[[r:R-900]][[p:9]]?! Then more'))).toEqual([
      { kind: 'text', at: 0, text: 'Approved?!' },
      { kind: 'markers', at: 8, ids: ['r:R-900', 'p:9'] },
      { kind: 'text', at: 28, text: ' Then more' },
    ])
  })

  it('gives the punctuation a text of its own when the answer starts with a marker', () => {
    expect(placed(segments('[[p:1]]. More'))).toEqual([
      { kind: 'text', at: 7, text: '.' },
      { kind: 'markers', at: 0, ids: ['p:1'] },
      { kind: 'text', at: 8, text: ' More' },
    ])
  })
})
