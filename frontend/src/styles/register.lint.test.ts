import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * The Register's "don't" list as a build check (Document 9, phase 0; the spec's section 12). Component CSS writes no
 * literal colour, no px font size, no gradient, no Inter or Heebo, no uppercase outside the seal and no letter-spacing
 * outside the seal, the band and the figure's value; no component sets a colour or a font size in a style attribute;
 * and the tokens of the product before the Register, aliased in tokens.css while their components waited for their
 * phase, are gone from every file (Document 9, phase 5). The two spec layers, tokens.css (every value) and index.css
 * (the base), are copied verbatim and left out of the CSS rules: the owner's answer of 2026-09-28 to the board's
 * question 2. The figure's value keeps the spec's −0.01em: the owner's answer of 2026-09-28 to phase 3's seventh
 * question (section 12 forbids tracked-out uppercase labels, not the tightening of a large numeral).
 */

/** Vitest runs from the project root, where its configuration lives. */
const ROOT = process.cwd()
const SPEC_LAYERS = new Set(['src/styles/tokens.css', 'src/index.css'])

/** The files under src/ with the given extension, as paths from the project root, in a stable order. */
function sources(extension: string): string[] {
  return readdirSync(resolve(ROOT, 'src'), { recursive: true, encoding: 'utf8' })
    .filter((path) => path.endsWith(extension))
    .map((path) => `src/${path}`)
    .sort()
}

function read(path: string): string {
  return readFileSync(resolve(ROOT, path), 'utf8')
}

const count = (text: string, pattern: RegExp) => [...text.matchAll(pattern)].length

/** A selector of the seal or of a band, with their BEM elements and modifiers, keeps its capitals and its spacing. */
const SEAL = /\.seal(?:__|--|\b)/
const BAND = /\.t-band(?:__|--|\b)/
/** The figure's numeral, and the share inside it, keep the spec's letter-spacing. */
const FIGURE = /\.figure__value\b/

/** Each rule of the list, and how many times one CSS rule (its selector and declarations) breaks it. */
const RULES: [name: string, hits: (selector: string, declarations: string) => number][] = [
  ['hex colour', (_selector, body) => count(body, /#[0-9a-f]{3,8}\b/gi)],
  ['rgb( colour', (_selector, body) => count(body, /\brgba?\(/g)],
  ['px font size', (_selector, body) => count(body, /font(?:-size)?\s*:[^;]*\d(?:\.\d+)?px/g)],
  [
    'uppercase outside .seal',
    (selector, body) => (SEAL.test(selector) ? 0 : count(body, /text-transform\s*:\s*uppercase/g)),
  ],
  ['gradient', (_selector, body) => count(body, /gradient\(/g)],
  ['Inter or Heebo', (_selector, body) => count(body, /\b(?:Inter|Heebo)\b/g)],
  [
    'letter-spacing outside .seal, .t-band and .figure__value',
    (selector, body) =>
      SEAL.test(selector) || BAND.test(selector) || FIGURE.test(selector)
        ? 0
        : count(body, /letter-spacing\s*:/g),
  ],
]

/** Every CSS rule of a stylesheet as [selector, declarations], comments removed and at-rules opened. */
function cssRules(css: string): [string, string][] {
  const text = css.replace(/\/\*[\s\S]*?\*\//g, '')
  return [...text.matchAll(/([^{}]*)\{([^{}]*)\}/g)].map(([, selector, body]) => [
    selector!.trim(),
    body!,
  ])
}

/** The component CSS files that break a rule of the list, each with its number of hits. */
function breaking(name: string): Record<string, number> {
  const [, hits] = RULES.find(([rule]) => rule === name)!
  const found: Record<string, number> = {}
  for (const file of sources('.css').filter((path) => !SPEC_LAYERS.has(path))) {
    const total = cssRules(read(file)).reduce(
      (sum, [selector, body]) => sum + hits(selector, body),
      0,
    )
    if (total > 0) {
      found[file] = total
    }
  }
  return found
}

/** A style attribute's property that sets a colour or a font size. */
const COLOUR_OR_FONT_SIZE =
  /\b(?:color|background(?:Color)?|border(?:[A-Z][a-z]+)*Color|outlineColor|fill|stroke|caretColor|accentColor|textDecorationColor|fontSize|font)\s*:/

/** The Register's five faces at the weights the spec draws (Document 9, "The map", section 03). */
const FACES: Record<string, number[]> = {
  'ibm-plex-sans': [400, 500, 600],
  'ibm-plex-sans-hebrew': [400, 500, 600],
  'ibm-plex-mono': [400, 500],
  'ibm-plex-serif': [400, 500],
  'frank-ruhl-libre': [400, 500],
}

/**
 * The names of the tokens before the Register (phase 0's alias block: the --color-* family, the --space-* and
 * --radius-* sizes, and the old frame's values), which no file may define or read now that every component is ported.
 */
const OLD_TOKEN =
  /--(?:color-[a-z0-9-]+|space-\d+|radius-(?:sm|md|lg)|header-height|sidebar-width|control-height(?:-lg)?|leading-normal|pp-[a-z-]+)\b/g

describe('component CSS', () => {
  it.each(RULES.map(([name]) => name))('adds no %s', (name) => {
    expect(breaking(name)).toEqual({})
  })
})

describe('the tokens before the Register', () => {
  it('are gone: no stylesheet, component or page defines or reads one', () => {
    const files = [...sources('.css'), ...sources('.tsx'), ...sources('.ts'), 'index.html'].filter(
      (file) => file !== 'src/styles/register.lint.test.ts',
    )

    expect(files.filter((file) => count(read(file), OLD_TOKEN) > 0)).toEqual([])
  })
})

describe('components', () => {
  it('set no colour and no font size in a style attribute', () => {
    const setting = sources('.tsx').filter((file) =>
      [...read(file).matchAll(/style=\{\{([\s\S]*?)\}\}/g)].some(([, body]) =>
        COLOUR_OR_FONT_SIZE.test(body!),
      ),
    )

    expect(setting).toEqual([])
  })
})

describe('the typefaces', () => {
  it("load the five Register faces at the spec's weights, pinned, and neither Inter nor Heebo", () => {
    const { dependencies } = JSON.parse(read('package.json')) as {
      dependencies: Record<string, string>
    }
    const faces = Object.entries(dependencies).filter(([name]) => name.startsWith('@fontsource/'))
    const imports = [...read('src/main.tsx').matchAll(/^import '(@fontsource\/[^']+)'$/gm)]

    expect(faces.map(([name]) => name).sort()).toEqual(
      Object.keys(FACES)
        .map((face) => `@fontsource/${face}`)
        .sort(),
    )
    expect(faces.filter(([, version]) => !/^\d+\.\d+\.\d+$/.test(version))).toEqual([])
    expect(imports.map(([, path]) => path)).toEqual(
      Object.entries(FACES).flatMap(([face, weights]) =>
        weights.map((weight) => `@fontsource/${face}/${weight}.css`),
      ),
    )
  })
})
