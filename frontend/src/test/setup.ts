import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './msw/server'

// A time is shown in the reader's own zone; the tests read it in UTC, so no text depends on the machine's zone.
process.env.TZ = 'UTC'

// jsdom has no layout, so bringing an element into view is a no-op there rather than a missing function.
if (!('scrollIntoView' in Element.prototype)) {
  // writable and configurable, so a test can watch what is brought into view
  Object.defineProperty(Element.prototype, 'scrollIntoView', {
    value: () => undefined,
    writable: true,
    configurable: true,
  })
}

// Every API call in a component test is answered by MSW; an unexpected request fails the test.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  cleanup()
})
afterAll(() => server.close())
