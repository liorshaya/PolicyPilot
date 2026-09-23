import type { AuditEntry, RulesetSummary, VersionResponse } from '../../api/types'
import {
  BASE_VERSION_ID,
  COPY_RULESET_ID,
  COPY_VERSION_ID,
  PROPOSAL_ID,
  scriptedProposalEvent,
  scriptedRequest,
} from './change'
import { lendingRuleSet } from './lending'

/**
 * The audit trail of the scripted change, entry by entry as the API writes it (Document 2, approve and reject: "a
 * CHANGE_APPROVED audit entry on the new version holds the actor, the request text, the note, the diff and the
 * regression report"; the details are those ChangeRequestService and RulesetService store). The request, the diff and
 * the regression are the committed fixtures'.
 */

export const SEEDED_POLICY_VERSION_ID = '0f4c1c9e-0000-4000-8000-0000000000d1'
export const COPY_FIRST_VERSION_ID = '0f4c1c9e-0000-4000-8000-0000000000c4'

/** The sandbox that proposed and approved: the actor of every entry it wrote (Document 5: the sandbox is the actor). */
export const SANDBOX = '5f0c1c9e-0000-4000-8000-00000000a0a0'

export const NOTE = 'אושר בוועדת האשראי'

/** Version 2 of the sandbox's copy: the approval, with everything a reader needs to see who changed what and why. */
export const approvalEntry: AuditEntry = {
  id: '0f4c1c9e-0000-4000-8000-00000000e101',
  at: '2026-09-27T09:12:00.482Z',
  actor: SANDBOX,
  action: 'CHANGE_APPROVED',
  rulesetVersionId: COPY_VERSION_ID,
  changeRequestId: PROPOSAL_ID,
  details: {
    rulesetId: COPY_RULESET_ID,
    versionNo: 2,
    rules: lendingRuleSet.rules.length,
    warnings: [],
    baseVersionId: BASE_VERSION_ID,
    requestText: scriptedRequest.text.he,
    note: NOTE,
    diff: scriptedProposalEvent.diff,
    regression: scriptedProposalEvent.regression,
  },
}

/** Version 1 of the copy: the protected version as published, copied in the same transaction. */
export const copyPublishEntry: AuditEntry = {
  id: '0f4c1c9e-0000-4000-8000-00000000e102',
  at: '2026-09-27T09:12:00.482Z',
  actor: SANDBOX,
  action: 'PUBLISH',
  rulesetVersionId: COPY_FIRST_VERSION_ID,
  changeRequestId: null,
  details: {
    rulesetId: COPY_RULESET_ID,
    versionNo: 1,
    rules: lendingRuleSet.rules.length,
    warnings: [],
    forkedFromVersionId: BASE_VERSION_ID,
  },
}

/** The seeded version 1: the proposal the sandbox made on it, and its own publication by the seed. */
export const proposedEntry: AuditEntry = {
  id: '0f4c1c9e-0000-4000-8000-00000000e103',
  at: '2026-09-27T09:10:00.120Z',
  actor: SANDBOX,
  action: 'CHANGE_PROPOSED',
  rulesetVersionId: BASE_VERSION_ID,
  changeRequestId: PROPOSAL_ID,
  details: { rulesetId: '0f4c1c9e-0000-4000-8000-0000000000b1', versionNo: 1, patches: 2 },
}

export const seedPublishEntry: AuditEntry = {
  id: '0f4c1c9e-0000-4000-8000-00000000e104',
  at: '2026-09-20T09:00:00Z',
  actor: 'demo-analyst',
  action: 'PUBLISH',
  rulesetVersionId: BASE_VERSION_ID,
  changeRequestId: null,
  details: {
    rulesetId: '0f4c1c9e-0000-4000-8000-0000000000b1',
    versionNo: 1,
    rules: lendingRuleSet.rules.length,
    warnings: [{ code: 'PROVENANCE_LOW_CONFIDENCE', path: '/rules/12/provenance' }],
  },
}

export const rejectedEntry: AuditEntry = {
  id: '0f4c1c9e-0000-4000-8000-00000000e105',
  at: '2026-09-27T09:20:00Z',
  actor: SANDBOX,
  action: 'CHANGE_REJECTED',
  rulesetVersionId: BASE_VERSION_ID,
  changeRequestId: '0f4c1c9e-0000-4000-8000-0000000000f2',
  details: { requestText: scriptedRequest.text.he, note: 'לא בתקופת הבחירות' },
}

export const gapEntry: AuditEntry = {
  id: '0f4c1c9e-0000-4000-8000-00000000e106',
  at: '2026-09-21T10:00:00Z',
  actor: SANDBOX,
  action: 'GAP_ACKNOWLEDGED',
  rulesetVersionId: '0f4c1c9e-0000-4000-8000-0000000000c2',
  changeRequestId: null,
  details: {
    id: 'F-4',
    kind: 'gap',
    severity: 'error',
    message: 'המדיניות אינה קובעת מה קורה למבקש ללא הכנסה קבועה',
    acknowledgement: { resolution: 'flag_added', note: 'נוסף סימון לבדיקה ידנית' },
    rulesetId: '0f4c1c9e-0000-4000-8000-0000000000b2',
    versionNo: 1,
  },
}

/** The sandbox's own copy of the seeded rule set after the approval: version 1 as published, version 2 the change. */
export const copyRuleset: RulesetSummary = {
  id: COPY_RULESET_ID,
  name: 'מדיניות אשראי צרכני',
  domain: 'consumer-lending',
  protected: false,
  forkedFromId: '0f4c1c9e-0000-4000-8000-0000000000b1',
  policyId: '0f4c1c9e-0000-4000-8000-0000000000a1',
  versions: [
    { versionNo: 1, status: 'PUBLISHED' },
    { versionNo: 2, status: 'PUBLISHED' },
  ],
}

/**
 * The copy's versions. Version 2 carries the patched rules of the scripted proposal, each `pending` provenance
 * rewritten into `analyst` as an approval does (Document 3, Provenance): the approver as actor, and the request and the
 * rationale as the note.
 */
export function copyVersion(versionNo: 1 | 2): VersionResponse {
  const patched = new Map(
    scriptedProposalEvent.patches.flatMap((patch) =>
      'rule' in patch && patch.rule.provenance.kind === 'pending'
        ? [
            [
              patch.ruleId,
              {
                ...patch.rule,
                provenance: {
                  kind: 'analyst' as const,
                  actor: SANDBOX,
                  note: `Change request ${PROPOSAL_ID}: ${scriptedRequest.text.he}\n${patch.rule.provenance.rationale}`,
                  changeRequestId: PROPOSAL_ID,
                },
              },
            ] as const,
          ]
        : [],
    ),
  )
  return {
    rulesetId: COPY_RULESET_ID,
    name: copyRuleset.name,
    domain: 'consumer-lending',
    protected: false,
    forkedFromId: copyRuleset.forkedFromId,
    versionId: versionNo === 1 ? COPY_FIRST_VERSION_ID : COPY_VERSION_ID,
    versionNo,
    status: 'PUBLISHED',
    policyVersionId: SEEDED_POLICY_VERSION_ID,
    publishedAt: '2026-09-27T09:12:00.482Z',
    publishedBy: SANDBOX,
    ruleSet:
      versionNo === 1
        ? lendingRuleSet
        : {
            ...lendingRuleSet,
            rules: lendingRuleSet.rules.map((rule) => patched.get(rule.id) ?? rule),
          },
    findings: [],
  }
}
