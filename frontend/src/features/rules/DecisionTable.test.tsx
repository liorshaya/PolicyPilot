import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Rule, RuleSetDocument } from '../../api/types'
import { lendingRuleSet } from '../../test/fixtures/lending'
import { DecisionTable } from './DecisionTable'

/**
 * The decision table itself (Document 3, Decision Table Rendering and Provenance; Document 6, Frontend Test Design:
 * the Hebrew labels read right to left and a refused pointer lands on the row it names).
 */

function rule(id: string): Rule {
  const found = lendingRuleSet.rules.find((candidate) => candidate.id === id)
  if (!found) {
    throw new Error(`the fixture has no rule ${id}`)
  }
  return found
}

function documentOf(...rules: Rule[]): RuleSetDocument {
  return { ...lendingRuleSet, rules }
}

function renderTable(props: Partial<Parameters<typeof DecisionTable>[0]> = {}) {
  return render(
    <DecisionTable
      document={lendingRuleSet}
      selectedRuleId={null}
      onSelect={() => undefined}
      {...props}
    />,
  )
}

describe('DecisionTable', () => {
  it('reads a Hebrew rule label right to left beside its Latin rule id', () => {
    renderTable()

    const label = screen.getByText('דחייה: גיל נמוך מ-21')
    expect(label.tagName).toBe('BDI')
    expect(label).toHaveAttribute('dir', 'auto')
    // the id stays in the monospace column, so the two never reorder each other
    expect(screen.getByText('R-100')).toHaveClass('mono')
  })

  it('marks the selected row and tells assistive technology which one it is', () => {
    renderTable({ selectedRuleId: 'R-330' })

    const row = screen.getByText('R-330').closest('tr')
    expect(row).toHaveClass('table__row--selected')
    // a row is not a grid cell, so the selection is stated with aria-current
    expect(row).toHaveAttribute('aria-current', 'true')
  })

  it('shows where each rule comes from: a paragraph, an analyst, or a pending change', () => {
    renderTable({
      document: documentOf(
        rule('R-100'),
        {
          ...rule('R-200'),
          provenance: { kind: 'analyst', actor: 'demo-analyst', note: 'tightened after the audit' },
        },
        {
          ...rule('R-900'),
          provenance: {
            kind: 'pending',
            changeRequestId: 'CR-7',
            rationale: 'proposed by the assistant',
          },
        },
      ),
    })

    expect(screen.getByText('¶1')).toBeInTheDocument()
    expect(screen.getByText('גילו 21 עד 70 בעת הגשת הבקשה')).toBeInTheDocument()
    expect(screen.getByText('Analyst')).toBeInTheDocument()
    expect(screen.getByText('tightened after the audit')).toBeInTheDocument()
    // a rule a person has not approved yet is never shown as a source of its own
    expect(screen.getByText('Pending approval')).toBeInTheDocument()
  })

  it('leaves the cells of a published version as text', () => {
    renderTable()

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.getByText('< 21 years')).toBeInTheDocument()
  })

  it('sends one edited comparison and leaves the others alone', async () => {
    const user = userEvent.setup()
    const onEditCell = vi.fn()
    renderTable({ document: documentOf(rule('R-110')), onEditCell })

    const cell = screen.getByLabelText('R-110, age')
    await user.clear(cell)
    await user.type(cell, '> 75 years{Enter}')

    expect(onEditCell).toHaveBeenCalledExactlyOnceWith(
      'R-110',
      { field: 'age', op: 'gt', value: 70 },
      { field: 'age', op: 'gt', value: 75 },
    )
  })

  it('restores the cell and clears its message when the edit is abandoned', async () => {
    const user = userEvent.setup()
    const onEditCell = vi.fn()
    renderTable({ document: documentOf(rule('R-100')), onEditCell })

    const cell = screen.getByLabelText('R-100, age')
    await user.clear(cell)
    await user.type(cell, 'nonsense{Enter}')
    expect(screen.getByText(/Write =/)).toBeInTheDocument()

    await user.type(cell, '{Escape}')

    expect(cell).toHaveValue('< 21 years')
    expect(screen.queryByText(/Write =/)).not.toBeInTheDocument()
    expect(onEditCell).not.toHaveBeenCalled()
  })

  it('commits an edit when the cell loses focus', async () => {
    const user = userEvent.setup()
    const onEditCell = vi.fn()
    renderTable({ document: documentOf(rule('R-100')), onEditCell })

    const cell = screen.getByLabelText('R-100, age')
    await user.clear(cell)
    await user.type(cell, '< 23 years')
    await user.tab()

    expect(onEditCell).toHaveBeenCalledExactlyOnceWith(
      'R-100',
      { field: 'age', op: 'lt', value: 21 },
      { field: 'age', op: 'lt', value: 23 },
    )
  })

  it('marks the row a 422 pointer names', () => {
    renderTable({
      document: documentOf(rule('R-100'), rule('R-200')),
      problems: [{ path: '/rules/1/condition/value', problem: 'below the minimum of the field' }],
    })

    expect(screen.getByText('R-200').closest('tr')).toHaveClass('table__row--refused')
    expect(screen.getByText('R-100').closest('tr')).not.toHaveClass('table__row--refused')
  })

  it('leaves a column empty where a rule says nothing about the field', () => {
    renderTable({ document: documentOf(rule('R-100'), rule('R-200')) })

    const row = screen.getByText('R-100').closest('tr')!
    // R-100 compares the age and says nothing about the debt ratio, so that column stays blank
    expect(within(row).getByRole('cell', { name: 'no comparison' })).toBeInTheDocument()
  })

  it('shows a disabled rule as disabled', () => {
    renderTable({ document: documentOf({ ...rule('R-100'), enabled: false }) })

    expect(screen.getByText('R-100').closest('tr')).toHaveClass('table__row--disabled')
  })
})
