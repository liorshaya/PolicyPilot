import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import type { Decision } from '../../api/types'
import { specRules, stylesheet, unported } from '../../test/css'
import { lendingCase17, lendingRuleSet, sampleDecision } from '../../test/fixtures/lending'
import { SEEDED_RULESET_ID } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { CaseForm } from './CaseForm'

// @requirement FR-8

/**
 * Decide a case, the credit officer's form (the spec, section 09, "Decide a case, and the field schema"; Document 2,
 * decide: one `case` to a published version, the full decision back, or 422 CASE_INVALID with nothing stored). The
 * form is built from the lending rule set's fields (fixtures/policies/consumer-lending/ruleset.v1.json), and case 17
 * of the seeded set is what an officer types into it.
 */

const BASE = 'http://localhost:8080/api/v1'
/** U+2212, built from its code point because it passes for a hyphen in the source. */
const MINUS_SIGN = String.fromCodePoint(0x2212)
const CASE_FIELDS = lendingRuleSet.fields.filter((field) => field.derived !== true)

function renderForm(onDecided: (decision: Decision) => void = () => undefined) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <CaseForm
        fields={lendingRuleSet.fields}
        language="he"
        version={{ id: SEEDED_RULESET_ID, versionNo: 1 }}
        onDecided={onDecided}
        onClose={() => undefined}
      />
    </QueryClientProvider>,
  )
}

/** A field's control, found by the Hebrew description its label begins with. */
function control(name: string): HTMLElement {
  const field = lendingRuleSet.fields.find((one) => one.name === name)!
  return screen.getByLabelText(field.description!, { exact: false })
}

/** Types case 17 of the seeded set into the form, as an officer would. */
async function typeCase17() {
  const user = userEvent.setup()
  for (const [name, value] of Object.entries(lendingCase17)) {
    const input = control(name)
    if (input instanceof HTMLSelectElement) {
      await user.selectOptions(input, String(value))
    } else {
      await user.type(input, String(value))
    }
  }
  return user
}

describe('CaseForm', () => {
  // The spec, section 09: "one field per line in schema order"; the derived fields are the engine's to compute
  it("lays out one field per line in the schema's order, and never a derived one", () => {
    renderForm()

    const names = [...document.querySelectorAll('.case-form .mono')].map((one) => one.textContent)
    expect(names).toEqual(CASE_FIELDS.map((field) => field.name))
    expect(names).not.toContain('monthly_installment')
    expect(names).not.toContain('debt_to_income')
  })

  // The spec, section 09: "the description first, then the name with its type, unit and domain"
  it('writes the Hebrew description first, and the mono name with its type, unit and domain under it', () => {
    renderForm()

    const label = document.querySelector<HTMLElement>('label[for$="-age"]')!
    const description = label.firstElementChild!
    expect(description.tagName).toBe('BDI')
    expect(description).toHaveClass('he-label')
    expect(description).toHaveAttribute('lang', 'he')
    expect(description).toHaveAttribute('dir', 'rtl')
    expect(description).toHaveTextContent(/^גיל המבקש בעת הגשת הבקשה$/)
    const id = label.querySelector('.field__id')!
    expect(id.textContent).toBe('age · integer · years · required · 0 to 120')
    expect(id.querySelector('.mono')).toHaveTextContent(/^age$/)
    expect(control('age')).toHaveClass('input', 'input--mono')
    expect(control('age')).toHaveAttribute('inputmode', 'numeric')
  })

  // the spec (v3.9), section 09: a number field asks a phone for the keypad with the decimal point, an integer field
  // for the digits; every number field had the digits alone, so 9,500.5 could not be typed on a phone
  it('asks a phone for the decimal keypad on a number field and the digits on an integer field', () => {
    renderForm()

    expect(control('monthly_income')).toHaveAttribute('inputmode', 'decimal')
    expect(control('requested_amount')).toHaveAttribute('inputmode', 'decimal')
    expect(control('term_months')).toHaveAttribute('inputmode', 'numeric')
  })

  // The spec, section 09: "an enum as a select, a boolean as a checkbox with a sentence"
  it('offers an enum as a select of its values and a boolean as a checkbox with its sentence', () => {
    renderForm()

    const select = control('employment_type') as HTMLSelectElement
    expect(select.tagName).toBe('SELECT')
    expect([...select.options].map((option) => option.value)).toEqual([
      '',
      'salaried',
      'self_employed',
      'retired',
      'unemployed',
    ])
    const box = screen.getByRole('checkbox')
    const sentence = box.closest('label')!
    expect(sentence).toHaveClass('check')
    expect(sentence.querySelector('.he-label')).toHaveTextContent(/^האם הועמד ערב$/)
    expect(sentence.querySelector('.mono')).toHaveTextContent(/^has_guarantor$/)
    expect(box).not.toBeChecked()
  })

  it('marks every required field and says which are optional', () => {
    renderForm()

    const lines = [...document.querySelectorAll('.case-form .field__id')].map(
      (one) => one.textContent,
    )
    expect(lines.filter((line) => line.includes(' · required'))).toHaveLength(7)
    expect(lines.find((line) => line.startsWith('employment_months'))).toBe(
      'employment_months · integer · months · optional',
    )
  })

  // The spec, section 09: the foot and the one action
  it('closes with the foot that says which version decides, and Decide', () => {
    renderForm()

    const foot = document.querySelector<HTMLElement>('.case-form__foot')!
    expect(foot.querySelector('.muted')!.textContent).toBe(
      'Decided by v1, the published version, and recorded like any other decision.',
    )
    expect(within(foot).getByRole('button', { name: 'Decide' })).toHaveClass('btn', 'btn--primary')
  })

  // Document 2, decide: "one case returns the full decision with its trace". Expected: case 17's input as
  // fixtures/policies/consumer-lending/cases-200.json holds it, the guarantor left out as the fixture leaves it
  it('posts one case to the published version and hands its decision on to open the trace', async () => {
    let sent: unknown = null
    let path = ''
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, async ({ request, params }) => {
        sent = await request.json()
        path = `${String(params.id)}/${String(params.no)}`
        return HttpResponse.json({ ...sampleDecision, caseNo: undefined })
      }),
    )
    const onDecided = vi.fn()
    renderForm(onDecided)

    const user = await typeCase17()
    await user.click(screen.getByRole('button', { name: 'Decide' }))

    await waitFor(() => expect(onDecided).toHaveBeenCalledOnce())
    expect(sent).toEqual({ case: lendingCase17 })
    expect(path).toBe(`${SEEDED_RULESET_ID}/1`)
    expect(onDecided.mock.calls[0]![0]).toMatchObject({ id: sampleDecision.id, outcome: 'refer' })
  })

  // The spec's specimen writes amounts with their thousands separator ("72,000"), and a minus is U+2212 (section 03)
  it('reads a number as the officer writes it, with its separator or a true minus', async () => {
    let sent: { case: Record<string, unknown> } | null = null
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, async ({ request }) => {
        sent = (await request.json()) as { case: Record<string, unknown> }
        return HttpResponse.json(sampleDecision)
      }),
    )
    renderForm()
    const user = userEvent.setup()

    await user.type(control('requested_amount'), '72,000')
    await user.type(control('existing_monthly_debt'), `${MINUS_SIGN}500`)
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: 'Decide' }))

    await waitFor(() => expect(sent).not.toBeNull())
    // TypeScript narrows `sent` to null here, not seeing the handler assign it
    const body = sent as { case: Record<string, unknown> } | null
    expect(body?.case).toEqual({
      requested_amount: 72000,
      existing_monthly_debt: -500,
      has_guarantor: true,
    })
  })

  // The edge cases of 2026-10-01: a comma is a thousands separator only between groups of three, and only decimal
  // digits make a number. "1,5" had gone as 15 and "0x10" as 16; each now goes as it was typed, and the API names it
  it('sends a value that is not a plain decimal number as it was typed', async () => {
    const sent: Record<string, unknown>[] = []
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, async ({ request }) => {
        sent.push(((await request.json()) as { case: Record<string, unknown> }).case)
        return HttpResponse.json(sampleDecision)
      }),
    )
    renderForm()
    const user = userEvent.setup()
    const typed: [string, unknown][] = [
      ['1,5', '1,5'],
      ['12,34,56', '12,34,56'],
      ['0x10', '0x10'],
      ['1e3', '1e3'],
      ['1,234,567.5', 1234567.5],
      ['.5', 0.5],
      ['+7', 7],
    ]

    for (const [text] of typed) {
      await user.clear(control('requested_amount'))
      await user.type(control('requested_amount'), text)
      await user.click(screen.getByRole('button', { name: 'Decide' }))
      await waitFor(() => expect(sent).toHaveLength(typed.findIndex(([one]) => one === text) + 1))
    }

    expect(sent.map((one) => one.requested_amount)).toEqual(typed.map(([, value]) => value))
  })

  // Document 2: "an invalid case refuses the whole request with 422 CASE_INVALID and nothing is stored"; each detail
  // is the field's pointer and the problem's code (Document 3, case validation: CASE_OUT_OF_RANGE)
  it('names the refused field under its input, and says that nothing was stored', async () => {
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/decide`, () =>
        HttpResponse.json(
          {
            code: 'CASE_INVALID',
            message: "The case is not valid against the rule set's fields.",
            details: [{ path: '/case/term_months', problem: 'CASE_OUT_OF_RANGE' }],
            traceId: 't',
          },
          { status: 422 },
        ),
      ),
    )
    const onDecided = vi.fn()
    renderForm(onDecided)

    const user = await typeCase17()
    await user.clear(control('term_months'))
    await user.type(control('term_months'), '0')
    await user.click(screen.getByRole('button', { name: 'Decide' }))

    const term = control('term_months')
    await waitFor(() => expect(term).toHaveAttribute('aria-invalid', 'true'))
    const error = document.getElementById(`${term.id}-error`)!
    expect(error).toHaveTextContent('Outside its domain: at least 1. CASE_OUT_OF_RANGE')
    expect(within(error).getByText('CASE_OUT_OF_RANGE')).toHaveClass('mono')
    expect(control('age')).not.toHaveAttribute('aria-invalid')
    const refusal = document.querySelector<HTMLElement>('.refusal')!
    expect(refusal.querySelector('.refusal__head')!.textContent).toBe(
      'CASE_INVALIDThe case was refused whole.',
    )
    expect(refusal.querySelector('.refusal__foot')!.textContent).toBe('Nothing was stored.')
    expect(onDecided).not.toHaveBeenCalled()
  })
})

describe('CaseForm.css', () => {
  it("carries the spec's case form, with the spec's declarations, and the label the schema shares in Field.css", () => {
    const form = specRules('/* decide a case, and the field schema', '.schema {')

    expect(form.map(([selector]) => selector)).toEqual([
      '.case-form',
      '.case-form .field__label',
      '.he-label',
      '.field__id',
      '.case-form .check .he-label',
      '.case-form__foot',
    ])
    const shared = form.filter(([selector]) => selector === '.he-label')
    expect(unported(stylesheet('shared/ui/Field.css'), shared)).toEqual([])
    expect(
      unported(
        stylesheet('features/cases/CaseForm.css'),
        form.filter(([selector]) => selector !== '.he-label'),
      ),
    ).toEqual([])
  })
})
