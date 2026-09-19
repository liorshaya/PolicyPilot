/**
 * API configuration. The access-code exchange lives beside it (auth.ts); the generated client and the SSE helper
 * join this folder on day 6 (Document 2, Frontend Architecture).
 */
const DEFAULT_API_BASE_URL = 'http://localhost:8080'

export const API_BASE_URL: string = (
  import.meta.env.VITE_API_BASE_URL ?? DEFAULT_API_BASE_URL
).replace(/\/+$/, '')
