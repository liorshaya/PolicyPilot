import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * The stylesheets the components are drawn with, read as text. jsdom lays nothing out and resolves no var(), so a test
 * that asks how a rule draws something reads the rule and follows its tokens through tokens.css, theme by theme, the
 * way the browser does on the one element both themes set.
 */
export type Theme = 'light' | 'dark'

export const THEMES: Theme[] = ['light', 'dark']

/** A stylesheet under src/, by its path from there. Vitest runs from the project root, where its configuration lives. */
export function stylesheet(path: string): string {
  return readFileSync(resolve(process.cwd(), 'src', path), 'utf8')
}

const withoutComments = (css: string) => css.replace(/\/\*[\s\S]*?\*\//g, '')

/** The stylesheet with its at-rule blocks (media queries, keyframes) taken out, so what is left is its top level. */
function topLevel(css: string): string {
  let text = withoutComments(css)
  for (let start = text.indexOf('@'); start >= 0; start = text.indexOf('@', start)) {
    const open = text.indexOf('{', start)
    const semicolon = text.indexOf(';', start)
    if (open < 0 || (semicolon >= 0 && semicolon < open)) {
      start = semicolon < 0 ? text.length : semicolon + 1
      continue
    }
    let depth = 0
    let at = open
    for (; at < text.length; at++) {
      depth += text[at] === '{' ? 1 : text[at] === '}' ? -1 : 0
      if (depth === 0) {
        break
      }
    }
    text = text.slice(0, start) + text.slice(at + 1)
  }
  return text
}

/**
 * The declarations of every top-level rule whose selector list names this selector exactly, as {property: value}; a
 * later rule wins, as in the cascade. An empty object means no rule draws the selector; a rule inside a media query is
 * read through media().
 */
export function rule(css: string, selector: string): Record<string, string> {
  const found: Record<string, string> = {}
  for (const [, selectors, body] of topLevel(css).matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    if (!selectors!.split(',').some((one) => one.trim() === selector)) {
      continue
    }
    for (const declaration of body!.split(';')) {
      const colon = declaration.indexOf(':')
      if (colon > 0) {
        found[declaration.slice(0, colon).trim()] = declaration.slice(colon + 1).trim()
      }
    }
  }
  return found
}

/**
 * The custom properties a stylesheet declares for each theme: `:root` is the light theme, and
 * `:root[data-theme='dark']` is the dark one, which overrides the light value wherever it declares a name.
 */
export function themeDeclarations(css: string): Record<Theme, Map<string, string>> {
  const found = { light: new Map<string, string>(), dark: new Map<string, string>() }
  for (const [, selector, body] of withoutComments(css).matchAll(
    /(:root(?:\[data-theme='dark'\])?)\s*\{([^{}]*)\}/g,
  )) {
    const theme: Theme = selector === ':root' ? 'light' : 'dark'
    for (const [, name, value] of body!.matchAll(/--([a-z0-9-]+):\s*([^;]+);/g)) {
      found[theme].set(name!, value!.trim())
    }
  }
  return found
}

const tokens = themeDeclarations(stylesheet('styles/tokens.css'))

/** The value a theme gives a token of tokens.css, following var() to the end. */
export function token(name: string, theme: Theme): string {
  const value = tokens[theme].get(name) ?? tokens.light.get(name)
  if (value === undefined) {
    throw new Error(`tokens.css declares no --${name}`)
  }
  return resolved(value, theme)
}

/** A declaration's value with every var(--name) replaced by the token's value in the theme. */
export function resolved(value: string, theme: Theme): string {
  return value.replace(/var\(--([a-z0-9-]+)\)/g, (_, name: string) => token(name, theme))
}

/** The rules inside one `@media` block, by its query as the stylesheet writes it, as a stylesheet of their own. */
export function media(css: string, query: string): string {
  const text = withoutComments(css)
  const start = text.indexOf(`@media ${query}`)
  if (start < 0) {
    return ''
  }
  let depth = 0
  for (let at = text.indexOf('{', start); at < text.length; at++) {
    depth += text[at] === '{' ? 1 : text[at] === '}' ? -1 : 0
    if (depth === 0) {
      return text.slice(text.indexOf('{', start) + 1, at)
    }
  }
  return ''
}
