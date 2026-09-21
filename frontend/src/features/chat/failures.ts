/** Why an answer could not be given, in the words the reader needs to act on it. */
export function failureText(code: string | undefined): string {
  switch (code) {
    case 'VERSION_STATUS_CONFLICT':
      return 'This version is still being indexed for questions. Try again in a moment.'
    case 'RATE_LIMITED':
      return 'Too many questions in a short time. Wait a minute, then try again.'
    case 'ANSWER_WITHHELD':
      return 'The answer was withheld because it contained something that must not be shown.'
    case 'PROVIDER_UNAVAILABLE':
      return 'The model did not answer in time. Try again.'
    default:
      return 'The question could not be answered. Try again.'
  }
}
