import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Finding, Rule } from '../../api/types'
import { specRules, stylesheet, unported } from '../../test/css'
import { lendingParagraphs, lendingRuleSet } from '../../test/fixtures/lending'
import { RuleDrawer } from './RuleDrawer'

// @requirement FR-4

/**
 * The Rules margin with a rule chosen (the spec, section 10, the Rules screen's margin; Document 9, phase 3): the rule
 * section, the paragraph it cites with the quoted span marked, and the findings on it. The rule and the paragraph are
 * the committed lending fixture's own.
 */

function rule(id: string): Rule {
  const found = lendingRuleSet.rules.find((candidate) => candidate.id === id)
  if (!found) {
    throw new Error(`the fixture has no rule ${id}`)
  }
  return found
}

const paragraphOne = lendingParagraphs.find((paragraph) => paragraph.index === 1)!

function renderDrawer(props: Partial<Parameters<typeof RuleDrawer>[0]> = {}) {
  return render(
    <RuleDrawer
      rule={rule('R-110')}
      language="he"
      versionStatus="DRAFT"
      paragraph={paragraphOne}
      findings={[]}
      reviewFindings={[]}
      reviewTotal={0}
      editable={false}
      acknowledging={null}
      onAcknowledge={() => undefined}
      onSelectRule={() => undefined}
      onShowParagraph={() => undefined}
      {...props}
    />,
  )
}

/** A margin section, found by the words its title row starts with. */
function section(title: string): HTMLElement {
  const heading = [...document.querySelectorAll('.margin__title')].find((one) =>
    (one.textContent ?? '').startsWith(title),
  )
  if (!heading) {
    throw new Error(`no margin section titled ${title}`)
  }
  return heading.closest<HTMLElement>('.margin__section')!
}

describe('RuleDrawer, the rule section', () => {
  it('names the rule by its chip and its version state, and reads the Hebrew label right to left', () => {
    renderDrawer()

    const ruleSection = section('Rule')
    expect(within(ruleSection).getByText('R-110')).toHaveClass('chip', 'chip--id', 'chip--active')
    expect(within(ruleSection).getByText('Draft')).toHaveClass('vstatus', 'vstatus--draft')
    const label = within(ruleSection).getByText(rule('R-110').label)
    expect(label).toHaveAttribute('dir', 'rtl')
    expect(label).toHaveAttribute('lang', 'he')
  })

  it('writes the condition, the action with terminal, and the reason for the applicant', () => {
    renderDrawer()

    const facts = section('Rule')
    const value = (term: string) => within(facts).getByText(term).nextElementSibling
    // Document 3's grammar, the unit left to the table's header
    expect(value('Condition')).toHaveTextContent('age > 70 AND employment_type ≠ retired')
    expect(within(value('Action') as HTMLElement).getByText('Decline')).toHaveClass('tag--decline')
    expect(value('Action')).toHaveTextContent('Decline terminal')
    expect(value('Reason for the applicant')).toHaveTextContent('גיל המבקש עולה על גיל המקסימום 70')
    expect(value('Reason for the applicant')).toHaveAttribute('dir', 'rtl')
  })

  it("writes the priority with its band, the source with the model's confidence, the last run and since when", () => {
    renderDrawer({ decided: { count: 4, decisions: 200 }, since: 'v1 · unchanged' })

    const facts = section('Rule')
    const value = (term: string) => within(facts).getByText(term).nextElementSibling
    expect(value('Priority')).toHaveTextContent('110 · Hard eligibility gates')
    // fixtures/policies/consumer-lending/ruleset.v1.json: R-110 quoted with confidence 0.9, the spec's "0.90"
    expect(value('Source')).toHaveTextContent('model · confidence 0.90')
    expect(value('Source')?.querySelector('.actor--model')).not.toBeNull()
    expect(value('Decided in the last run')).toHaveTextContent('4 of 200 cases')
    expect(value('Since')).toHaveTextContent('v1 · unchanged')
  })

  it('leaves out the last run for a rule the statistics do not name', () => {
    // the owner's answer of 2026-09-28 to phase 3's fourth question: the statistics name the five that decided most
    renderDrawer({ decided: undefined })

    expect(within(section('Rule')).queryByText('Decided in the last run')).not.toBeInTheDocument()
  })

  it('names who wrote a rule an analyst wrote, and a pending one', () => {
    const { rerender } = renderDrawer({ rule: rule('R-310') })

    expect(within(section('Rule')).getByText('Source').nextElementSibling).toHaveTextContent(
      'analyst',
    )
    rerender(
      <RuleDrawer
        rule={{
          ...rule('R-900'),
          provenance: { kind: 'pending', changeRequestId: 'CR-1', rationale: 'r' },
        }}
        language="he"
        versionStatus="DRAFT"
        findings={[]}
        reviewFindings={[]}
        reviewTotal={0}
        editable={false}
        acknowledging={null}
        onAcknowledge={() => undefined}
        onSelectRule={() => undefined}
        onShowParagraph={() => undefined}
      />,
    )
    expect(within(section('Rule')).getByText('Source').nextElementSibling).toHaveTextContent(
      'Pending',
    )
  })
})

describe('RuleDrawer, whether the rule runs', () => {
  // Document 9, known tensions: Document 3's enabled toggle is a checkbox of the margin's rule section, on a draft only
  it('carries Enabled on a draft, says what it means, and asks to skip the rule when unchecked', async () => {
    const user = userEvent.setup()
    const onToggleEnabled = vi.fn()
    renderDrawer({ editable: true, onToggleEnabled })

    const enabled = within(section('Rule')).getByRole('checkbox', { name: 'Enabled' })
    expect(enabled).toBeChecked()
    expect(
      screen.getByText('This rule runs; unchecked, the engine skips it and the table dims it'),
    ).toBeInTheDocument()
    await user.click(enabled)
    expect(onToggleEnabled).toHaveBeenCalledExactlyOnceWith(false)
  })

  it('shows a rule switched off unchecked, and no control on a version that is not a draft', () => {
    const { rerender } = renderDrawer({
      rule: { ...rule('R-110'), enabled: false },
      editable: true,
      onToggleEnabled: () => undefined,
    })

    expect(screen.getByRole('checkbox', { name: 'Enabled' })).not.toBeChecked()
    rerender(
      <RuleDrawer
        rule={rule('R-110')}
        language="he"
        versionStatus="PUBLISHED"
        findings={[]}
        reviewFindings={[]}
        reviewTotal={0}
        editable={false}
        acknowledging={null}
        onAcknowledge={() => undefined}
        onSelectRule={() => undefined}
        onShowParagraph={() => undefined}
      />,
    )
    expect(screen.queryByRole('checkbox', { name: 'Enabled' })).not.toBeInTheDocument()
  })
})

describe('RuleDrawer, the paragraph it cites', () => {
  it('shows the paragraph with the quoted span marked', async () => {
    const user = userEvent.setup()
    const onShowParagraph = vi.fn()
    renderDrawer({ onShowParagraph })

    const policy = section('Policy')
    expect(policy.querySelector('.margin__title .chip--para')).toHaveTextContent(/^¶\s1$/)
    // R-110's quote, "גילו 21 עד 70 בעת הגשת הבקשה", inside paragraph 1
    const marked = policy.querySelector<HTMLElement>('mark')!
    expect(marked).toHaveTextContent('גילו 21 עד 70 בעת הגשת הבקשה')
    expect(marked.closest('p')).toHaveTextContent(paragraphOne.text)
    expect(marked.closest('p')).toHaveAttribute('dir', 'rtl')
    await user.click(within(policy).getByRole('button', { name: 'Open the policy' }))
    expect(onShowParagraph).toHaveBeenCalledExactlyOnceWith(1)
  })
})

describe('RuleDrawer, the findings on the rule', () => {
  const conflict = {
    id: 'F-2',
    kind: 'conflict' as const,
    severity: 'error' as const,
    ruleIds: ['R-110', 'R-115'],
    paragraphIndexes: [1, 8],
    message: 'סעיף 1 מגביל את גיל כל המבקשים ל-70, בעוד סעיף 8 מתיר לגמלאים עד גיל 75.',
    suggestion: 'יש להבהיר אם סעיף 8 הוא חריג לסעיף 1.',
    confidence: 0.9,
    blocking: true,
  }
  const validated: Finding = {
    code: 'DSL-311',
    severity: 'warning',
    path: '/rules/3',
    message: 'the rule is unreachable',
    ruleIds: ['R-110'],
    fieldNames: [],
  }

  it("counts the rule's findings of the review's, and leads back to the whole review", async () => {
    const user = userEvent.setup()
    const onOpenReview = vi.fn()
    renderDrawer({
      reviewFindings: [conflict],
      reviewTotal: 5,
      findings: [validated],
      onOpenReview,
    })

    const onRule = section('Findings on this rule')
    expect(onRule.querySelector('.margin__title')).toHaveTextContent('Findings on this rule1 of 5')
    expect(within(onRule).getByText('Conflict')).toHaveClass('finding__kind')
    expect(within(onRule).getByText('DSL-311')).toBeInTheDocument()
    expect(within(onRule).getByText('the rule is unreachable')).toBeInTheDocument()
    await user.click(within(onRule).getByRole('button', { name: 'All 5, in the review' }))
    expect(onOpenReview).toHaveBeenCalledOnce()
  })

  it('has no findings section when nothing names the rule', () => {
    renderDrawer()

    expect(screen.queryByText('Findings on this rule')).not.toBeInTheDocument()
  })
})

describe('RuleDrawer.css', () => {
  it("carries the spec's label-over-value lists, with the spec's declarations", () => {
    const lists = specRules('/* Policy document and its list */', '/* Access gate').filter(
      ([selector]) => selector.startsWith('.kv'),
    )

    expect(lists).toHaveLength(6)
    expect(unported(stylesheet('features/rules/RuleDrawer.css'), lists)).toEqual([])
  })
})
