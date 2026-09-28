import { usePolicies, useRulesets, useVersion } from '../../api/queries'
import type { ContentLanguage } from '../i18n/direction'
import type { VersionStatus } from '../ui/decisionLabels'

export interface Workspace {
  /** The policy the workspace's rule set was written from, named in its own language. */
  policy: { title: string; language: ContentLanguage } | null
  /** The blocking findings of the workspace's draft that wait to be acknowledged; 0 when there is nothing to act on. */
  findingsToAcknowledge: number
  /** The latest version of the workspace's rule set, which the phone's top bar names (the spec, section 10). */
  version: { status: VersionStatus; versionNo: number } | null
}

/**
 * What the rail says about the workspace (the spec, section 08), read from the queries the screens already use: the
 * rule set the workspace is on is the one asked for, or the first one, as the Rules screen chooses it; its latest
 * version decides the count, which is the open blocking findings of a reviewed draft.
 */
export function useWorkspace(rulesetId: string | null): Workspace {
  const rulesets = useRulesets()
  const policies = usePolicies()
  const list = rulesets.data ?? []
  const chosen = list.find((one) => one.id === rulesetId) ?? list[0]
  const latestNo = chosen?.versions[chosen.versions.length - 1]?.versionNo
  const version = useVersion(
    chosen && latestNo !== undefined ? { id: chosen.id, versionNo: latestNo } : null,
  )

  const policy = policies.data?.find((one) => one.id === chosen?.policyId)
  const review = version.data?.status === 'DRAFT' ? version.data.review : undefined
  const open =
    review?.status === 'DONE' ? review.findings.filter((finding) => finding.blocking) : []
  return {
    policy: policy
      ? { title: policy.title, language: policy.language === 'he' ? 'he' : 'en' }
      : null,
    findingsToAcknowledge: open.length,
    version: version.data
      ? { status: version.data.status as VersionStatus, versionNo: version.data.versionNo }
      : null,
  }
}
