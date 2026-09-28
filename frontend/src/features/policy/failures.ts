import type { GenerationRefusal } from './useGeneration'

/**
 * Why no rule set was written (Document 2: an `error` event, or a refusal before the stream opened), in the words the
 * analyst needs to act on it. The refusal block closes with "Nothing was stored." (the spec, section 08).
 */
export function generationFailureText(refusal: GenerationRefusal): string | undefined {
  switch (refusal.code) {
    case 'RULESET_INVALID':
      // Document 4, Repair Loop: two repairs for the author prompt; a refusal without findings is an answer that was not
      // a document at all, which says nothing about repairs
      return refusal.findings.length > 0
        ? 'The model was asked twice to repair the draft; both attempts failed the validator.'
        : undefined
    case 'RATE_LIMITED':
      return 'Too many requests in a short time. Wait a minute, then try again.'
    case 'PROVIDER_UNAVAILABLE':
      return 'The model did not answer in time. Try again.'
    case 'BUDGET_EXHAUSTED':
      return "Today's model budget is spent; the demo's draft is still written from the cache, and any other waits until the budget resumes."
    case 'NOT_FOUND':
      return 'This sandbox cannot see that policy.'
    default:
      return 'Try again.'
  }
}

/**
 * Why a policy was not added, in the words of the person who pasted it (Document 2, error codes; Document 5, Input
 * Validation): the sentence names the limit that was broken, never the text that broke it, and the form shows the
 * code beside it (the owner's answer of 2026-09-28 to phase 5's third question).
 */
export function policyFailureText(code: string): string {
  switch (code) {
    case 'POLICY_INVALID':
      return 'The text is over its limits (40 KB, 200 paragraphs, 4,000 characters a paragraph) or holds a control character.'
    case 'UPLOAD_REJECTED':
      return 'The file must be a .txt, .md or .pdf under 2 MB, with readable text and no scripts.'
    case 'PAYLOAD_TOO_LARGE':
      return 'The file is larger than the 2 MB the API accepts.'
    case 'RATE_LIMITED':
      return 'Too many requests from this address; wait a moment and try again.'
    default:
      return 'The policy was refused.'
  }
}
