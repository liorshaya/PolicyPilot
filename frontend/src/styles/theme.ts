/**
 * The theme a browser chose (the Register spec, section 12): light by default, dark remembered per browser under the
 * key pp-theme. index.html applies it before the first paint with the same key; the app applies it again on mount.
 */
export type Theme = 'light' | 'dark'

const THEME_KEY = 'pp-theme'

/** The stored choice; light when there is none, or when the browser refuses its storage. */
export function storedTheme(): Theme {
  try {
    return localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

/** Puts the theme on <html>, where the dark block of tokens.css reads it. */
export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme
}
