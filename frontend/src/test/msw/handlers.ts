import type { RequestHandler } from 'msw'

/**
 * MSW request handlers for component tests. Day 6 generates them from the OpenAPI document so the mocks are
 * typed by the same generated client the app uses (Document 6, Frontend Test Design). Empty on day 1.
 */
export const handlers: RequestHandler[] = []
