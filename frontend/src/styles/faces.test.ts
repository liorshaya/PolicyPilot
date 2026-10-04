import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadFaces } from './faces'

/**
 * The faces fetched at the start (the Register spec, section 03, v3.10): a browser fetches a face when the first text
 * set in it is drawn, and until it arrives the text stands in a fallback face and is then drawn again, narrower or
 * wider. The product asks for every face it declares when it starts, for the two scripts it writes. The browser's own
 * font set is stood in for here by one that declares the faces as @fontsource does, a face per subset; that a real
 * browser then fetches nothing when a screen opens is e2e/layout.spec.ts's to prove.
 */

/** The Register's five faces at the weights the spec draws (Document 9, "The map", section 03). */
const FACES: Record<string, number[]> = {
  'IBM Plex Sans': [400, 500, 600],
  'IBM Plex Sans Hebrew': [400, 500, 600],
  'IBM Plex Mono': [400, 500],
  'IBM Plex Serif': [400, 500],
  'Frank Ruhl Libre': [400, 500],
}

/** The subsets one weight of a family is declared in, a face each: Cyrillic, Hebrew, extended Latin and Latin. */
const SUBSETS = ['U+0460-052F', 'U+0590-05FF', 'U+0100-02BA', 'U+0000-00FF']

interface Declared {
  family: string
  weight: string
  style: string
  unicodeRange: string
}

/** What a font set is asked: the font, as the CSS shorthand names it, and the text it is to draw. */
type Load = (font: string, text?: string) => Promise<FontFace[]>

/** A font set that declares the given faces, and records what it is asked to load. */
function fontSet(faces: Declared[], load = vi.fn<Load>(() => Promise.resolve([]))) {
  const fonts = { [Symbol.iterator]: () => faces[Symbol.iterator](), load }
  return { fonts: fonts as unknown as FontFaceSet, load }
}

/** Every face of the Register in every subset, as the stylesheets main.tsx imports declare them. */
function theRegister(quote = ''): Declared[] {
  return Object.entries(FACES).flatMap(([family, weights]) =>
    weights.flatMap((weight) =>
      SUBSETS.map((unicodeRange) => ({
        family: `${quote}${family}${quote}`,
        weight: String(weight),
        style: 'normal',
        unicodeRange,
      })),
    ),
  )
}

/** What a font set was asked for, as the font each call names, in order of the name. */
const asked = (load: ReturnType<typeof vi.fn<Load>>) => load.mock.calls.map(([font]) => font).sort()

describe('loadFaces', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('asks once for each face at each weight, however many subsets it is declared in', () => {
    const { fonts, load } = fontSet(theRegister())

    loadFaces(fonts)

    expect(asked(load)).toEqual(
      Object.entries(FACES)
        .flatMap(([family, weights]) =>
          weights.map((weight) => `normal ${String(weight)} 1em "${family}"`),
        )
        .sort(),
    )
  })

  // a face is fetched for the text it is asked to draw: a Latin letter and a Hebrew one bring the two subsets the
  // product writes in, and leave the Cyrillic and the extended Latin where they are
  it('asks for a Latin letter and a Hebrew one, the two scripts the product writes', () => {
    const { fonts, load } = fontSet(theRegister())

    loadFaces(fonts)

    expect(load.mock.calls).toHaveLength(12)
    for (const [, text] of load.mock.calls) {
      expect(text).toMatch(/^[A-Za-z]\p{Script=Hebrew}$/u)
    }
  })

  // Firefox gives a face's family back in the quotes it was declared with, Chromium without them
  it('names a family once, in quotes, whether or not the browser gives it back quoted', () => {
    const { fonts, load } = fontSet(theRegister('"'))

    loadFaces(fonts)

    expect(asked(load)).toHaveLength(12)
    expect(asked(load)).toContain('normal 400 1em "IBM Plex Sans Hebrew"')
  })

  it('leaves a face that cannot be fetched to its first use, and no rejection unhandled', async () => {
    const unhandled = vi.fn()
    process.on('unhandledRejection', unhandled)
    const faces = theRegister()
    let asked = 0
    // a plain function, not a mock: a mock takes the promise it returns in hand, and nothing is then unhandled
    const load: Load = () => {
      asked += 1
      return Promise.reject(new Error('network'))
    }
    const fonts = { [Symbol.iterator]: () => faces[Symbol.iterator](), load }

    loadFaces(fonts as unknown as FontFaceSet)
    await new Promise((resolve) => setTimeout(resolve))

    process.off('unhandledRejection', unhandled)
    expect(asked).toBe(12)
    expect(unhandled).not.toHaveBeenCalled()
  })

  it('does nothing in a browser without the font loading API', () => {
    // jsdom is one: its document has no font set
    expect('fonts' in document).toBe(false)
    expect(() => loadFaces()).not.toThrow()
  })
})
