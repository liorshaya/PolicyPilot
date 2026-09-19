import type { ExchangeResult } from '../../api/auth'

/** What the visitor reads after a refused exchange; the code itself is never repeated. */
export function refusalMessage(result: Exclude<ExchangeResult, { kind: 'entered' }>): string {
  switch (result.kind) {
    case 'wrong-code':
      return 'That code is not valid.'
    case 'locked': {
      const minutes = Math.ceil(result.retryAfterSeconds / 60)
      return `Too many attempts. Try again in ${minutes} minute${minutes === 1 ? '' : 's'}.`
    }
    case 'failed':
      return 'The demo could not be reached. Try again in a moment.'
  }
}
