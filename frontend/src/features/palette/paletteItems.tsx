import type {
  AuditEntry,
  CaseResult,
  PolicyResponse,
  RuleSetDocument,
  RulesetSummary,
  VersionResponse,
} from '../../api/types'
import { contentAttributes } from '../../shared/i18n/direction'
import { timeOf } from '../../shared/i18n/time'
import type { PaletteItem } from '../../shared/ui/paletteQuery'
import { Seal } from '../../shared/ui/Seal'
import { Severity } from '../../shared/ui/Severity'
import { DecisionTag, VersionTag } from '../../shared/ui/StatusTag'
import type { VersionStatus } from '../../shared/ui/decisionLabels'
import { changeRequestName } from '../change/names'

/** Where opening a row of the palette goes: the screen, and what it opens there. */
export type PaletteTarget =
  | { kind: 'case'; decisionId: string }
  | { kind: 'rule'; rulesetId: string; ruleId: string }
  | { kind: 'paragraph'; policyId: string; index: number }
  | { kind: 'finding'; rulesetId: string; findingId: string }
  | { kind: 'change'; changeRequestId: string }
  | { kind: 'version'; rulesetId: string; versionNo: number }

/** What the screens have read that the palette can reach (the owner's answer to phase 6's second question). */
export interface PaletteSources {
  /** This session's run of the cases, on the published version the Cases screen works on. */
  run?: CaseResult[]
  /** The workspace's rule set, whose versions the palette lists. */
  ruleset?: RulesetSummary
  /** Its latest version: the rules, and the findings of its review. */
  version?: VersionResponse
  /** The policy its rules cite. */
  policy?: PolicyResponse
  /** Every entry of the audit log the sandbox can see: the change requests, by number. */
  audit?: AuditEntry[]
}

/** The digits an identifier is written with: "R-170" is 170, "F-1" is 1. */
const digitsOf = (identifier: string) => identifier.replace(/\D/g, '')

/**
 * Everything the palette can reach, each with its own chip and its state (the spec, section 08: "a decision tag, a
 * severity, a status"): the cases of the run, the rules and the review's findings of the workspace's version, the
 * paragraphs of its policy, each change request once in the state its newest entry records, and the rule set's
 * versions.
 */
export function paletteItems({
  run = [],
  ruleset,
  version,
  policy,
  audit = [],
}: PaletteSources): PaletteItem<PaletteTarget>[] {
  const document = version?.ruleSet as RuleSetDocument | undefined
  const rulesetId = version?.rulesetId ?? ruleset?.id ?? ''
  const paragraphs = policy?.versions?.[policy.versions.length - 1]?.paragraphs ?? []
  // the newest entry of each change request: the log lists the newest first
  const changes = new Map<string, AuditEntry>()
  for (const entry of audit) {
    if (entry.changeRequestId !== null && entry.changeRequestNumber !== null) {
      if (!changes.has(entry.changeRequestId)) {
        changes.set(entry.changeRequestId, entry)
      }
    }
  }

  return [
    ...run.flatMap((result): PaletteItem<PaletteTarget>[] =>
      result.caseNo === undefined
        ? []
        : [
            {
              kind: 'case',
              code: `Case ${String(result.caseNo)}`,
              digits: String(result.caseNo),
              state: (
                <DecisionTag
                  status={result.status === 'OK' && result.outcome ? result.outcome : 'error'}
                  quiet
                />
              ),
              target: { kind: 'case', decisionId: result.id },
            },
          ],
    ),
    ...(document?.rules ?? []).map((rule): PaletteItem<PaletteTarget> => ({
      kind: 'rule',
      code: rule.id,
      digits: digitsOf(rule.id),
      state: (
        <bdi className="step__label" {...contentAttributes(document?.language ?? 'en')}>
          {rule.label}
        </bdi>
      ),
      target: { kind: 'rule', rulesetId, ruleId: rule.id },
    })),
    ...paragraphs.map((paragraph): PaletteItem<PaletteTarget> => ({
      kind: 'paragraph',
      code: String(paragraph.index),
      digits: String(paragraph.index),
      target: { kind: 'paragraph', policyId: policy?.id ?? '', index: paragraph.index },
    })),
    ...(version?.review?.findings ?? []).map((finding): PaletteItem<PaletteTarget> => ({
      kind: 'finding',
      code: finding.id,
      digits: digitsOf(finding.id),
      state: <Severity kind={finding.kind} />,
      target: { kind: 'finding', rulesetId, findingId: finding.id },
    })),
    ...[...changes.values()]
      .sort((one, other) => (one.changeRequestNumber ?? 0) - (other.changeRequestNumber ?? 0))
      .map((entry): PaletteItem<PaletteTarget> => {
        const name = changeRequestName(entry.changeRequestNumber ?? 0)
        return {
          kind: 'change',
          code: name,
          digits: digitsOf(name),
          state:
            entry.action === 'CHANGE_APPROVED' ? (
              <Seal kicker="Approved" line={timeOf(entry.at)} inline />
            ) : entry.action === 'CHANGE_REJECTED' ? (
              <Seal kicker="Rejected" line={timeOf(entry.at)} inline />
            ) : (
              <span className="vstatus vstatus--pending">Proposed</span>
            ),
          target: { kind: 'change', changeRequestId: entry.changeRequestId ?? '' },
        }
      }),
    ...(ruleset?.versions ?? []).map((one): PaletteItem<PaletteTarget> => ({
      kind: 'version',
      code: `v${String(one.versionNo)}`,
      digits: String(one.versionNo),
      state: <VersionTag status={one.status as VersionStatus} />,
      target: { kind: 'version', rulesetId: ruleset?.id ?? '', versionNo: one.versionNo },
    })),
  ]
}
