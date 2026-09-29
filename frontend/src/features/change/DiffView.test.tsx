import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import type { Diff } from '../../api/types'
import { rule, specRules, stylesheet, unported } from '../../test/css'
import { scriptedProposalEvent } from '../../test/fixtures/change'
import { lendingRuleSet } from '../../test/fixtures/lending'
import { rtlSnapshot } from '../../test/rtlSnapshot'
import { DiffView } from './DiffView'

// @requirement FR-18
// @requirement FR-20

/**
 * The diff (the spec, section 09, "The change request": "one row per changed cell (rule · field · before → after) with
 * the changed value tinted, the whole rule struck when removed; unchanged rules collapse; side-by-side is the
 * alternative, remembered per person"; Document 3, Structural diff: "the side-by-side view highlights
 * condition.value: 8000 → 9000 rather than the whole rule"; Work Plan day 14: added, removed and modified rows, and an
 * RTL snapshot). The scripted diff is built from the committed fixtures; the rest are rules of the lending rule set.
 */

/** Where the reader's choice of view is kept in this browser. */
const VIEW_KEY = 'pp-diff-view'

const r160 = lendingRuleSet.rules.find((rule) => rule.id === 'R-160')!

const empty: Diff = {
  fields: { added: [], removed: [], modified: [] },
  rules: { added: [], removed: [], modified: [] },
  defaults: null,
}

function renderDiff(diff: Diff, rules = lendingRuleSet.rules) {
  return render(
    <DiffView
      diff={diff}
      language="he"
      fields={lendingRuleSet.fields}
      rules={rules}
      beforeLabel="Version 1"
      afterLabel="Proposed"
    />,
  )
}

/** The unified view's rows under its head, as their cells' text. */
function unifiedRows(): string[][] {
  return [
    ...document.querySelectorAll('.udiff__row:not(.udiff__row--head):not(.udiff__row--collapsed)'),
  ].map((row) => [...row.children].map((cell) => cell.textContent ?? ''))
}

const unifiedTests = () => {
  it('writes one row per changed cell, rule · field · before → after, the changed value tinted', () => {
    renderDiff(scriptedProposalEvent.diff)

    const view = screen.getByRole('region', { name: 'Changes from Version 1 to Proposed' })
    expect(view.querySelector('.udiff__row--head')).toHaveTextContent('RuleFieldVersion 1Proposed')
    const rows = unifiedRows()
    // Document 3: R-170's threshold 8,000 → 9,000, its label, its action and its source; R-410's band by its two ends
    expect(rows.map((row) => row.slice(0, 2))).toStrictEqual([
      ['R-170', 'monthly_income'],
      ['R-170', 'label'],
      ['R-170', 'action'],
      ['R-170', 'source'],
      ['R-410', 'monthly_income'],
      ['R-410', 'source'],
    ])
    const [threshold, label, , source, band] = [
      ...view.querySelectorAll('.udiff__row:not(.udiff__row--head)'),
    ]
    expect(threshold!.querySelector('.del')).toHaveTextContent('8,000')
    expect(threshold!.querySelector('.add')).toHaveTextContent('9,000')
    expect(threshold).toHaveTextContent('<8,000→<9,000')
    expect(label!.querySelector('.del')).toHaveTextContent('8,000')
    expect(label!.querySelector('.add')).toHaveTextContent('9,000')
    expect(label).toHaveTextContent('…נמוכה מ-8,000→…נמוכה מ-9,000')
    expect(source).toHaveTextContent('¶ 4→Pending')
    expect([...band!.querySelectorAll('.del')].map((cell) => cell.textContent)).toStrictEqual([
      '8,000',
      '9,000',
    ])
    expect([...band!.querySelectorAll('.add')].map((cell) => cell.textContent)).toStrictEqual([
      '9,000',
      '10,000',
    ])
  })

  it('writes an added rule whole, and strikes a removed one whole', () => {
    const added = { ...r160, id: 'R-131', label: 'דחייה: הלוואה מעל 80,000 ללא ערב', priority: 131 }
    renderDiff({ ...empty, rules: { added: [added], removed: [r160], modified: [] } })

    const rows = [
      ...document.querySelectorAll(
        '.udiff__row:not(.udiff__row--head):not(.udiff__row--collapsed)',
      ),
    ]
    expect(rows[0]).toHaveTextContent('R-131')
    expect(rows[0]).toHaveTextContent('Added')
    expect(rows[0]!.querySelector('.add')).toHaveTextContent(added.label)
    expect(rows[1]).toHaveClass('udiff__row--removed')
    expect(rows[1]).toHaveTextContent('Removed')
    expect(rows[1]!.querySelector('.step__label')).toHaveTextContent(r160.label)
  })

  it('collapses the rules that did not change into one row with their count, and shows them on request', async () => {
    renderDiff(scriptedProposalEvent.diff)
    const user = userEvent.setup()

    const collapsed = document.querySelector('.udiff__row--collapsed')!
    // the lending rule set's twenty rules, two of them changed
    expect(collapsed).toHaveTextContent('18 unchanged rules · Show · Side by side')

    await user.click(within(collapsed as HTMLElement).getByRole('button', { name: 'Show' }))

    const unchanged = [...document.querySelectorAll('.udiff__row--he')]
    expect(unchanged).toHaveLength(18)
    expect(unchanged[0]).toHaveTextContent('R-010')
    expect(unchanged[0]).toHaveTextContent('unchanged')
  })
}

describe('DiffView · unified, the first view', () => {
  beforeEach(() => localStorage.removeItem(VIEW_KEY))

  unifiedTests()

  it('switches to side by side, and remembers the choice in this browser', async () => {
    const { unmount } = renderDiff(scriptedProposalEvent.diff)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Side by side' }))

    expect(rowOf('R-170')).toBeInTheDocument()
    expect(localStorage.getItem(VIEW_KEY)).toBe('side-by-side')
    unmount()
    renderDiff(scriptedProposalEvent.diff)
    expect(rowOf('R-170')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Unified' }))
    expect(localStorage.getItem(VIEW_KEY)).toBe('unified')
    expect(document.querySelector('.udiff')).not.toBeNull()
  })

  it('says so when the two versions are the same', () => {
    renderDiff(empty)

    expect(
      screen.getByText('Version 1 and Proposed have the same rules, fields and defaults.'),
    ).toBeVisible()
    expect(document.querySelector('.udiff')).toBeNull()
  })
})

function rowOf(ruleId: string): HTMLElement {
  const header = screen.getByRole('rowheader', { name: new RegExp(`^${ruleId}\\b`) })
  const row = header.closest('tr')
  if (row === null) {
    throw new Error(`no row for ${ruleId}`)
  }
  return row
}

describe('DiffView · side by side, the alternative', () => {
  beforeEach(() => localStorage.setItem(VIEW_KEY, 'side-by-side'))
  afterEach(() => localStorage.removeItem(VIEW_KEY))

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

describe('DiffView.css', () => {
  it("carries every rule of the spec's unified diff, with the spec's declarations", () => {
    const unified = specRules('/* Diff, unified: one row per changed cell */', '.matrix {')

    expect(unified).toHaveLength(15)
    expect(unported(stylesheet('features/change/DiffView.css'), unified)).toEqual([])
  })

  // the spec (v3.5): a Hebrew value stands at its cell's start and reads right to left inside, as a chip does; the
  // owner found a label's excerpt hugging the arrow at the far end of a wide cell
  it("stands a Hebrew cell at its row's start, reading right to left inside", () => {
    expect(rule(stylesheet('features/change/DiffView.css'), '.udiff .step__label')).toMatchObject({
      'justify-self': 'start',
    })
    expect(rule(stylesheet('features/cases/TraceView.css'), '.step__label').direction).toBe('rtl')
  })
})
