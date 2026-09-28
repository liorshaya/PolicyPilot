import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { specRules, stylesheet, unported } from '../../test/css'
import { scriptedProposalEvent } from '../../test/fixtures/change'
import { RegressionReport } from './RegressionReport'
import type { Regression } from './types'

// @requirement FR-18

/**
 * The regression report (Document 3, Regression report: every decision the sandbox made on the base version, decided
 * again by the copy, their outcomes before, each flip with its case number, both outcomes and both deciding rules, a
 * count per transition and the flags that moved), drawn as the spec draws it (section 09, "The change request": "12
 * flipped · 6.0% of 200" first, the matrix with totals and the unchanged diagonal, flips by cause as a one-hue bar
 * that filters the list, flags moved as their own figure, each flipped case with both traces one click away). The
 * scripted report is built from the committed fixtures: the 12 flips of the `regression` block of cases-expected.json,
 * the outcome of every case under version 1 from the same file, and the flags the Python reference moves.
 */

/** The flipped applications of the scripted request, as fixtures/.../cases-expected.json lists them. */
const FLIPPED = ['8', '11', '33', '72', '93', '99', '100', '139', '152', '161', '174', '185']

const report = scriptedProposalEvent.regression

function renderReport(regression: Regression = report, onBothTraces = vi.fn()) {
  render(<RegressionReport regression={regression} baseVersionNo={1} onBothTraces={onBothTraces} />)
  return onBothTraces
}

function figure(label: string): HTMLElement {
  return screen.getByText(label, { selector: '.figure__label' }).closest<HTMLElement>('.figure')!
}

function flipRows(): HTMLElement[] {
  const table = screen.getByRole('table', { name: 'The decisions that flip' })
  const [, body] = within(table).getAllByRole('rowgroup')
  return within(body!).getAllByRole('row')
}

function cellsOf(row: HTMLElement): string[] {
  return within(row)
    .getAllByRole('cell')
    .map((cell) => cell.textContent ?? '')
}

describe('RegressionReport', () => {
  it('leads with the count that matters: 12 flipped, 6.0% of 200, then the unchanged and the flags moved', () => {
    renderReport()

    expect(figure('Flipped').querySelector('.figure__value')).toHaveTextContent('126.0% of 200')
    expect(figure('Flipped').querySelector('.dot--refer')).not.toBeNull()
    expect(figure('Unchanged').querySelector('.figure__value')).toHaveTextContent('18894.0%')
    // the Python reference moves the flags of eleven decisions, every one by R-410
    expect(figure('Flags moved').querySelector('.figure__value')).toHaveTextContent('11R-410')
  })

  it('draws the matrix: version 1 by the proposal, the totals, the unchanged diagonal quiet and the flips hot', () => {
    renderReport()

    const matrix = screen.getByRole('table', { name: 'Version 1 by the proposal' })
    const rows = within(matrix).getAllByRole('row')
    const cells = (row: HTMLElement) =>
      [...row.querySelectorAll('th, td')].map((cell) => cell.textContent)
    // cases-expected.json: 113 approved, 60 declined and 27 referred under version 1; six of each flip to a decline
    expect(rows.map(cells)).toStrictEqual([
      ['v1 → proposed', 'Approved', 'Declined', 'Manual review', 'Total'],
      ['Approved', '107', '6', '0', '113'],
      ['Declined', '0', '60', '0', '60'],
      ['Manual review', '0', '6', '21', '27'],
      ['Total', '107', '72', '21', '200'],
    ])
    const approved = rows[1]!.querySelectorAll('td')
    expect(approved[0]).toHaveClass('same')
    expect(approved[1]).toHaveClass('hot')
    expect(approved[2]).not.toHaveClass('hot')
    expect(approved[3]).toHaveClass('total')
  })

  it('lists the first flips by case, with both outcomes and both rules, and shows all twelve on request', async () => {
    renderReport()
    const user = userEvent.setup()

    expect(flipRows()).toHaveLength(3)
    // case 8 was approved by R-900 and case 11 referred by R-330 under version 1; R-170 declines both
    expect(cellsOf(flipRows()[0]!)).toStrictEqual([
      '8',
      'Approved',
      'Declined',
      'R-900 → R-170',
      'Both traces',
    ])
    expect(cellsOf(flipRows()[1]!)).toStrictEqual([
      '11',
      'Manual review',
      'Declined',
      'R-330 → R-170',
      'Both traces',
    ])

    await user.click(screen.getByRole('button', { name: 'Show all 12' }))

    expect(flipRows().map((row) => cellsOf(row)[0])).toStrictEqual(FLIPPED)
  })

  it('counts the flips by cause as a bar list that filters the list', async () => {
    const twoCauses: Regression = {
      ...report,
      flips: [
        ...report.flips.slice(0, 2),
        { ...report.flips[2]!, after: 'refer', decidingRuleAfter: 'R-330' },
      ],
      transitions: { 'approve → reject': 1, 'refer → reject': 1, 'approve → refer': 1 },
    }
    renderReport(twoCauses)
    const user = userEvent.setup()

    const causes = within(screen.getByRole('group', { name: 'Flips by cause' })).getAllByRole(
      'button',
    )
    expect(causes.map((cause) => cause.textContent)).toStrictEqual(['R-1702 flips', 'R-3301 flip'])

    await user.click(causes[1]!)

    expect(causes[1]).toHaveAttribute('aria-pressed', 'true')
    expect(flipRows().map((row) => cellsOf(row)[0])).toStrictEqual(['33'])
  })

  it('opens both traces of a flipped case', async () => {
    const onBothTraces = renderReport()
    const user = userEvent.setup()

    await user.click(within(flipRows()[0]!).getByRole('button', { name: 'Both traces' }))

    expect(onBothTraces).toHaveBeenCalledWith(report.flips[0])
  })

  it('shows a case decided on its own without a number, and an input the copy cannot decide as an error', () => {
    renderReport({
      decisions: 3,
      before: { approve: 3 },
      flips: [
        {
          decisionId: '0f4c1c9e-0000-4000-8000-0000000000e9',
          caseNo: null,
          before: 'approve',
          after: 'error',
          decidingRuleBefore: 'R-900',
          decidingRuleAfter: null,
        },
      ],
      transitions: { 'approve → error': 1 },
      flagsMoved: { decisions: 0, byRule: {} },
    })

    expect(cellsOf(flipRows()[0]!).slice(0, 4)).toStrictEqual([
      'Own case',
      'Approved',
      'Evaluation error',
      'R-900 → none',
    ])
    expect(figure('Flags moved').querySelector('.figure__value')).toHaveTextContent('0')
  })

  // The spec, section 10, at phone width: a table may be wider than the window only in a box of its own that scrolls
  it('keeps each table in a box of its own that scrolls sideways, so a narrow window never widens the report', () => {
    renderReport()

    const matrix = screen.getByRole('table', { name: 'Version 1 by the proposal' })
    expect(matrix.parentElement).toHaveClass('table-scroll')
    const flips = screen.getByRole('table', { name: 'The decisions that flip' })
    expect(flips.parentElement).toHaveClass('table-scroll')
  })

  it('draws a report stored before its outcomes were counted without the matrix and the flags moved', () => {
    renderReport({
      decisions: report.decisions,
      flips: report.flips,
      transitions: report.transitions,
    })

    expect(figure('Flipped').querySelector('.figure__value')).toHaveTextContent('126.0% of 200')
    expect(
      screen.queryByRole('table', { name: 'Version 1 by the proposal' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByText('Flags moved')).not.toBeInTheDocument()
  })

  it('says so when the sandbox decided nothing on the base version, and when nothing flips', () => {
    const { unmount } = render(
      <RegressionReport
        regression={{ decisions: 0, flips: [], transitions: {} }}
        baseVersionNo={1}
      />,
    )
    expect(screen.getByText(/has not decided a case on version 1/)).toBeVisible()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    unmount()

    render(
      <RegressionReport
        regression={{ decisions: 200, flips: [], transitions: {} }}
        baseVersionNo={1}
      />,
    )
    expect(screen.getByText('None of the 200 decisions flips.')).toBeVisible()
    expect(screen.queryByRole('table', { name: 'The decisions that flip' })).not.toBeInTheDocument()
  })
})

describe('RegressionReport.css', () => {
  it("carries every rule of the spec's matrix, with the spec's declarations", () => {
    const matrix = specRules('.matrix {', '/* Audit log */')

    expect(matrix).toHaveLength(8)
    expect(unported(stylesheet('features/change/RegressionReport.css'), matrix)).toEqual([])
  })
})
