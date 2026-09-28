import type { Finding } from '../../api/types'
/**
 * A refusal's rows from the validator's findings (Document 3, Error reporting shape): each pointer once, with the
 * problems found there, each its code and message.
 */
export function findingRows(findings: Finding[]): { pointer: string; problem: string }[] {
  const problems = new Map<string, string[]>()
  for (const finding of findings) {
    problems.set(finding.path, [
      ...(problems.get(finding.path) ?? []),
      `${finding.code} · ${finding.message}`,
    ])
  }
  return [...problems].map(([pointer, found]) => ({ pointer, problem: found.join('; ') }))
}
