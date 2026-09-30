import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Aggregates } from '../../api/types'
import { rule, specRules, stylesheet, unported } from '../../test/css'
import { lendingRuleSet } from '../../test/fixtures/lending'
import { aggregates } from '../../test/msw/handlers'
import { Dashboard } from './Dashboard'

// @requirement FR-9

/**
 * The outcome row over the case list (the spec, section 09, "Figures: the outcome row"; Document 2, statistics). The
 * aggregates are those of the 200 seeded cases, fixtures/policies/consumer-lending/cases-expected.json: 113 approved,
 * 27 manual review, 60 declined, and R-900, R-320, R-330, R-200 and R-220 deciding most often; each rule's outcome is
 * its action in the committed rule set.
 */

const css = stylesheet('features/cases/Dashboard.css')
const figuresCss = stylesheet('shared/ui/Figures.css')

function renderFigures(
  props: Partial<Parameters<typeof Dashboard>[0]> = {},
  seen: Aggregates = aggregates,
) {
  return render(
    <Dashboard
      aggregates={seen}
      rules={lendingRuleSet.rules}
      filter={null}
      onFilter={() => undefined}
      {...props}
    />,
  )
}

const rows = () =>
  [...document.querySelectorAll<HTMLElement>('.barlist__row')].map((row) => row.textContent)

describe('Dashboard, the outcome row', () => {
  it('draws one proportional bar, and names it for a screen reader', () => {
    renderFigures()

    const bar = screen.getByRole('img', {
      name: '113 approved, 27 manual review, 60 declined, 0 evaluation errors',
    })
    expect(
      [...bar.querySelectorAll<HTMLElement>('.outcome__seg')].map((segment) => [
        segment.className,
        segment.style.inlineSize,
      ]),
    ).toStrictEqual([
      ['outcome__seg outcome__seg--approve', '56.5%'],
      ['outcome__seg outcome__seg--refer', '13.5%'],
      // the width as CSS reads it: 30.0% is 30%
      ['outcome__seg outcome__seg--decline', '30%'],
    ])
    // the spec: "The bar is 12px with 2px gaps"
    expect(rule(css, '.outcome__bar').height).toBe('12px')
    expect(rule(css, '.outcome__bar').gap).toBe('2px')
  })

  it('writes three figures with their share, each beside the dot of its outcome', () => {
    renderFigures()

    expect(
      [...document.querySelectorAll('.figure')].map((figure) => figure.textContent),
    ).toStrictEqual(['Approved11356.5%', 'Manual review2713.5%', 'Declined6030.0%'])
    for (const [label, dot] of [
      ['Approved', 'dot--approve'],
      ['Manual review', 'dot--refer'],
      ['Declined', 'dot--decline'],
    ] as const) {
      expect(screen.getByText(label).querySelector('.dot')).toHaveClass(dot)
    }
    // the owner's answer of 2026-09-28 to phase 3's seventh question: the figure's letter-spacing as the spec writes it
    expect(rule(figuresCss, '.figure__value')['letter-spacing']).toBe('-0.01em')
  })

  it('writes the evaluation errors as a footnote, not a quarter of the row', () => {
    const { rerender } = renderFigures()

    expect(document.querySelector('.figure__foot')).toHaveTextContent('0 evaluation errors')
    expect(document.querySelectorAll('.figure')).toHaveLength(3)

    rerender(
      <Dashboard
        aggregates={{ ...aggregates, errors: 1, decisions: 201 }}
        rules={lendingRuleSet.rules}
        filter={null}
        onFilter={() => undefined}
      />,
    )
    expect(document.querySelector('.figure__foot')).toHaveTextContent('1 evaluation error')
    expect(
      screen.getByRole('img', {
        name: '113 approved, 27 manual review, 60 declined, 1 evaluation error',
      }),
    ).toContainElement(document.querySelector('.outcome__seg--error'))
  })
})

describe('Dashboard, the rules that decided most often', () => {
  it('lists them with the dot of their outcome, their count and their share of every decision', () => {
    renderFigures()

    expect(
      screen.getByText('Rules that decided most often · click to filter the list'),
    ).toBeInTheDocument()
    expect(rows()).toStrictEqual([
      'R-900113 · 56.5%',
      'R-32011 · 5.5%',
      'R-33011 · 5.5%',
      'R-2008 · 4.0%',
      'R-2208 · 4.0%',
    ])
    const dotOf = (ruleId: string) =>
      screen.getByRole('button', { name: new RegExp(`^${ruleId}`) }).querySelector('.dot')
    expect(dotOf('R-900')).toHaveClass('dot--approve')
    expect(dotOf('R-330')).toHaveClass('dot--refer')
    expect(dotOf('R-200')).toHaveClass('dot--decline')
    // one hue: each bar is a proportion of the rule that decided most often
    expect(
      [...document.querySelectorAll<HTMLElement>('.barlist__fill')].map(
        (fill) => fill.style.inlineSize,
      ),
    ).toStrictEqual(['100%', '9.7%', '9.7%', '7.1%', '7.1%'])
  })

  it('filters the case list by the rule a row names, and a second press clears it', async () => {
    const user = userEvent.setup()
    const onFilter = vi.fn()
    const { rerender } = renderFigures({ onFilter })

    await user.click(screen.getByRole('button', { name: /^R-330/ }))
    expect(onFilter).toHaveBeenLastCalledWith('R-330')

    rerender(
      <Dashboard
        aggregates={aggregates}
        rules={lendingRuleSet.rules}
        filter="R-330"
        onFilter={onFilter}
      />,
    )
    expect(screen.getByRole('button', { name: /^R-330/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /^R-900/ })).toHaveAttribute('aria-pressed', 'false')
    await user.click(screen.getByRole('button', { name: /^R-330/ }))
    expect(onFilter).toHaveBeenLastCalledWith(null)
  })

  it('answers the officer\'s question, which rules decline most, with "Declines only"', async () => {
    // Document 9, known tensions: "Declines only" is what "top rejection reasons" means in this product
    const user = userEvent.setup()
    renderFigures()

    const declines = screen.getByRole('button', { name: 'Declines only' })
    expect(declines).toHaveAttribute('aria-pressed', 'false')
    await user.click(declines)

    expect(declines).toHaveAttribute('aria-pressed', 'true')
    expect(rows()).toStrictEqual(['R-2008 · 4.0%', 'R-2208 · 4.0%'])
    expect(within(document.querySelector('.barlist')!).queryByText('R-900')).not.toBeInTheDocument()
  })

  it('shows the state of "Declines only", a pressed control in the list\'s title', () => {
    // the spec, section 09 (v3.7): a pressed control, so its state shows
    renderFigures()

    const declines = screen.getByRole('button', { name: 'Declines only' })
    expect(declines).toHaveClass('btn', 'btn--secondary', 'btn--sm')
    expect(declines.closest('.barlist__title')).not.toBeNull()
    expect(rule(figuresCss, ".barlist__title .btn[aria-pressed='true']")).toStrictEqual({
      background: 'var(--well-2)',
      'border-color': 'var(--border-strong)',
    })
  })

  it("names each rule of the list by its label on hover, in the policy's language", () => {
    renderFigures()

    // the label of R-330 in the committed rule set (fixtures/policies/consumer-lending/ruleset.v1.json)
    expect(screen.getByRole('button', { name: /^R-330/ })).toHaveAttribute(
      'title',
      'בדיקת חתם: אירוע אשראי אחד ללא ערב',
    )
  })
})

describe('Dashboard, the summary band', () => {
  it('stands the outcome row and the rules side by side as one band, stacked when the sheet is narrow', () => {
    const { container } = renderFigures()

    const band = container.querySelector<HTMLElement>('.summary')!
    expect(band.firstElementChild).toHaveClass('outcome')
    expect(band.lastElementChild).toHaveClass('summary__rules')
    expect(
      within(band).getByText('Rules that decided most often · click to filter the list'),
    ).toHaveClass('barlist__title')
    // the spec, section 09 (v3.7): a row that wraps, the outcome first from 420px, the rules from 240px
    expect(rule(css, '.summary')).toMatchObject({ display: 'flex', 'flex-wrap': 'wrap' })
    expect(rule(css, '.summary > .outcome').flex).toBe('1 1 420px')
    expect(rule(css, '.summary__rules').flex).toBe('1 1 240px')
  })

  it('names each segment of the bar on hover', () => {
    renderFigures()

    expect(
      [...document.querySelectorAll<HTMLElement>('.outcome__seg')].map((segment) => segment.title),
    ).toStrictEqual(['113 approved · 56.5%', '27 manual review · 13.5%', '60 declined · 30.0%'])
  })
})

describe('Dashboard.css', () => {
  it("carries every rule of the spec's outcome bar and summary band, with the spec's declarations", () => {
    const outcome = specRules('/* Figures */', '/* Policy document and its list */').filter(
      ([selector]) => selector.startsWith('.outcome') || selector.startsWith('.summary'),
    )

    expect(outcome).toHaveLength(9)
    expect(unported(css, outcome)).toEqual([])
  })
})
