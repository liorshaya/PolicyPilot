import { describe, expect, it } from 'vitest'
import { API_BASE_URL } from './config'

describe('API_BASE_URL', () => {
  it('is an absolute URL without a trailing slash', () => {
    expect(API_BASE_URL).toMatch(/^https?:\/\//)
    expect(API_BASE_URL.endsWith('/')).toBe(false)
  })
})
