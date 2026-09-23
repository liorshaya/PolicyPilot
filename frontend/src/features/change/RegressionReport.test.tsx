import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { scriptedProposalEvent } from '../../test/fixtures/change'
import { RegressionReport } from './RegressionReport'
import type { Regression } from './types'

// @requirement FR-18

/**
 * The regression report (Document 3, Regression report: every decision the sandbox made on the base version, decided
 * again by the copy; each flip with its case number, both outcomes and both deciding rules, and a count per
 * transition). The scripted report is built from the committed fixtures: the 12 flips of the `regression` block of
 * cases-expected.json, and the rule that decided each case under version 1 from the same file.
 */

/** The flipped applications of the scripted request, as fixtures/.../cases-expected.json lists them. */
const FLIPPED = ['8', '11', '33', '72', '93', '99', '100', '139', '152', '161', '174', '185']

function bodyRows(): HTMLElement[] {
  const [, body] = screen.getAllByRole('rowgroup')
  return within(body!).getAllByRole('row')
}

function cellsOf(row: HTMLElement): string[] {
  return within(row)
    .getAllByRole('cell')
    .map((cell) => cell.textContent ?? '')
}

describe('RegressionReport', () => {
  it('lists the 12 flips of the scripted request by application number, with both outcomes and both rules', () => {
    render(<RegressionReport regression={scriptedProposalEvent.regression} baseVersionNo={1} />)

    const rows = bodyRows()
    expect(rows.map((row) => cellsOf(row)[0])).toStrictEqual(FLIPPED)
    // case 8 was approved by R-900 and case 11 referred by R-330 under version 1; R-170 declines both
    expect(cellsOf(rows[0]!)).toStrictEqual(['8', 'Approved', 'Declined', 'R-900 → R-170'])
    expect(cellsOf(rows[1]!)).toStrictEqual(['11', 'Manual review', 'Declined', 'R-330 → R-170'])
  })

  it('says how many of the decisions flip, and counts each transition', () => {
    render(<RegressionReport regression={scriptedProposalEvent.regression} baseVersionNo={1} />)

    expect(screen.getByText(/12 of the 200 decisions/)).toBeVisible()
    const transitions = within(screen.getByRole('list', { name: 'Transitions' })).getAllByRole(
      'listitem',
    )
    // six approvals and six referrals turn into declines (cases-expected.json, regression)
    expect(transitions).toHaveLength(2)
    expect(transitions[0]).toHaveTextContent(/^Approved → Declined\s*6$/)
    expect(transitions[1]).toHaveTextContent(/^Manual review → Declined\s*6$/)
  })

  it('shows a case decided on its own without a number, and an input the copy cannot decide as an error', () => {
    const report: Regression = {
      decisions: 3,
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
    }
    render(<RegressionReport regression={report} baseVersionNo={2} />)

    expect(cellsOf(bodyRows()[0]!)).toStrictEqual([
      'Own case',
      'Approved',
      'Evaluation error',
      'R-900 → none',
    ])
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
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })
})
