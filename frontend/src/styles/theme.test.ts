import { afterEach, describe, expect, it, vi } from 'vitest'
import { chooseTheme, storedTheme } from './theme'

/**
 * The theme a browser chose (the Register spec, section 12: light by default, dark remembered per browser). The light
 * and dark paths run through App.test.tsx; this is the path no screen shows, a browser that refuses its storage.
 */
describe('storedTheme', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.removeItem('pp-theme')
    document.documentElement.removeAttribute('data-theme')
  })

  // the rail's Theme control (Document 9, phase 1): a choice is stored in this browser and put on <html> at once
  it('stores a chosen theme in this browser and puts it on <html>', () => {
    chooseTheme('dark')

    expect(localStorage.getItem('pp-theme')).toBe('dark')
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(storedTheme()).toBe('dark')
  })

  it('falls back to the light theme when the browser refuses its storage', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('The operation is insecure.', 'SecurityError')
    })

    expect(storedTheme()).toBe('light')
  })
})
