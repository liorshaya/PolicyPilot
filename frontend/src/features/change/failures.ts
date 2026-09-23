/**
 * Why a change could not be proposed, in the words the analyst needs to act on it (Document 2, API Surface: before any
 * stream opens, 400 for the text, 404 for a version the sandbox cannot see, 409 for one not published or not embedded,
 * 429 for the rate limits; after it, an `error` event).
 */
export function proposeFailureText(code: string): string {
  switch (code) {
    case 'VERSION_STATUS_CONFLICT':
      return 'Only a published version that has finished indexing takes a change. Try again in a moment.'
    case 'RATE_LIMITED':
      return 'Too many requests in a short time. Wait a minute, then try again.'
    case 'REQUEST_INVALID':
      return 'The request is empty, too long, or holds a character it may not carry. Nothing was stored.'
    case 'NOT_FOUND':
      return 'This sandbox cannot see that version.'
    case 'PROVIDER_UNAVAILABLE':
      return 'The model did not answer in time. Nothing was stored. Try again.'
    default:
      return 'The change could not be proposed. Nothing was stored. Try again.'
  }
}

/** Why an approval or a rejection was refused (Document 2, approve and reject: 400, 404, 409). */
export function decisionFailureText(code: string): string {
  switch (code) {
    case 'VERSION_STATUS_CONFLICT':
      return 'The version it was proposed on is no longer the latest, or this sandbox already has its own copy of the seeded rule set. Propose the change again on the latest version.'
    case 'REQUEST_INVALID':
      return 'The note is too long or holds a character it may not carry.'
    case 'NOT_FOUND':
      return 'This change request is not one this sandbox can see.'
    default:
      return 'Nothing was published. Try again.'
  }
}
