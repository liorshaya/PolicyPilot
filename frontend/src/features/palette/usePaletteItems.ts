import { publishedTarget } from '../../api/published'
import { useAudit, useLastRun, usePolicy, useRulesets, useVersion } from '../../api/queries'
import type { PaletteItem } from '../../shared/ui/paletteQuery'
import { paletteItems, type PaletteTarget } from './paletteItems'

/**
 * What the palette reaches in this workspace, read with the queries the screens use (the owner's answer to phase 6's
 * second question): the rule set the workspace is on, as the Rules screen chooses it, with its latest version and its
 * policy; this session's run of the cases on the version the Cases screen works on; the whole audit log. The palette
 * mounts this only while it is open, so nothing is asked of the API before it opens.
 */
export function usePaletteItems(rulesetId: string | null): {
  items: PaletteItem<PaletteTarget>[]
  ran: boolean
} {
  const rulesets = useRulesets()
  const list = rulesets.data ?? []
  const chosen = list.find((one) => one.id === rulesetId) ?? list[0]
  const latestNo = chosen?.versions[chosen.versions.length - 1]?.versionNo
  const version = useVersion(
    chosen && latestNo !== undefined ? { id: chosen.id, versionNo: latestNo } : null,
  )
  const policy = usePolicy(chosen?.policyId ?? null)
  const cases = publishedTarget(list, rulesetId)
  const run = useLastRun(cases ? { id: cases.ruleset.id, versionNo: cases.versionNo } : null)
  const audit = useAudit({ versionId: null })
  return {
    items: paletteItems({
      run: run.data?.results,
      ruleset: chosen,
      version: version.data,
      policy: policy.data,
      audit: audit.data,
    }),
    ran: run.data !== undefined,
  }
}
