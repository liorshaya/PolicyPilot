import type { RulesetSummary } from './types'

/**
 * Which version a screen that needs a published one works on (Document 2, decide and chat sessions: only a PUBLISHED
 * version decides or is asked about). The workspace may be on a draft written a moment ago, so the choice falls back
 * to the first rule set that has a published version, and the screen says so.
 */
export interface PublishedTarget {
  ruleset: RulesetSummary
  versionNo: number
  /** True when the rule set the workspace is on is not the one being worked on. */
  elsewhere: boolean
}

/** The newest published version of a rule set, or undefined when it has none yet. */
export function latestPublished(ruleset: RulesetSummary): number | undefined {
  return [...ruleset.versions].reverse().find((version) => version.status === 'PUBLISHED')
    ?.versionNo
}

/** The published version to work on, or null when no rule set the sandbox can see has one. */
export function publishedTarget(
  list: RulesetSummary[],
  rulesetId: string | null,
): PublishedTarget | null {
  const wanted =
    list.find((one) => one.id === rulesetId) ?? list.find((one) => one.protected) ?? list[0]
  const ruleset =
    wanted && latestPublished(wanted) !== undefined
      ? wanted
      : list.find((one) => latestPublished(one) !== undefined)
  const versionNo = ruleset ? latestPublished(ruleset) : undefined
  if (ruleset === undefined || versionNo === undefined) {
    return null
  }
  return { ruleset, versionNo, elsewhere: wanted !== undefined && wanted.id !== ruleset.id }
}
