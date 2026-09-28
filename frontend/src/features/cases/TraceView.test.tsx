import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Decision } from '../../api/types'
import { rule, specRules, stylesheet, unported } from '../../test/css'
import { lendingRuleSet, sampleDecision } from '../../test/fixtures/lending'
import { server } from '../../test/msw/server'
import { rtlSnapshot } from '../../test/rtlSnapshot'
import { TraceView } from './TraceView'

// @requirement FR-8

/**
 * The trace of one decision (the Register spec, section 09, "The trace"; Document 3, Trace format; Document 2, key
 * decision 4: the trace is rendered verbatim). The decision is case 17 as the engine decided it,
 * fixtures/policies/consumer-lending/sample-decision.json, and every word is the engine's or the glossary's.
 */

const BASE = 'http://localhost:8080/api/v1'
const css = stylesheet('features/cases/TraceView.css')

function renderTrace(decision: Decision = sampleDecision, onClose?: () => void) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <TraceView
        decision={decision}
        language="he"
        fields={lendingRuleSet.fields}
        onClose={onClose}
      />
    </QueryClientProvider>,
  )
}

const STEPS = 'Steps, in the order the engine walked them'

/** A step of the trace, found by the rule chip it heads. */
function stepOf(ruleId: string): HTMLElement {
  return within(screen.getByRole('list', { name: STEPS }))
    .getByText(ruleId)
    .closest('li') as HTMLElement
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('TraceView, the head', () => {
  it('heads the trace with the case, the outcome tag and the provenance line', () => {
    renderTrace()

    expect(screen.getByRole('heading', { level: 2, name: 'Case 17' })).toBeInTheDocument()
    const head = document.querySelector<HTMLElement>('.trace__head')!
    expect(within(head).getByText('Manual review')).toHaveClass('tag', 'tag--refer')
    expect(within(head).getByText('Manual review')).not.toHaveClass('tag--quiet')
    // the spec's Cases screen, section 10: "decided on v1 · engine 1.0.0 · 61 µs · 10:14:07", with no version (the
    // owner's answer of 2026-09-28 to phase 3's second question); the date stays in the time element
    expect(
      [...head.querySelectorAll('.prov > *')].map((segment) => segment.textContent),
    ).toStrictEqual(['decided on v1', 'engine · 61 µs', '10:14:07'])
    expect(head.querySelector('.prov .actor--engine')).not.toBeNull()
    const time = head.querySelector('.prov time')
    expect(time).toHaveAttribute('datetime', '2026-09-23T10:14:07Z')
    expect(time).toHaveAttribute('title', '2026-09-23 10:14:07')
  })

  it('draws the hit map, one cell per rule in the order the engine walked them, the deciding one ringed', () => {
    renderTrace()

    const map = screen.getByRole('img', {
      name: '20 rules: 3 matched, 14 did not match, 3 not reached',
    })
    const cells = [...map.querySelectorAll('.hitmap__cell')]
    expect(cells).toHaveLength(20)
    expect(cells[0]).toHaveClass('hitmap__cell--matched')
    expect(cells[0]).toHaveAttribute('title', 'R-010 · Matched')
    expect(cells[2]).toHaveAttribute('title', 'R-100 · Did not match')
    expect(cells[16]).toHaveClass('hitmap__cell--deciding')
    expect(cells[16]).toHaveAttribute('title', 'R-330 · decided')
    expect(cells[17]).toHaveClass('hitmap__cell--notreached')
    // ringed in the colour of its outcome, never the accent (the spec, section 09)
    expect(map).toHaveClass('hitmap--refer')
    expect(rule(css, '.hitmap--refer')['--ring']).toBe('var(--refer-mark)')
    expect(
      [...document.querySelectorAll('.hitmap__legend > span')].map((entry) => entry.textContent),
    ).toStrictEqual([
      'matched',
      'did not match',
      'not reached',
      'disabled',
      'decided, ringed in its outcome',
    ])
  })

  it('offers the two explanations as buttons, and the export as links', () => {
    renderTrace()

    expect(screen.getByRole('button', { name: 'Explain for an officer' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Explain for the applicant' })).toBeInTheDocument()
    const exportTo = `${BASE}/decisions/${sampleDecision.id}/export`
    expect(screen.getByRole('link', { name: 'JSON' })).toHaveAttribute('href', exportTo)
    expect(screen.getByRole('link', { name: 'CSV' })).toHaveAttribute('href', exportTo)
  })

  it('exports the CSV with its Accept header and saves what the API sent', async () => {
    const user = userEvent.setup()
    let accepted: string | null = null
    server.use(
      http.get(`${BASE}/decisions/:id/export`, ({ request }) => {
        accepted = request.headers.get('accept')
        return new HttpResponse('rule,status\nR-010,fired\n', {
          headers: { 'Content-Type': 'text/csv' },
        })
      }),
    )
    const saved = vi.fn(() => 'blob:the-export')
    // jsdom has no object URLs; the page's own URL is kept and given the two it lacks
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = saved
        static override revokeObjectURL = vi.fn()
      },
    )
    const clicked = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined)
    renderTrace()

    await user.click(screen.getByRole('link', { name: 'CSV' }))

    await waitFor(() => expect(saved).toHaveBeenCalledOnce())
    expect(accepted).toBe('text/csv')
    expect(clicked).toHaveBeenCalledOnce()
  })

  it('closes the margin from its head', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    renderTrace(sampleDecision, onClose)

    await user.click(screen.getByRole('button', { name: 'Close' }))
    expect(onClose).toHaveBeenCalledOnce()
  })
})

describe('TraceView, what the engine decided', () => {
  it('pins the deciding rule on the well, with its Hebrew label and the reason for the applicant', () => {
    renderTrace()

    const deciding = document.querySelector<HTMLElement>('.trace__deciding')!
    expect(within(deciding).getByText('Decided by')).toHaveClass('trace__deciding-label')
    expect(within(deciding).getByText('R-330')).toHaveClass('chip', 'chip--active')
    expect(within(deciding).getByText('בדיקת חתם: אירוע אשראי אחד ללא ערב')).toHaveAttribute(
      'dir',
      'rtl',
    )
    expect(within(deciding).getByText('Reason for the applicant')).toBeInTheDocument()
    const reason = within(deciding).getByText(sampleDecision.reason!)
    expect(reason).toHaveClass('trace__reason')
    expect(reason).toHaveAttribute('lang', 'he')
    expect(rule(css, '.trace__deciding').background).toBe('var(--well)')
  })

  it('names what the engine derived and what it flagged, the flags even when there are none', () => {
    renderTrace()

    const derived = screen.getByText('Derived by the engine').nextElementSibling as HTMLElement
    expect(within(derived).getByText('monthly_installment').nextElementSibling).toHaveTextContent(
      '1,493.1 ₪',
    )
    expect(within(derived).getByText('debt_to_income').nextElementSibling).toHaveTextContent(
      '0.2835',
    )
    // case 17 was decided at R-330, before the advisory rules R-410 and R-420 were reached
    expect(screen.getByText('Flags').nextElementSibling).toHaveTextContent(
      'None: the decision came before the advisory rules.',
    )
  })

  it("leaves out the case's inputs, which the decision the API returns does not carry", () => {
    // the owner's answer of 2026-09-28 to phase 3's first question
    renderTrace()

    expect(screen.queryByText('Case inputs')).not.toBeInTheDocument()
  })
})

describe('TraceView, the steps', () => {
  it("walks the steps in the engine's words, each comparison as Field · Expected · In the case · Result", () => {
    renderTrace()

    const r020 = stepOf('R-020')
    expect(r020).toHaveClass('step', 'step--matched')
    expect(within(r020).getByText('Matched')).toHaveClass('step__status')
    const comparison = r020.querySelector<HTMLElement>('.cmp')!
    expect(
      [...comparison.querySelectorAll('.cmp__h')].map((head) => head.textContent),
    ).toStrictEqual(['Field', 'Expected', 'In the case', 'Result'])
    expect(within(comparison).getByText('monthly_income')).toHaveClass('cmp__field')
    expect(within(comparison).getByText('> 0')).toHaveClass('cmp__cond')
    expect(within(comparison).getByText('9,500')).toHaveClass('cmp__actual')
    const result = within(comparison).getByText('met')
    expect(result).toHaveClass('cmp__result', 'cmp__result--true')
    expect(result.querySelector('svg')?.dataset.icon).toBe('check')
    expect(
      within(stepOf('R-010')).getByText('set monthly_installment · null → 1,493.1'),
    ).toHaveClass('step__effect')
  })

  it('marks the deciding step as Matched · decided, with the dot of its outcome', () => {
    renderTrace()

    const r330 = stepOf('R-330')
    expect(within(r330).getByText('R-330')).toHaveClass('chip--active')
    const status = r330.querySelector<HTMLElement>('.step__status')!
    expect(status).toHaveTextContent('Matched · decided')
    expect(status.querySelector('.dot--refer')).not.toBeNull()
    expect(within(r330).getByText('decide · Manual review · terminal')).toBeInTheDocument()
  })

  it('collapses the rules that did not match to their head, until every comparison is shown', async () => {
    const user = userEvent.setup()
    renderTrace()

    // 3 matched and 14 that did not match; the 3 not reached after the decision are counted, not listed (the owner's
    // answer of 2026-09-28 to phase 3's sixth question)
    expect(within(screen.getByRole('list', { name: STEPS })).getAllByRole('listitem')).toHaveLength(
      17,
    )
    expect(within(stepOf('R-110')).getByText('Did not match')).toBeInTheDocument()
    expect(stepOf('R-110').querySelector('.cmp')).toBeNull()
    const show = screen.getByRole('button', { name: 'Show every comparison' })
    // the spec draws the count as the last, not-reached row of the steps
    expect(show.closest('.step')).toHaveClass('step--notreached')
    expect(show.closest('.step')).toHaveTextContent(
      '14 rules that did not match are collapsed to their head · 3 not reached after the decision · Show every comparison',
    )

    await user.click(show)

    expect(within(screen.getByRole('list', { name: STEPS })).getAllByRole('listitem')).toHaveLength(
      20,
    )
    expect(screen.queryByRole('button', { name: 'Show every comparison' })).not.toBeInTheDocument()
    const notMet = within(stepOf('R-110')).getAllByText('not met')[0]!
    expect(notMet).toHaveClass('cmp__result--false')
    expect(notMet.querySelector('svg')?.dataset.icon).toBe('cross')
    // the engine's own text of R-116's computed limit, beside its value (Document 3, R-116 in full)
    const limit = within(stepOf('R-116')).getByText('≥ 74')
    expect(limit.closest('.cmp__cond')).toHaveTextContent('≥ 74 (78 - (term_months / 12))')
    expect(within(stepOf('R-900')).getByText('Not reached')).toBeInTheDocument()
    expect(stepOf('R-900')).toHaveClass('step--notreached')
  })
})

describe('TraceView, an evaluation error', () => {
  const failed: Decision = {
    ...sampleDecision,
    status: 'ERROR',
    outcome: undefined,
    reason: undefined,
    decidingRuleId: null,
    errorCode: 'DIVISION_BY_ZERO',
    errorRuleId: 'R-020',
    derived: {},
    trace: [
      sampleDecision.trace[0]!,
      {
        ...sampleDecision.trace[1]!,
        status: 'error',
        actions: undefined,
        error: { code: 'DIVISION_BY_ZERO', detail: 'monthly_income is 0' },
      },
    ],
  }

  it('heads the trace with the ink tag, and says in the strip which rule failed and why', () => {
    renderTrace(failed)

    const head = document.querySelector<HTMLElement>('.trace__head')!
    expect(within(head).getByText('Evaluation error')).toHaveClass('tag--error')
    // the spec, section 09: "the strip says which rule failed and why (DIVISION_BY_ZERO · R-020)"
    expect(document.querySelector('.trace__deciding')).toHaveTextContent('DIVISION_BY_ZERO · R-020')
    expect(within(stepOf('R-020')).getByText('Evaluation error')).toHaveClass('step__status')
    expect(stepOf('R-020').querySelector('.step__effect')).toHaveTextContent(
      'DIVISION_BY_ZERO · monthly_income is 0',
    )
    // the steps stop at the rule that failed
    expect(within(screen.getByRole('list', { name: STEPS })).getAllByRole('listitem')).toHaveLength(
      2,
    )
  })
})

describe('TraceView.css', () => {
  it("carries every rule of the spec's trace, hit map, steps and comparisons, with the spec's declarations", () => {
    const trace = specRules('/* Trace */', '/* The assistant thread').filter(
      ([selector]) => !selector.startsWith('.explain'),
    )

    expect(trace).toHaveLength(41)
    expect(unported(css, trace)).toEqual([])
  })
})

describe('TraceView in both directions (NFR-5)', () => {
  it("RTL: case 17's Hebrew labels and reason read right to left beside Latin ids, fields and numbers (snapshot)", () => {
    const { container } = renderTrace()

    expect(rtlSnapshot(container)).toMatchSnapshot()
  })
})
