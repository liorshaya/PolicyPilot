import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'
import { api } from './client'
import type {
  Aggregates,
  Audience,
  AuditEntry,
  BatchResult,
  Decision,
  Diff,
  GapResolution,
  PolicyResponse,
  PolicySummary,
  ProviderResponse,
  RulesetSummary,
  VersionResponse,
  RuleSetDocument,
} from './types'

/**
 * Server state, and only server state (Document 2, Frontend Architecture, key decision 1): every screen is a query
 * or a mutation, and a publication or an edit invalidates the keys that showed the old version.
 */

export const keys = {
  provider: ['provider'] as const,
  policies: ['policies'] as const,
  policy: (policyId: string) => ['policy', policyId] as const,
  rulesets: ['rulesets'] as const,
  version: (rulesetId: string, versionNo: number) => ['version', rulesetId, versionNo] as const,
  stats: (rulesetId: string, versionNo: number) => ['stats', rulesetId, versionNo] as const,
  decision: (decisionId: string) => ['decision', decisionId] as const,
  audit: (versionId: string) => ['audit', versionId] as const,
  diff: (rulesetId: string, from: number, to: number) => ['diff', rulesetId, from, to] as const,
}

/** The provider the API runs on (Document 2, GET /system/provider); it changes only with a deployment. */
export function useProvider(): UseQueryResult<ProviderResponse> {
  return useQuery({ queryKey: keys.provider, queryFn: () => api.provider(), staleTime: Infinity })
}

export function usePolicies(): UseQueryResult<PolicySummary[]> {
  return useQuery({
    queryKey: keys.policies,
    queryFn: async () => (await api.policies()).policies ?? [],
  })
}

export function usePolicy(policyId: string | null): UseQueryResult<PolicyResponse> {
  return useQuery({
    queryKey: keys.policy(policyId ?? 'none'),
    queryFn: () => api.policy(policyId ?? ''),
    enabled: policyId !== null,
  })
}

export function useRulesets(): UseQueryResult<RulesetSummary[]> {
  return useQuery({
    queryKey: keys.rulesets,
    queryFn: async () => (await api.rulesets()).rulesets ?? [],
  })
}

export function useVersion(
  ruleset: { id: string; versionNo: number } | null,
): UseQueryResult<VersionResponse> {
  return useQuery({
    queryKey: keys.version(ruleset?.id ?? 'none', ruleset?.versionNo ?? 0),
    queryFn: () => api.version(ruleset?.id ?? '', ruleset?.versionNo ?? 0),
    enabled: ruleset !== null,
  })
}

/** Creating a policy from pasted text or from a file; both refresh the policy list. */
export function useCreatePolicy() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: { title: string; language: 'he' | 'en'; text?: string; file?: File }) =>
      input.file
        ? api.uploadPolicy(input.file, input.title, input.language)
        : api.createPolicy({
            title: input.title,
            language: input.language,
            text: input.text ?? '',
          }),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.policies }),
  })
}

/** Replacing the rules of a draft; the version it answers with replaces the one on the screen. */
export function useReplaceRules(ruleset: { id: string; versionNo: number }) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (document: RuleSetDocument) =>
      api.replaceRules(ruleset.id, ruleset.versionNo, document),
    onSuccess: (version) => {
      client.setQueryData(keys.version(version.rulesetId, version.versionNo), version)
      void client.invalidateQueries({ queryKey: keys.rulesets })
    },
  })
}

/** Publishing a draft; everything that showed the version or the rule sets is refreshed. */
export function usePublish(ruleset: { id: string; versionNo: number }) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api.publish(ruleset.id, ruleset.versionNo),
    onSuccess: (version) => {
      client.setQueryData(keys.version(version.rulesetId, version.versionNo), version)
      void client.invalidateQueries({ queryKey: keys.rulesets })
    },
  })
}

/**
 * Running the review of a draft again (Document 2, Flow 1: after an edit made it STALE, or a call left it FAILED); the
 * version it answers with replaces the one on the screen. It can take a minute: the reviewer reads the whole draft.
 */
export function useRunReview(ruleset: { id: string; versionNo: number }) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api.review(ruleset.id, ruleset.versionNo),
    onSuccess: (version) => {
      client.setQueryData(keys.version(version.rulesetId, version.versionNo), version)
    },
  })
}

/** Acknowledging one review finding: a gap with its resolution, an error with a note (Document 3, Publishing gate). */
export function useAcknowledge(ruleset: { id: string; versionNo: number }) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: { findingId: string; resolution?: GapResolution; note?: string }) =>
      api.acknowledge(ruleset.id, ruleset.versionNo, input.findingId, {
        resolution: input.resolution,
        note: input.note,
      }),
    onSuccess: (version) => {
      client.setQueryData(keys.version(version.rulesetId, version.versionNo), version)
    },
  })
}

/**
 * The explanation of one decision for one reader (Document 4, Prompt 3). It is asked for, not loaded with the trace:
 * the trace is the engine's, the explanation is a model's reading of it, and the reader chooses to see it.
 */
export function useExplain(decisionId: string) {
  return useMutation({
    mutationFn: (audience: Audience) => api.explain(decisionId, audience),
  })
}

/** What the version has decided so far (Document 2, statistics over the latest decision of every case). */
export function useStats(
  ruleset: { id: string; versionNo: number } | null,
): UseQueryResult<Aggregates> {
  return useQuery({
    queryKey: keys.stats(ruleset?.id ?? 'none', ruleset?.versionNo ?? 0),
    queryFn: () => api.stats(ruleset?.id ?? '', ruleset?.versionNo ?? 0),
    enabled: ruleset !== null,
  })
}

/** One decision with its whole trace; a case is opened from the list by its id. */
export function useDecision(decisionId: string | null): UseQueryResult<Decision> {
  return useQuery({
    queryKey: keys.decision(decisionId ?? 'none'),
    queryFn: () => api.decision(decisionId ?? ''),
    enabled: decisionId !== null,
  })
}

/**
 * A person's decision on a proposed change (Document 2, approve and reject). An approval publishes the next version,
 * into the sandbox's own copy when the base is protected, so the rule set list is read again either way.
 */
export function useDecideChange() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: { changeId: string; verdict: 'approve' | 'reject'; note: string }) =>
      api.decideChange(input.changeId, input.verdict, input.note),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: keys.rulesets })
      // the base's log gains the decision, and a new version its own entries
      await client.invalidateQueries({ queryKey: ['audit'] })
    },
  })
}

/** The audit log of one version, newest first (Document 2, GET /audit). */
export function useAudit(versionId: string | null): UseQueryResult<AuditEntry[]> {
  return useQuery({
    queryKey: keys.audit(versionId ?? 'none'),
    queryFn: async () => (await api.audit(versionId ?? '')).entries,
    enabled: versionId !== null,
  })
}

/** The structural diff of two versions of one rule set (Brief FR-20: any two versions). */
export function useDiff(
  compared: { rulesetId: string; from: number; to: number } | null,
): UseQueryResult<Diff> {
  return useQuery({
    queryKey: keys.diff(compared?.rulesetId ?? 'none', compared?.from ?? 0, compared?.to ?? 0),
    queryFn: () => api.diff(compared?.rulesetId ?? '', compared?.from ?? 0, compared?.to ?? 0),
    enabled: compared !== null,
  })
}

/** Running a seeded set of cases; the statistics of the version are refreshed with the batch's own aggregates. */
export function useRunFixtureSet(ruleset: { id: string; versionNo: number }) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (fixtureSet: string) =>
      api.decideFixtureSet(ruleset.id, ruleset.versionNo, fixtureSet),
    onSuccess: (batch: BatchResult) => {
      client.setQueryData(keys.stats(ruleset.id, ruleset.versionNo), batch.aggregates)
    },
  })
}
