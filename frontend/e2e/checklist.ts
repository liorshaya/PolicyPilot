import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { Page } from '@playwright/test'

/**
 * The design's checklist (the Register spec, section 12) as a browser can check it on a screen it has drawn (Document
 * 9, phase 6): each check lists what breaks it, one line per element, and nothing when the screen keeps it. The checks
 * read the page as drawn, its computed styles and boxes, never the product's stylesheets; what they take from a file is
 * the spec's own word on type, read from its stylesheet.
 */

/** The rules of register.css that ship, layers 1 to 5 (layer 6 styles the spec's own page), as [selectors, body]. */
function shippedRules(): [string[], string][] {
  const css = readFileSync(
    fileURLToPath(new URL('../../docs/design/register.css', import.meta.url)),
    'utf8',
  )
  const end = css.indexOf('/* ---------- Layer 6')
  if (end < 0) {
    throw new Error('register.css has no layer 6 heading to stop at')
  }
  const shipped = css.slice(0, end).replace(/\/\*[\s\S]*?\*\//g, '')
  return [...shipped.matchAll(/([^{}]+)\{([^{}]*)\}/g)].map(([, selectors, body]) => [
    selectors
      .split(',')
      .map((selector) => selector.trim().replace(/\s+/g, ' '))
      // a pseudo-element styles no text node of its own
      .filter((selector) => !selector.includes('::')),
    body,
  ])
}

const RULES = shippedRules()

/** The spec's type sizes in pixels, from its tokens (`--text-sm: 0.8125rem` is 13px). */
const SIZES = new Map(
  RULES.flatMap(([, body]) =>
    [...body.matchAll(/--(text-[\w-]+):\s*([\d.]+)rem/g)].map(
      ([, token, rem]): [string, number] => [token, Number(rem) * 16],
    ),
  ),
)

/**
 * The selectors of the spec's rules at the type floor, 11px (`--text-2xs`, "the floor; never for running text"): the
 * only text allowed under 12px (the owner's answer to phase 6's first question).
 */
export const FLOOR_SELECTORS: string[] = RULES.filter(([, body]) =>
  /font-size:\s*var\(--text-2xs\)/.test(body),
).flatMap(([selectors]) => selectors)

/**
 * The spec's relative sizes: a rule that sizes its text under 1em draws it smaller for its face, not in smaller type
 * (identifiers at 0.92em, a chip inside right-to-left text at 0.92em, the pilcrow at 0.95em of its chip), so such a
 * text is held to the floor of the text around it (the owner's answer to the third question).
 */
export const RELATIVE_RULES: { selector: string; factor: number }[] = RULES.flatMap(
  ([selectors, body]) => {
    const em = /font-size:\s*([\d.]+)em\b/.exec(body)
    return em !== null && Number(em[1]) < 1
      ? selectors.map((selector) => ({ selector, factor: Number(em[1]) }))
      : []
  },
)

/**
 * The spec's serif labels: its rules that set the serif under 16px, a paragraph's chip and number at 13px, which keep
 * the chrome's floor; the serif's 16px holds its running text, the policy's paragraphs and a quote (the same answer).
 */
export const SERIF_LABELS: string[] = RULES.filter(([, body]) => {
  const size = /font-size:\s*var\(--(text-[\w-]+)\)/.exec(body)
  return (
    /font-family:\s*var\(--font-doc-/.test(body) && size !== null && (SIZES.get(size[1]) ?? 16) < 16
  )
}).flatMap(([selectors]) => selectors)

/**
 * The spec's two bleeds, its negative-margin rules: a cited paragraph reaches 12px into the sheet's padding
 * (`.para--cited`), and a document of the margin's list into its section's (`.doc-row`, the composed screen's style
 * attribute). The segment's 1px overlap is inside the checks' 1px of rounding.
 */
const BLEEDS = '.para--cited, button.doc-row'

/** What scrolls or clips on purpose, whose insides may be wider than their containers: a table's box and the provenance line. */
const SCROLLS = '.table-scroll, .prov'

/** The faces the product loads (the spec, section 03): IBM Plex and Frank Ruhl Libre, nothing else. */
const FONTS = [
  'IBM Plex Sans',
  'IBM Plex Sans Hebrew',
  'IBM Plex Mono',
  'IBM Plex Serif',
  'Frank Ruhl Libre',
]

/** What breaks the checklist on the screen as drawn, per check; every list empty when nothing does. */
export interface Findings {
  /** An element wider than its container, or text wider than its element, or the page scrolling sideways. */
  overflow: string[]
  /** Text under its floor: 11px only where the spec sets it, 12px chrome, 13px Hebrew, the serif's running text 16px. */
  floor: string[]
  /** Text under 4.5:1 against what it is drawn on, or a mark under 3:1; a disabled control is left out (WCAG 1.4.3). */
  contrast: string[]
  /** Capitals drawn by CSS outside the seal. */
  capitals: string[]
  /** A gradient anywhere: a background, a border image or a mask. */
  gradient: string[]
  /** A face loaded, or asked for first, that is not Plex or Frank Ruhl Libre. */
  fonts: string[]
  /** The rest of the don't column a browser can see, each line named by the item it breaks. */
  dont: string[]
  /** More than one primary action on the screen ("One primary action per screen, in ink"). */
  primary: string[]
}

/** Checks the screen as it stands against the checklist, once the faces it asked for have loaded. */
export async function inspect(page: Page): Promise<Findings> {
  await page.evaluate(async () => {
    await (globalThis as unknown as { document: { fonts: { ready: Promise<unknown> } } }).document
      .fonts.ready
  })
  return page.evaluate(check, {
    floor: FLOOR_SELECTORS.join(', '),
    relative: RELATIVE_RULES,
    serifLabels: SERIF_LABELS.join(', '),
    bleeds: BLEEDS,
    scrolls: SCROLLS,
    fonts: FONTS,
  })
}

// What the checks read of the DOM, as structural types: the end-to-end project compiles without the DOM library.
interface DomBox {
  left: number
  right: number
  width: number
  height: number
}
interface DomStyle {
  getPropertyValue(property: string): string
}
interface DomNode {
  nodeType: number
  textContent: string | null
}
interface DomElement extends DomNode {
  tagName: string
  className: unknown
  parentElement: DomElement | null
  childNodes: ArrayLike<DomNode>
  closest(selectors: string): DomElement | null
  matches(selectors: string): boolean
  getAttribute(name: string): string | null
  getBoundingClientRect(): DomBox
  checkVisibility(options: Record<string, boolean>): boolean
  querySelectorAll(selectors: string): ArrayLike<DomElement>
  value?: unknown
  placeholder?: unknown
}
interface DomWindow {
  document: {
    documentElement: DomElement & { scrollWidth: number; clientWidth: number }
    querySelector(selectors: string): DomElement | null
    querySelectorAll(selectors: string): ArrayLike<DomElement>
    createRange(): { selectNodeContents(node: DomNode): void; getBoundingClientRect(): DomBox }
    fonts: Iterable<{ family: string; status: string }>
    getAnimations(): {
      effect: { target: DomElement | null; getComputedTiming(): { iterations?: number } } | null
    }[]
  }
  getComputedStyle(element: DomElement, pseudo?: string): DomStyle
  innerWidth: number
}

/** Runs in the page: everything it uses is defined inside it. */
function check({
  floor,
  relative,
  serifLabels,
  bleeds,
  scrolls,
  fonts,
}: {
  floor: string
  relative: { selector: string; factor: number }[]
  serifLabels: string
  bleeds: string
  scrolls: string
  fonts: string[]
}): Findings {
  const view = globalThis as unknown as DomWindow
  const doc = view.document
  const found: Findings = {
    overflow: [],
    floor: [],
    contrast: [],
    capitals: [],
    gradient: [],
    fonts: [],
    dont: [],
    primary: [],
  }

  type Rgba = [number, number, number, number]
  interface TextRun {
    element: DomElement
    text: string
    pseudo: string | undefined
  }
  const read = (element: DomElement, property: string, pseudo?: string) =>
    view.getComputedStyle(element, pseudo).getPropertyValue(property)
  const name = (element: DomElement) => {
    const classes =
      typeof element.className === 'string' && element.className.trim() !== ''
        ? `.${element.className.trim().split(/\s+/).join('.')}`
        : ''
    return `${element.tagName.toLowerCase()}${classes}`
  }
  const ownText = (element: DomElement) =>
    Array.from(element.childNodes)
      .filter((node) => node.nodeType === 3)
      .map((node) => node.textContent ?? '')
      .join('')
      .replace(/\s+/g, ' ')
      .trim()
  const quoted = (text: string) => `"${text.length > 40 ? `${text.slice(0, 40)}…` : text}"`
  const shown = (element: DomElement) => {
    const box = element.getBoundingClientRect()
    return (
      box.width > 0 &&
      box.height > 0 &&
      element.checkVisibility({ checkVisibilityCSS: true, visibilityProperty: true }) &&
      element.closest('.sr-only') === null
    )
  }
  const firstFamily = (element: DomElement, pseudo?: string) =>
    (read(element, 'font-family', pseudo).split(',')[0] ?? '').replace(/["']/g, '').trim()

  const all = Array.from(doc.querySelectorAll('body *')).filter(
    (element) =>
      (element.closest('svg') === null || element.tagName.toLowerCase() === 'svg') &&
      shown(element),
  )
  // what draws text: an element with text of its own, or a field with a value or a placeholder
  const fields =
    'input:not([type="checkbox"]):not([type="radio"]):not([type="hidden"]), textarea, select'
  const texts = all.flatMap((element): TextRun[] => {
    const own = ownText(element)
    if (own !== '') {
      return [{ element, text: own, pseudo: undefined }]
    }
    if (element.matches(fields)) {
      const value = typeof element.value === 'string' ? element.value.trim() : ''
      const placeholder = typeof element.placeholder === 'string' ? element.placeholder.trim() : ''
      if (value !== '') {
        return [{ element, text: value, pseudo: undefined }]
      }
      if (placeholder !== '') {
        return [{ element, text: placeholder, pseudo: '::placeholder' }]
      }
    }
    return []
  })

  // The type floor: 11px where a rule of the spec sets it, the serif's running text 16px, Hebrew 13px, the rest of the
  // chrome 12px; a text one of the spec's em rules sizes is measured at the size of the text around it
  const px = (element: DomElement) => Number.parseFloat(read(element, 'font-size'))
  const around = (element: DomElement): number => {
    let context = element
    for (;;) {
      const parent = context.parentElement
      if (
        parent === null ||
        !relative.some(
          ({ selector, factor }) =>
            context.matches(selector) && Math.abs(px(context) - factor * px(parent)) < 0.05,
        )
      ) {
        return px(context)
      }
      context = parent
    }
  }
  for (const { element, text } of texts) {
    const family = firstFamily(element)
    const serif = family === 'Frank Ruhl Libre' || family === 'IBM Plex Serif'
    const least =
      element.closest(floor) !== null
        ? 11
        : serif && element.closest(serifLabels) === null
          ? 16
          : /\p{Script=Hebrew}/u.test(text)
            ? 13
            : 12
    const size = around(element)
    if (size < least) {
      found.floor.push(
        `${name(element)} ${quoted(text)} ${String(size)}px, under ${String(least)}px`,
      )
    }
  }

  // Contrast: the text and the ground it is drawn on, each composed through every background and opacity above it
  const parse = (color: string): Rgba => {
    const parts = (color.match(/-?[\d.]+/g) ?? []).map(Number)
    return [parts[0] ?? 0, parts[1] ?? 0, parts[2] ?? 0, parts[3] ?? 1]
  }
  const over = (top: Rgba, bottom: Rgba): Rgba => {
    const alpha = top[3] + bottom[3] * (1 - top[3])
    if (alpha === 0) {
      return [0, 0, 0, 0]
    }
    const mix = (channel: number) =>
      (top[channel] * top[3] + bottom[channel] * bottom[3] * (1 - top[3])) / alpha
    return [mix(0), mix(1), mix(2), alpha]
  }
  const luminance = ([red, green, blue]: Rgba) => {
    const linear = (value: number) => {
      const scaled = value / 255
      return scaled <= 0.04045 ? scaled / 12.92 : ((scaled + 0.055) / 1.055) ** 2.4
    }
    return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
  }
  const ratio = (one: Rgba, other: Rgba) => {
    const [light, dark] = [luminance(one), luminance(other)].sort((a, b) => b - a)
    return (light + 0.05) / (dark + 0.05)
  }
  const drawn = (element: DomElement, pseudo: string | undefined) => {
    const chain: DomElement[] = []
    for (let node: DomElement | null = element; node !== null; node = node.parentElement) {
      chain.unshift(node)
    }
    let pixel: Rgba = [255, 255, 255, 1]
    const groups: { backdrop: Rgba; opacity: number }[] = []
    for (const node of chain) {
      const opacity = Number.parseFloat(read(node, 'opacity'))
      if (opacity < 1) {
        groups.push({ backdrop: pixel, opacity })
        pixel = [0, 0, 0, 0]
      }
      pixel = over(parse(read(node, 'background-color')), pixel)
    }
    const flatten = (inner: Rgba) =>
      groups.reduceRight<Rgba>(
        (color, group) =>
          over([color[0], color[1], color[2], color[3] * group.opacity], group.backdrop),
        inner,
      )
    return {
      ink: flatten(over(parse(read(element, 'color', pseudo)), pixel)),
      ground: flatten(pixel),
    }
  }
  const disabled =
    'button:disabled, input:disabled, select:disabled, textarea:disabled, fieldset:disabled, [aria-disabled="true"]'
  for (const { element, text, pseudo } of texts) {
    if (element.closest(disabled) !== null) {
      continue
    }
    const { ink, ground } = drawn(element, pseudo)
    const least = /[\p{L}\p{N}]/u.test(text) ? 4.5 : 3
    const measured = ratio(ink, ground)
    if (measured < least) {
      found.contrast.push(
        `${name(element)}${pseudo ?? ''} ${quoted(text)} ${measured.toFixed(2)}:1, under ${String(least)}:1`,
      )
    }
  }

  // Width: nothing of the workspace wider than its container, no text wider than its element, no sideways scroll
  const html = doc.documentElement
  if (html.scrollWidth > html.clientWidth) {
    found.overflow.push(
      `the page scrolls sideways by ${String(html.scrollWidth - html.clientWidth)}px`,
    )
  }
  for (const element of all) {
    const floating = ['absolute', 'fixed'].includes(read(element, 'position'))
    const inApp = element.closest('#root') !== null
    if (!inApp && !floating) {
      continue
    }
    if (element.parentElement?.closest(scrolls) || element.matches(bleeds)) {
      continue
    }
    const box = element.getBoundingClientRect()
    let container = element.parentElement
    while (container !== null && read(container, 'display') === 'contents') {
      container = container.parentElement
    }
    const edge =
      floating || container === null
        ? { left: 0, right: view.innerWidth }
        : container.getBoundingClientRect()
    if (box.left < edge.left - 1 || box.right > edge.right + 1) {
      const by = Math.round(Math.max(edge.left - box.left, box.right - edge.right))
      found.overflow.push(
        `${name(element)} ${quoted(ownText(element))} ${String(by)}px past ${floating || container === null ? 'the window' : name(container)}`,
      )
    }
    if (read(element, 'overflow-x') !== 'visible') {
      continue
    }
    for (const node of Array.from(element.childNodes)) {
      if (node.nodeType !== 3 || (node.textContent ?? '').trim() === '') {
        continue
      }
      const range = doc.createRange()
      range.selectNodeContents(node)
      const line = range.getBoundingClientRect()
      if (line.width > 0 && (line.left < box.left - 1 || line.right > box.right + 1)) {
        found.overflow.push(
          `${name(element)} ${quoted(node.textContent ?? '')} text ${String(Math.round(line.width))}px wide in ${String(Math.round(box.width))}px`,
        )
      }
    }
  }

  // Capitals only in the seal; no gradient anywhere, on an element or on what it draws before and after itself
  for (const element of all) {
    for (const pseudo of [undefined, '::before', '::after']) {
      if (pseudo !== undefined && ['none', 'normal'].includes(read(element, 'content', pseudo))) {
        continue
      }
      const at = `${name(element)}${pseudo ?? ''}`
      const capitals =
        read(element, 'text-transform', pseudo) === 'uppercase' ||
        /small-caps|petite-caps|unicase|titling-caps/.test(
          read(element, 'font-variant-caps', pseudo),
        )
      if (capitals && element.closest('.seal') === null) {
        found.capitals.push(`${at} ${quoted(ownText(element))}`)
      }
      const images = ['background-image', 'border-image-source', 'mask-image']
        .map((property) => read(element, property, pseudo))
        .join(' ')
      if (images.includes('gradient(')) {
        found.gradient.push(`${at} ${images.trim()}`)
      }
    }
  }

  // The fonts: every face loaded is Plex or Frank Ruhl Libre, and so is the first face asked for wherever text is drawn
  const loaded = new Set(
    Array.from(doc.fonts)
      .filter((face) => face.status === 'loaded')
      .map((face) => face.family.replace(/["']/g, '').trim()),
  )
  if (loaded.size === 0) {
    found.fonts.push('no face loaded')
  }
  for (const family of loaded) {
    if (!fonts.includes(family)) {
      found.fonts.push(`${family} loaded`)
    }
  }
  for (const { element, text, pseudo } of texts) {
    const family = firstFamily(element, pseudo)
    if (!fonts.includes(family)) {
      found.fonts.push(`${name(element)} ${quoted(text)} asks for ${family} first`)
    }
  }

  // The rest of the don't column, as far as a browser can see it
  const dont = (line: string, what: string) => found.dont.push(`${line}: ${what}`)
  const hue = (color: string) => {
    const [red, green, blue, alpha] = parse(color).map((value, at) =>
      at < 3 ? value / 255 : value,
    )
    const high = Math.max(red, green, blue)
    const low = Math.min(red, green, blue)
    const chroma = high - low
    if (alpha === 0 || chroma === 0) {
      return null
    }
    const lightness = (high + low) / 2
    const turn =
      high === red
        ? ((green - blue) / chroma + 6) % 6
        : high === green
          ? (blue - red) / chroma + 2
          : (red - green) / chroma + 4
    return { degrees: turn * 60, saturation: chroma / (1 - Math.abs(2 * lightness - 1)) }
  }
  const lengths = (layer: string) =>
    (layer.replace(/rgba?\([^)]*\)/g, '').match(/-?[\d.]+px/g) ?? []).map(Number.parseFloat)
  for (const element of all) {
    const at = name(element)
    const colors = ['color', 'background-color', 'fill', 'stroke'].concat(
      ['top', 'right', 'bottom', 'left']
        .filter((side) => Number.parseFloat(read(element, `border-${side}-width`)) > 0)
        .map((side) => `border-${side}-color`),
    )
    for (const property of colors) {
      const shade = hue(read(element, property))
      if (
        shade !== null &&
        shade.saturation > 0.2 &&
        shade.degrees >= 245 &&
        shade.degrees <= 330
      ) {
        dont('no purple, indigo or violet', `${at} ${property} ${read(element, property)}`)
      }
    }
    if (read(element, 'backdrop-filter') !== 'none' || read(element, 'filter').includes('blur')) {
      dont('no glassmorphism, no blurred blobs', at)
    }
    // the spec's radii are 2, 3 and 4px, the paragraph chip's pill and a mark's circle
    for (const corner of ['top-left', 'top-right', 'bottom-right', 'bottom-left']) {
      const radius = read(element, `border-${corner}-radius`)
      const size = Number.parseFloat(radius)
      if (radius.endsWith('px') && size > 4 && size < 999) {
        dont('no rounded-xl', `${at} ${radius}`)
      }
    }
    // a shadow only under what floats, or a frozen column: what stands in a floating layer floats with it; a ring drawn
    // with a spread is not a shadow
    let floats = ['absolute', 'fixed', 'sticky'].includes(read(element, 'position'))
    for (
      let layer = element.parentElement;
      layer !== null && !floats;
      layer = layer.parentElement
    ) {
      floats = ['absolute', 'fixed'].includes(read(layer, 'position'))
    }
    const shadows = read(element, 'box-shadow')
    const dropped = shadows.split(/,(?![^(]*\))/).filter(
      (layer) =>
        !layer.includes('inset') &&
        lengths(layer)
          .slice(0, 3)
          .some((length) => length !== 0),
    )
    if (shadows !== 'none' && dropped.length > 0 && !floats) {
      dont('no drop shadows on panels', `${at} ${shadows}`)
    }
    if (element.getAttribute('role') === 'switch') {
      dont('no switches', at)
    }
    if (element.tagName.toLowerCase() === 'img') {
      dont('no avatars', at)
    }
  }
  for (const { element, text } of texts) {
    const at = `${name(element)} ${quoted(text)}`
    if (/\p{Emoji_Presentation}/u.test(text)) {
      dont('no emoji, no sparkles', at)
    }
    if (text.includes('!')) {
      dont('no exclamation marks', at)
    }
    if (/\b(get started|submit|learn more)\b/i.test(text)) {
      dont('no "Get started", "Submit", "Learn more"', at)
    }
    if (/lorem ipsum|john doe|jane doe/i.test(text)) {
      dont('no lorem ipsum or placeholder people', at)
    }
    if (element.closest('a, button, [role="link"]') !== null && /^→|→$/.test(text)) {
      dont('no "→" welded to links', at)
    }
  }
  // a number in a column is end-aligned in tabular figures, or an identifier in the mono
  for (const cell of all.filter((element) =>
    ['td', 'th'].includes(element.tagName.toLowerCase()),
  )) {
    const text = (cell.textContent ?? '').replace(/\s+/g, ' ').trim()
    if (!/^[−+-]?[\d,.]+ ?(%|₪|µs|ms|s)?$/.test(text)) {
      continue
    }
    if (read(cell, 'text-align') === 'center') {
      dont('no centred numbers', `${name(cell)} ${quoted(text)}`)
    }
    if (
      !read(cell, 'font-variant-numeric').includes('tabular-nums') &&
      firstFamily(cell) !== 'IBM Plex Mono'
    ) {
      dont('no proportional figures in a column', `${name(cell)} ${quoted(text)}`)
    }
  }
  // motion at 120 and 180ms, the seal and the one flash; nothing moves on its own at rest
  for (const animation of doc.getAnimations()) {
    if (animation.effect?.getComputedTiming().iterations === Infinity) {
      const target = animation.effect.target
      dont('nothing animates on its own', target === null ? 'an animation' : name(target))
    }
  }

  // One primary action per screen, in ink: a disabled one counts, since it stands there all the same
  const primaries = all.filter((element) => element.matches('.btn--primary'))
  if (primaries.length > 1) {
    found.primary.push(
      `${String(primaries.length)} primary actions: ${primaries.map((one) => quoted(ownText(one))).join(', ')}`,
    )
  }

  // one line per finding, in the order found
  for (const key of Object.keys(found) as (keyof Findings)[]) {
    found[key] = [...new Set(found[key])]
  }
  return found
}
