import englishRules from '../../../../fixtures/eval/policies/consumer-lending-en/expected.ruleset.json'
import englishText from '../../../../fixtures/eval/policies/consumer-lending-en/policy.en.md?raw'
import notCoveredFile from '../../../../backend/src/main/resources/prompts/answer/not-covered.yml?raw'
import type { Decision, PolicyResponse, RuleSetDocument } from '../../api/types'
import { decision } from '../msw/handlers'

/**
 * The left-to-right half of the RTL pass (NFR-5; Work Plan day 16: "every screen in RTL and LTR"): the labeled set's
 * English lending policy and its expected rule set, both committed (fixtures/eval/policies/consumer-lending-en), and
 * Document 4's not-covered sentence in each language, read from the file the API itself serves it from
 * (backend/src/main/resources/prompts/answer/not-covered.yml). Nothing here is retyped.
 */

export const ENGLISH_POLICY_ID = '0f4c1c9e-0000-4000-8000-0000000000a4'
export const ENGLISH_RULESET_ID = '0f4c1c9e-0000-4000-8000-0000000000b4'

export const englishRuleSet = englishRules as unknown as RuleSetDocument

export const englishParagraphs: { index: number; text: string }[] = englishText
  .split('\n\n')
  .map((block) => block.trim())
  .filter((block) => block !== '')
  .map((text, position) => ({ index: position + 1, text }))

export const englishPolicy: PolicyResponse = {
  id: ENGLISH_POLICY_ID,
  title: englishRuleSet.name,
  language: 'en',
  protected: false,
  createdAt: '2026-09-28T09:00:00Z',
  versions: [{ versionNo: 1, createdAt: '2026-09-28T09:00:00Z', paragraphs: englishParagraphs }],
}

/** Document 4's fixed sentence in one language, as the API holds it: a quoted string on the line of its language. */
export function notCovered(language: 'he' | 'en'): string {
  const line = notCoveredFile
    .split('\n')
    .find((candidate) => candidate.startsWith(`${language}: `))!
  return JSON.parse(line.slice(language.length + 2)) as string
}

function englishRule(id: string) {
  return englishRuleSet.rules.find((rule) => rule.id === id)!
}

/**
 * Case 17 decided on the English policy, told as the engine tells it (Document 3, Trace): the steps of the decision the
 * component tests use, each with the English rule's own label, provenance and threshold. Under the English minimum
 * income of 7,000 the case still passes R-170, and R-330 still refers it for its one credit event without a guarantor.
 */
export const englishDecision: Decision = {
  ...decision,
  reason:
    englishRule('R-330')
      .actions.map((action) => action.reason)
      .find(Boolean) ?? '',
  trace: decision.trace.map((step) => {
    const rule = englishRule(step.ruleId)
    const comparisons = step.comparisons?.map((comparison) =>
      step.ruleId === 'R-170'
        ? { ...comparison, expected: (rule.condition as { value: number }).value }
        : comparison,
    )
    return {
      ...step,
      label: rule.label,
      ...(comparisons ? { comparisons } : {}),
      ...(step.provenance ? { provenance: rule.provenance } : {}),
    }
  }),
  rulesetVersion: { ...decision.rulesetVersion, id: englishRuleSet.id },
}
