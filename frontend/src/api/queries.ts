import {
  skipToken,
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryResult,
} from '@tanstack/react-query'
import { api } from './client'
import type {
  Aggregates,
  Audience,
  AuditEntry,
  BatchResult,
  BudgetResponse,
  ChatConversationResponse,
  ChatSessionSummary,
  Decision,
  Diff,
  GapResolution,
  PolicyResponse,
  PolicySummary,
  ProposedDecision,
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
  budget: ['budget'] as const,
  policies: ['policies'] as const,
  policy: (policyId: string) => ['policy', policyId] as const,
  rulesets: ['rulesets'] as const,
  version: (rulesetId: string, versionNo: number) => ['version', rulesetId, versionNo] as const,
  stats: (rulesetId: string, versionNo: number) => ['stats', rulesetId, versionNo] as const,
  run: (rulesetId: string, versionNo: number) => ['run', rulesetId, versionNo] as const,
  decision: (decisionId: string) => ['decision', decisionId] as const,
  proposedDecision: (changeId: string, decisionId: string) =>
    ['proposed-decision', changeId, decisionId] as const,
  audit: (versionId: string) => ['audit', versionId] as const,
  chatSessions: ['chat-sessions'] as const,
  chatSession: (id: string) => ['chat-session', id] as const,
  diff: (rulesetId: string, from: number, to: number) => ['diff', rulesetId, from, to] as const,
}

/** The provider the API runs on (Document 2, GET /system/provider); it changes only with a deployment. */
export function useProvider(): UseQueryResult<ProviderResponse> {
  return useQuery({ queryKey: keys.provider, queryFn: () => api.provider(), staleTime: Infinity })
}

/**
 * The day's token budget (Document 2, GET /system/budget; Document 5, the banner of the degradation order). It is read
 * when a screen that calls a model opens, and again when a call finds it spent (BUDGET_EXHAUSTED).
 */
export function useBudget(): UseQueryResult<BudgetResponse> {
  return useQuery({ queryKey: keys.budget, queryFn: () => api.budget() })
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
 * the trace is the engine's, the explanation is a model's reading of it, and the reader chooses to see it. A decision
 * that was never stored, such as what a proposal decides, has none to ask for.
 */
export function useExplain(decisionId: string | null) {
  return useMutation({
    mutationFn: (audience: Audience) => api.explain(decisionId ?? '', audience),
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
 * What a proposal decides for one of the sandbox's stored decisions, with the trace (Document 2, the proposed side of a
 * flipped case). The request's stored patches never change, so neither does the answer.
 */
export function useProposedDecision(
  flipped: { changeId: string; decisionId: string } | null,
): UseQueryResult<ProposedDecision> {
  return useQuery({
    queryKey: keys.proposedDecision(flipped?.changeId ?? 'none', flipped?.decisionId ?? 'none'),
    queryFn: () => api.proposedDecision(flipped?.changeId ?? '', flipped?.decisionId ?? ''),
    enabled: flipped !== null,
    staleTime: Infinity,
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

/**
 * The audit log, newest first (Document 2, GET /audit): one version's entries, or, without a version, every entry the
 * sandbox can see; null while the version to read is not known yet.
 */
export function useAudit(scope: { versionId: string | null } | null): UseQueryResult<AuditEntry[]> {
  return useQuery({
    queryKey: keys.audit(scope === null ? 'none' : (scope.versionId ?? 'all')),
    queryFn: async () => (await api.audit(scope?.versionId ?? null)).entries,
    enabled: scope !== null,
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

/**
 * Deciding one case the officer typed (Document 2, decide: one `case`, the full decision with its trace back, or 422
 * CASE_INVALID with nothing stored). The decision is recorded like any other, so it is cached under its id for the
 * trace to open, and the version's statistics, which count it, are read again.
 */
export function useDecideCase(ruleset: { id: string; versionNo: number }) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (input: Record<string, unknown>) =>
      api.decideCase(ruleset.id, ruleset.versionNo, input),
    onSuccess: async (decided: Decision) => {
      client.setQueryData(keys.decision(decided.id), decided)
      await client.invalidateQueries({ queryKey: keys.stats(ruleset.id, ruleset.versionNo) })
    },
  })
}

/**
 * Running a seeded set of cases; the statistics of the version are refreshed with the batch's own aggregates, and the
 * run is kept for the session, so the Cases screen lists it again and the palette reaches its cases by number.
 */
export function useRunFixtureSet(ruleset: { id: string; versionNo: number }) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (fixtureSet: string) =>
      api.decideFixtureSet(ruleset.id, ruleset.versionNo, fixtureSet),
    onSuccess: (batch: BatchResult) => {
      client.setQueryData(keys.stats(ruleset.id, ruleset.versionNo), batch.aggregates)
      client.setQueryData(keys.run(ruleset.id, ruleset.versionNo), batch)
    },
  })
}

/** The sandbox's conversations, newest first (Document 2, GET /chat/sessions); a finished answer refreshes them. */
export function useChatSessions(): UseQueryResult<ChatSessionSummary[]> {
  return useQuery({
    queryKey: keys.chatSessions,
    queryFn: () => api.chatSessions().then((response) => response.sessions),
  })
}

/** A conversation as it was shown, to open it again (Document 2, GET /chat/sessions/{id}). */
export function useChatSession(id: string | null): UseQueryResult<ChatConversationResponse> {
  return useQuery({
    queryKey: keys.chatSession(id ?? 'none'),
    queryFn: () => api.chatSession(id ?? ''),
    enabled: id !== null,
  })
}

/**
 * This session's last run of the cases on a version, if there was one (the owner's answer to phase 6's second
 * question): read from what the run left, never asked of the API, and kept for the session, which drops it when it
 * closes (the spec, section 11, Gate, v3.9).
 */
export function useLastRun(
  ruleset: { id: string; versionNo: number } | null,
): UseQueryResult<BatchResult> {
  return useQuery({
    queryKey: keys.run(ruleset?.id ?? 'none', ruleset?.versionNo ?? 0),
    queryFn: skipToken,
    staleTime: Infinity,
    gcTime: Infinity,
  })
}
