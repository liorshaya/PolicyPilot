import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { rule, stylesheet, token } from '../../test/css'
import { decisionIdOf, lendingRun } from '../../test/fixtures/lending'
import { DecisionList } from './DecisionList'

// @requirement FR-9

/**
 * The case list, the base table of the Register (the spec, section 07): a paper header row, rows at the row height or
 * the compact one, numbers end-aligned, ids in mono, decisions as quiet tags, flags as field chips, a dash for an empty
 * cell, and the count, the scope and the keys in the footer. The run is the 200 seeded cases as version 1 decides them
 * (fixtures/policies/consumer-lending/cases-expected.json).
 */

const css = stylesheet('shared/ui/Table.css')

function renderList(props: Partial<Parameters<typeof DecisionList>[0]> = {}) {
  return render(
    <DecisionList
      results={lendingRun}
      selectedId={null}
      onSelect={() => undefined}
      versionNo={1}
      {...props}
    />,
  )
}

/** The row of a case, found by the button its number is. */
function rowOf(caseNo: number): HTMLElement {
  return screen.getByRole('button', { name: String(caseNo) }).closest('tr')!
}

/** The cases of the run the fixture decides as it is asked. */
const decidedBy = (ruleId: string) =>
  lendingRun.filter((result) => result.decidingRuleId === ruleId).length

afterEach(() => {
  localStorage.clear()
})

describe('DecisionList, the table', () => {
  it('keeps its header in view on paper, at 12px and 500', () => {
    renderList()

    expect(screen.getAllByRole('columnheader').map((header) => header.textContent)).toStrictEqual([
      'Case',
      'Decision',
      'Decided by',
      'Flags',
    ])
    expect(rule(css, '.table th')).toMatchObject({
      position: 'sticky',
      top: '0',
      background: 'var(--paper)',
      'font-size': 'var(--text-xs)',
      'font-weight': '500',
    })
    expect(token('text-xs', 'light')).toBe('0.75rem')
  })

  it('draws a row at the row height, and a Compact row at the compact height', () => {
    expect(rule(css, '.table td').height).toBe('var(--row-h)')
    expect(rule(css, '.table--compact td').height).toBe('var(--row-h-compact)')
    expect(token('row-h', 'light')).toBe('36px')
    expect(token('row-h-compact', 'light')).toBe('30px')
  })

  it('end-aligns the case number with its header', () => {
    renderList()

    expect(screen.getByRole('columnheader', { name: 'Case' })).toHaveClass('t-num')
    expect(screen.getByRole('button', { name: '17' }).closest('td')).toHaveClass('t-num')
    expect(rule(css, '.t-num')).toMatchObject({
      'text-align': 'end',
      'font-variant-numeric': 'tabular-nums lining-nums',
    })
  })

  it('writes the deciding rule as a mono id and the decision as the quiet tag', () => {
    renderList()

    expect(within(rowOf(17)).getByText('R-330')).toHaveClass('t-id')
    expect(within(rowOf(17)).getByText('Manual review')).toHaveClass(
      'tag',
      'tag--refer',
      'tag--quiet',
      'tag--dot',
    )
    expect(rule(css, '.t-id')['font-family']).toBe('var(--font-mono)')
  })

  it('shows the flags as field chips, and an empty cell as the dash', () => {
    renderList()

    // case 2 is approved with the manual-check flag, case 17 carries none
    expect(within(rowOf(2)).getByText('STABLE_INCOME_MANUAL_CHECK')).toHaveClass(
      'chip',
      'chip--field',
    )
    expect(within(rowOf(17)).getByRole('cell', { name: 'no flags' })).toHaveClass('t-empty')
    expect(rule(css, '.t-empty').color).toBe('var(--mark-quiet)')
  })

  it('marks the selected row with the accent bar and says which one it is', () => {
    renderList({ selectedId: decisionIdOf(17) })

    expect(rowOf(17)).toHaveClass('t-selected')
    expect(rowOf(17)).toHaveAttribute('aria-current', 'true')
    expect(rowOf(18)).not.toHaveClass('t-selected')
  })

  it('opens a case from the button its number is, the one the end-to-end tests press', async () => {
    const onSelect = vi.fn()
    renderList({ onSelect })

    await userEvent.click(screen.getByRole('button', { name: '17' }))

    expect(onSelect).toHaveBeenCalledExactlyOnceWith(decisionIdOf(17))
  })
})

describe('DecisionList, the footer', () => {
  it('says the count, the scope and the three keys', () => {
    const { container } = renderList()

    const foot = container.querySelector('.sheet__foot')!
    expect(
      within(foot as HTMLElement).getByText('Cases 1–200 of 200 · one run on v1'),
    ).toBeInTheDocument()
    expect(foot).toHaveTextContent('↑ ↓ select · ↵ open · / filter')
    expect([...foot.querySelectorAll('.kbd')].map((key) => key.textContent)).toStrictEqual([
      '↑',
      '↓',
      '↵',
      '/',
    ])
  })
})

describe('DecisionList, the filters and the density', () => {
  it('filters by case number, outcome and deciding rule, and counts what it shows', async () => {
    const user = userEvent.setup()
    const { container } = renderList()
    const foot = () => container.querySelector('.sheet__foot')!

    await user.type(screen.getByRole('textbox', { name: 'Jump to a case' }), '17')
    // 17 and 170 to 179
    expect(screen.getAllByRole('row')).toHaveLength(1 + 11)
    expect(foot()).toHaveTextContent('Cases 1–11 of 200 · one run on v1')

    await user.clear(screen.getByRole('textbox', { name: 'Jump to a case' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Outcome' }), 'Declined')
    // 60 of the 200 are declined (cases-expected.json)
    expect(foot()).toHaveTextContent('Cases 1–60 of 200 · one run on v1')

    await user.selectOptions(screen.getByRole('combobox', { name: 'Outcome' }), 'All outcomes')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Deciding rule' }), 'R-330')
    expect(foot()).toHaveTextContent(`Cases 1–${decidedBy('R-330')} of 200 · one run on v1`)
    expect(
      within(screen.getByRole('combobox', { name: 'Deciding rule' }))
        .getAllByRole('option')
        .at(0),
    ).toHaveTextContent('Any deciding rule')
  })

  it('follows the deciding rule the figures above it choose, and reports a choice of its own', async () => {
    // the spec, section 09: the rows of "Rules that decided most often" filter the case list
    const user = userEvent.setup()
    const onDecidingRuleChange = vi.fn()
    renderList({ decidingRule: 'R-330', onDecidingRuleChange })

    const select = screen.getByRole('combobox', { name: 'Deciding rule' })
    expect(select).toHaveValue('R-330')
    expect(document.querySelector('.sheet__foot')).toHaveTextContent(
      `Cases 1–${decidedBy('R-330')} of 200 · one run on v1`,
    )
    await user.selectOptions(select, 'R-900')
    expect(onDecidingRuleChange).toHaveBeenLastCalledWith('R-900')
    await user.selectOptions(select, '')
    expect(onDecidingRuleChange).toHaveBeenLastCalledWith(null)
  })

  it('offers the outcomes in the words of the tags', () => {
    renderList()

    expect(
      within(screen.getByRole('combobox', { name: 'Outcome' }))
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toStrictEqual(['All outcomes', 'Approved', 'Declined', 'Manual review', 'Evaluation error'])
  })

  it('remembers the row height the reader chose', async () => {
    const user = userEvent.setup()
    const { unmount } = renderList()

    expect(screen.getByRole('table')).not.toHaveClass('table--compact')
    expect(screen.getByRole('button', { name: 'Comfortable' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    await user.click(screen.getByRole('button', { name: 'Compact' }))

    expect(screen.getByRole('table')).toHaveClass('table--compact')
    expect(screen.getByRole('button', { name: 'Compact' })).toHaveAttribute('aria-pressed', 'true')
    unmount()
    renderList()
    expect(screen.getByRole('table')).toHaveClass('table--compact')
  })
})

describe('DecisionList, the keys', () => {
  it('goes to the filter with /, and opens the first case it finds with Enter', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    renderList({ onSelect })

    await user.keyboard('/')
    expect(screen.getByRole('textbox', { name: 'Jump to a case' })).toHaveFocus()
    await user.keyboard('17{Enter}')

    expect(onSelect).toHaveBeenCalledExactlyOnceWith(decisionIdOf(17))
  })

  it('moves between the cases with the arrows and opens one with Enter', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    renderList({ onSelect })

    screen.getByRole('button', { name: '1' }).focus()
    await user.keyboard('{ArrowDown}{ArrowDown}')
    expect(screen.getByRole('button', { name: '3' })).toHaveFocus()
    await user.keyboard('{ArrowUp}{Enter}')

    expect(onSelect).toHaveBeenCalledExactlyOnceWith(decisionIdOf(2))
  })
})
