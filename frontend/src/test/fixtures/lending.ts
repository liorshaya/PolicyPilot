import ruleSet from '../../../../fixtures/policies/consumer-lending/ruleset.v1.json'
import cases from '../../../../fixtures/policies/consumer-lending/cases-200.json'
import casesExpected from '../../../../fixtures/policies/consumer-lending/cases-expected.json'
import policyText from '../../../../fixtures/policies/consumer-lending/policy.he.md?raw'
import depositRules from '../../../../fixtures/eval/policies/rental-deposit-en/expected.ruleset.json'
import type { CaseResult, Outcome, RuleSetDocument } from '../../api/types'

/**
 * The committed demo fixtures, imported from {@code fixtures/} itself (Document 6, Fixtures and Test Data: the same
 * files the Java tests and the Python reference use). Nothing here is retyped, so a component test can never drift
 * from the data the API really serves.
 */
export const lendingRuleSet = ruleSet as unknown as RuleSetDocument

/**
 * A second rule set, so a screen cannot assume the sandbox holds one: an English policy from the committed
 * evaluation set, whose name and rules are unmistakably not the lending ones.
 */
export const depositRuleSet = depositRules as unknown as RuleSetDocument

export const lendingParagraphs: { index: number; text: string }[] = policyText
  .split('\n\n')
  .map((block) => block.trim())
  .filter((block) => block !== '')
  .map((text, position) => ({ index: position + 1, text }))

/** Case 17 of the demo fixture: one credit event, no guarantor (Document 3, Worked Example). */
export const lendingCase17: Record<string, unknown> =
  cases.cases.find((fixture) => fixture.id === 17)?.input ?? {}

/** A decision id per case, readable in a failure message: case 8 is ...000000000008. */
export const decisionIdOf = (caseNo: number): string =>
  `0f4c1c9e-0000-4000-8000-${String(caseNo).padStart(12, '0')}`

/**
 * The 200 seeded cases decided by version 1, as the batch answer lists them: the outcome, the deciding rule and the
 * flags of fixtures/policies/consumer-lending/cases-expected.json, case by case.
 */
export const lendingRun: CaseResult[] = casesExpected.cases.map((expected) => ({
  id: decisionIdOf(expected.id),
  caseNo: expected.id,
  status: 'OK',
  outcome: expected.outcome as Outcome,
  decidingRuleId: expected.decidingRuleId,
  flags: expected.flags,
}))
