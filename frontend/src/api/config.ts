/**
 * API configuration. The generated client, the SSE helper and the access-code exchange join this folder on
 * days 4 and 6 (Document 2, Frontend Architecture).
 */
const DEFAULT_API_BASE_URL = 'http://localhost:8080'

export const API_BASE_URL: string = (
  import.meta.env.VITE_API_BASE_URL ?? DEFAULT_API_BASE_URL
).replace(/\/+$/, '')
