import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Diff } from '../../api/types'
import { scriptedProposalEvent } from '../../test/fixtures/change'
import { lendingRuleSet } from '../../test/fixtures/lending'
import { rtlSnapshot } from '../../test/rtlSnapshot'
import { DiffView } from './DiffView'

// @requirement FR-18
// @requirement FR-20

/**
 * The side-by-side diff (Document 2, Frontend Architecture: "diff view (side by side, rule level)"; Document 3,
 * Structural diff: "the side-by-side view highlights condition.value: 8000 → 9000 rather than the whole rule"; Work
 * Plan day 14: added, removed and modified rows, and an RTL snapshot). The scripted diff is built from the committed
 * fixtures; the rest are rules of the lending rule set.
 */

const r160 = lendingRuleSet.rules.find((rule) => rule.id === 'R-160')!

const empty: Diff = {
  fields: { added: [], removed: [], modified: [] },
  rules: { added: [], removed: [], modified: [] },
  defaults: null,
}

function renderDiff(diff: Diff) {
  return render(
    <DiffView
      diff={diff}
      language="he"
      fields={lendingRuleSet.fields}
      beforeLabel="Version 1"
      afterLabel="Proposed"
    />,
  )
}

function rowOf(ruleId: string): HTMLElement {
  const header = screen.getByRole('rowheader', { name: new RegExp(`^${ruleId}\\b`) })
  const row = header.closest('tr')
  if (row === null) {
    throw new Error(`no row for ${ruleId}`)
  }
  return row
}

describe('DiffView', () => {
  it('shows a modified rule side by side, its changed attributes struck out and inserted', () => {
    renderDiff(scriptedProposalEvent.diff)

    const [before, after] = within(rowOf('R-170')).getAllByRole('cell')
    const deleted = within(before!).getAllByRole('deletion')
    const inserted = within(after!).getAllByRole('insertion')
    // Document 3: the label, the condition, the actions and the provenance of R-170 change; the priority does not
    expect(deleted).toHaveLength(4)
    expect(inserted).toHaveLength(4)
    expect(deleted.map((element) => element.textContent)).toContain('monthly_income < 8,000 ILS')
    expect(inserted.map((element) => element.textContent)).toContain('monthly_income < 9,000 ILS')
    expect(within(after!).getByText('170')).not.toHaveRole('insertion')
  })

  it('lists each change by its pointer, a value from before to after', () => {
    renderDiff(scriptedProposalEvent.diff)

    const changes = within(screen.getByRole('list', { name: 'What changed in R-170' }))
    expect(changes.getByText('/condition/value').closest('li')).toHaveTextContent('8,000 → 9,000')
    const band = within(screen.getByRole('list', { name: 'What changed in R-410' }))
    expect(band.getByText('/condition/value/0').closest('li')).toHaveTextContent('8,000 → 9,000')
    expect(band.getByText('/condition/value/1').closest('li')).toHaveTextContent('9,000 → 10,000')
  })

  it('shows an added rule only after, and a removed rule only before', () => {
    const added = {
      ...r160,
      id: 'R-131',
      label: 'דחייה: הלוואה מעל 80,000 ללא ערב',
      priority: 131,
      enabled: false,
    }
    renderDiff({ ...empty, rules: { added: [added], removed: [r160], modified: [] } })

    const [addedBefore, addedAfter] = within(rowOf('R-131')).getAllByRole('cell')
    expect(addedBefore).toHaveTextContent('Not in Version 1')
    expect(within(addedAfter!).getByRole('insertion')).toHaveTextContent(added.label)
    // a rule that arrives disabled says so: it decides nothing until someone enables it
    expect(within(addedAfter!).getByText('Enabled').nextSibling).toHaveTextContent('No')

    const [removedBefore, removedAfter] = within(rowOf('R-160')).getAllByRole('cell')
    expect(within(removedBefore!).getByRole('deletion')).toHaveTextContent(r160.label)
    expect(removedAfter).toHaveTextContent('Not in Proposed')
    expect(screen.getByText('1 rule added · 1 rule removed')).toBeVisible()
  })

  it('shows a field by its attributes and the defaults whole, each marked where it changed', () => {
    const income = lendingRuleSet.fields.find((field) => field.name === 'monthly_income')!
    const guarantor = lendingRuleSet.fields.find((field) => field.name === 'has_guarantor')!
    renderDiff({
      fields: {
        added: [],
        removed: [],
        modified: [
          {
            name: 'has_guarantor',
            from: guarantor,
            to: { ...guarantor, required: true },
            changes: [{ path: '/required', from: false, to: true }],
          },
          {
            name: 'monthly_income',
            from: income,
            to: { ...income, maximum: 200000 },
            changes: [{ path: '/maximum', from: null, to: 200000 }],
          },
        ],
      },
      rules: { added: [], removed: [], modified: [] },
      defaults: {
        from: lendingRuleSet.defaults,
        to: { outcome: 'approve', reason: 'כל בקשה מאושרת' },
      },
    })

    const [, incomeAfter] = within(rowOf('monthly_income')).getAllByRole('cell')
    expect(within(incomeAfter!).getByRole('insertion')).toHaveTextContent('200,000')
    expect(
      within(screen.getByRole('list', { name: 'What changed in monthly_income' })).getByRole(
        'listitem',
      ),
    ).toHaveTextContent('/maximum none → 200,000')
    expect(
      within(screen.getByRole('list', { name: 'What changed in has_guarantor' })).getByRole(
        'listitem',
      ),
    ).toHaveTextContent('/required false → true')
    // the defaults are compared whole (Document 3): the lending default refers, the new one approves
    const [defaultsBefore, defaultsAfter] = within(rowOf('Defaults')).getAllByRole('cell')
    expect(within(defaultsBefore!).getByRole('deletion')).toHaveTextContent('Manual review')
    expect(within(defaultsAfter!).getByRole('insertion')).toHaveTextContent('Approved')
    expect(screen.getByText('2 fields modified · the defaults changed')).toBeVisible()
  })

  it('says so when the two versions are the same', () => {
    renderDiff(empty)

    expect(
      screen.getByText('Version 1 and Proposed have the same rules, fields and defaults.'),
    ).toBeVisible()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('RTL: Hebrew content turns right to left inside left-to-right chrome (snapshot)', () => {
    renderDiff(scriptedProposalEvent.diff)

    const [before] = within(rowOf('R-170')).getAllByRole('cell')
    // the label and the reason are the policy's Hebrew; the condition is field names and numbers
    const label = within(before!).getByText('דחייה: הכנסה חודשית נטו נמוכה מ-8,000')
    expect(label).toHaveAttribute('dir', 'rtl')
    expect(label).toHaveAttribute('lang', 'he')
    expect(within(before!).getByText('monthly_income < 8,000 ILS')).toHaveAttribute('dir', 'ltr')
    expect(screen.getByRole('table')).not.toHaveAttribute('dir')
    expect(rtlSnapshot(screen.getByRole('region', { name: /^Changes from/ }))).toMatchSnapshot()
  })
})
