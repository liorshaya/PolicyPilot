import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Finding, RuleSetDocument } from '../../api/types'
import { specRules, stylesheet, unported } from '../../test/css'
import { lendingRuleSet } from '../../test/fixtures/lending'
import { FieldsPanel } from './FieldsPanel'

// @requirement FR-6

/**
 * Fields, the analyst's view of the version's schema (the spec, section 09, "Decide a case, and the field schema"): every
 * name with its meaning, its type and values, and the paragraph that implied it, which is where an invented field
 * would show; the validator's FIELD_UNUSED stands beside a field no rule reads (Document 3). Expected: the spec's
 * schema specimen, drawn from the lending rule set's fields (fixtures/policies/consumer-lending/ruleset.v1.json).
 */

/** U+00A0, built from its code point because it passes for a space in the source. */
const NO_BREAK_SPACE = String.fromCodePoint(0x00a0)

function renderPanel(
  document: RuleSetDocument = lendingRuleSet,
  findings: Finding[] = [],
  onShowParagraph: (index: number) => void = () => undefined,
) {
  return render(
    <FieldsPanel document={document} findings={findings} onShowParagraph={onShowParagraph} />,
  )
}

/** The schema's rows, in the order they stand. */
function rows(): HTMLElement[] {
  return [...document.querySelectorAll<HTMLElement>('.schema__row')]
}

describe('FieldsPanel', () => {
  it('writes every field of the version with its name in mono, then its type, unit and presence', () => {
    renderPanel()

    expect(
      rows().map((row) => [
        row.querySelector('.schema__head > .mono')!.textContent,
        row.querySelector('.schema__head > .muted')!.textContent,
      ]),
    ).toEqual([
      ['age', 'integer · years · required · 0 to 120'],
      ['requested_amount', 'number · ₪ · required'],
      ['term_months', 'integer · months · required · at least 1'],
      ['employment_type', 'enum · required'],
      ['employment_months', 'integer · months · optional'],
      ['monthly_income', 'number · ₪ · required'],
      ['existing_monthly_debt', 'number · ₪ · required'],
      ['credit_events_24m', 'integer · required'],
      ['has_guarantor', 'boolean · optional, false when absent'],
      ['monthly_installment', 'number · ₪ · derived by R-010'],
      ['debt_to_income', 'number · derived by R-020'],
    ])
  })

  it('writes each Hebrew description under the name, and the values of an enum', () => {
    renderPanel()

    const age = rows()[0]!
    const description = age.querySelector('.he-label')!
    expect(description.tagName).toBe('BDI')
    expect(description).toHaveAttribute('lang', 'he')
    expect(description).toHaveAttribute('dir', 'rtl')
    expect(description).toHaveTextContent(/^גיל המבקש בעת הגשת הבקשה$/)
    expect(rows()[3]!.querySelector('.schema__values')).toHaveTextContent(
      /^salaried · self_employed · retired · unemployed$/,
    )
    expect(age.querySelector('.schema__values')).toBeNull()
  })

  // The spec, section 09: "the paragraph that implied it", a chip that opens the paragraph
  it('names the paragraph that implied each case field, and opens it', async () => {
    const onShowParagraph = vi.fn()
    renderPanel(lendingRuleSet, [], onShowParagraph)

    expect(
      rows()
        .slice(0, 9)
        .map((row) => row.querySelector('.chip--para')!.textContent),
    ).toEqual([1, 2, 2, 3, 3, 4, 6, 7, 7].map((index) => `¶${NO_BREAK_SPACE}${String(index)}`))
    await userEvent.click(within(rows()[5]!).getByRole('button', { name: 'Paragraph 4' }))
    expect(onShowParagraph).toHaveBeenCalledWith(4)
  })

  it('marks a derived field with the dashed tag, and gives it no paragraph', () => {
    renderPanel()

    const derived = rows().slice(9)
    expect(derived.map((row) => row.querySelector('.derived-tag')?.textContent)).toEqual([
      'derived',
      'derived',
    ])
    expect(derived.every((row) => row.querySelector('.chip--para') === null)).toBe(true)
    expect(rows()[0]!.querySelector('.derived-tag')).toBeNull()
  })

  it('counts the case fields and the derived ones in its title', () => {
    renderPanel()

    const section = screen.getByRole('region', { name: 'Fields' })
    expect(within(section).getByText('9 case fields, 2 derived')).toHaveClass('quiet')
  })

  // Document 3: "a field with no source and no use in any rule is flagged by the validator as FIELD_UNUSED"
  it("stands the validator's FIELD_UNUSED beside a field no rule reads", () => {
    const invented: RuleSetDocument = {
      ...lendingRuleSet,
      fields: [
        ...lendingRuleSet.fields,
        { name: 'marital_status', type: 'string', description: 'מצב משפחתי' },
      ],
    }
    renderPanel(invented, [
      {
        code: 'FIELD_UNUSED',
        severity: 'warning',
        path: '/fields/11',
        message: 'marital_status is not read by any rule',
        ruleIds: [],
        fieldNames: ['marital_status'],
      },
    ])

    const row = rows().find((one) => one.textContent.startsWith('marital_status'))!
    const mark = row.querySelector('.sev')!
    expect(mark).toHaveTextContent(/^FIELD_UNUSED$/)
    expect(mark).toHaveClass('sev--warning')
    expect(mark).toHaveAttribute('title', 'marital_status is not read by any rule')
    expect(row.querySelector('.chip--para')).toBeNull()
    expect(rows()[0]!.querySelector('.sev')).toBeNull()
  })
})

/**
 * The spec (v3.8), section 09: on a draft, a case field that is neither derived nor given a default carries Required,
 * and the section says how many are optional. Expected: ruleset.v1.json declares eight such fields, of which
 * employment_months alone is optional; has_guarantor has a default and the two derived fields are the rules' own.
 */
describe('FieldsPanel on a draft', () => {
  const HINT = 'A case without it is refused; unchecked, every comparison on it reads false'

  function renderDraft(
    document: RuleSetDocument = lendingRuleSet,
    onRequire: (field: string, required: boolean) => void = () => undefined,
  ) {
    return render(
      <FieldsPanel
        document={document}
        findings={[]}
        onShowParagraph={() => undefined}
        onRequire={onRequire}
      />,
    )
  }

  it('gives Required to each case field with neither a derivation nor a default, checked as the field is', () => {
    renderDraft()

    const boxes = screen.getAllByRole('checkbox', { name: 'Required' })
    expect(
      boxes.map((box) => [
        box.closest('.schema__row')!.querySelector('.schema__head > .mono')!.textContent,
        (box as HTMLInputElement).checked,
      ]),
    ).toEqual([
      ['age', true],
      ['requested_amount', true],
      ['term_months', true],
      ['employment_type', true],
      ['employment_months', false],
      ['monthly_income', true],
      ['existing_monthly_debt', true],
      ['credit_events_24m', true],
    ])
    expect(boxes[0]).toHaveAccessibleDescription(HINT)
  })

  it('asks for the field to be required, and for it to be optional again', async () => {
    const user = userEvent.setup()
    const onRequire = vi.fn()
    renderDraft(lendingRuleSet, onRequire)
    const row = rows().find((one) => one.textContent.startsWith('employment_months'))!

    await user.click(within(row).getByRole('checkbox', { name: 'Required' }))
    await user.click(rows()[0]!.querySelector<HTMLElement>('input[type="checkbox"]')!)

    expect(onRequire.mock.calls).toEqual([
      ['employment_months', true],
      ['age', false],
    ])
  })

  it('says how many case fields are optional with no default, in the singular for one', () => {
    renderDraft()

    expect(screen.getByRole('region', { name: 'Fields' }).querySelector('.note')).toHaveTextContent(
      '1 case field is optional and has no default: a case without it is not refused, and every comparison on it reads false.',
    )
  })

  // the new-policy walk of 2026-10-01: the model declared six inputs and marked none required
  it('counts every one when none is required', () => {
    renderDraft({
      ...lendingRuleSet,
      fields: lendingRuleSet.fields.map((field) =>
        field.derived === true ? field : { ...field, required: false },
      ),
    })

    expect(screen.getByRole('region', { name: 'Fields' }).querySelector('.note')).toHaveTextContent(
      '8 case fields are optional and have no default: a case without one is not refused, and every comparison on it reads false.',
    )
  })

  it('says nothing when every such field is required', () => {
    renderDraft({
      ...lendingRuleSet,
      fields: lendingRuleSet.fields.map((field) =>
        field.name === 'employment_months' ? { ...field, required: true } : field,
      ),
    })

    expect(screen.getByRole('region', { name: 'Fields' }).querySelector('.note')).toBeNull()
  })

  it('offers no Required and no note on a published or seeded version', () => {
    renderPanel()

    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Fields' }).querySelector('.note')).toBeNull()
  })
})

describe('FieldsPanel.css', () => {
  it("carries the spec's field schema, with the spec's declarations", () => {
    const schema = specRules('.schema {', '.barlist__id .dot')

    expect(schema.map(([selector]) => selector)).toEqual([
      '.schema',
      '.schema__row',
      '.schema__row:last-child',
      '.schema__head',
      '.schema__head .mono',
      '.schema__row .he-label',
      '.derived-tag',
    ])
    expect(unported(stylesheet('features/rules/FieldsPanel.css'), schema)).toEqual([])
  })
})
