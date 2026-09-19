import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'
import { api } from './client'
import type {
  PolicyResponse,
  PolicySummary,
  RulesetSummary,
  VersionResponse,
  RuleSetDocument,
} from './types'

/**
 * Server state, and only server state (Document 2, Frontend Architecture, key decision 1): every screen is a query
 * or a mutation, and a publication or an edit invalidates the keys that showed the old version.
 */

export const keys = {
  policies: ['policies'] as const,
  policy: (policyId: string) => ['policy', policyId] as const,
  rulesets: ['rulesets'] as const,
  version: (rulesetId: string, versionNo: number) => ['version', rulesetId, versionNo] as const,
  stats: (rulesetId: string, versionNo: number) => ['stats', rulesetId, versionNo] as const,
  decision: (decisionId: string) => ['decision', decisionId] as const,
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
