import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Finding, Review, ReviewFinding, Rule, RuleSetDocument } from '../../api/types'
import { rule as cssRule, stylesheet } from '../../test/css'
import { lendingRuleSet } from '../../test/fixtures/lending'
import { DecisionTable } from './DecisionTable'

// @requirement FR-6

/**
 * The decision table (the Register spec, section 07; Document 3, Decision Table Rendering and Provenance; Document 6,
 * Frontend Test Design: the Hebrew labels read right to left and a refused pointer lands on the cell it names). The
 * rule set is the committed lending fixture; the words, marks and sizes are the spec's own, read through the rule of
 * Table.css that draws them, since jsdom lays nothing out.
 */

const css = stylesheet('shared/ui/Table.css')
const NO_BREAK_SPACE = String.fromCodePoint(0x00a0)

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

/** The row of a rule, found by the id the rule cell writes under its label. */
function rowOf(ruleId: string): HTMLElement {
  const row = screen.getByText(ruleId, { selector: '.t-rule__id' }).closest('tr')
  if (!row) {
    throw new Error(`no row for ${ruleId}`)
  }
  return row
}

/** A finding of the review, as fixtures/eval/policies/consumer-lending/seeded.findings.json anchors them. */
function reviewed(overrides: Partial<ReviewFinding>): ReviewFinding {
  return {
    id: 'F-1',
    kind: 'ambiguity',
    severity: 'warning',
    ruleIds: ['R-420'],
    paragraphIndexes: [4],
    message: 'הכנסה יציבה אינה מוגדרת',
    suggestion: 'להוסיף סימון לבדיקה ידנית',
    confidence: 0.8,
    blocking: false,
    ...overrides,
  }
}

/** SF-1, SF-2 and SF-4 of the seeded findings: the undefined stable income, the age conflict, the uncovered clause. */
const review: Review = {
  status: 'DONE',
  promptVersion: 'v1',
  coverage: {},
  findings: [
    reviewed({}),
    reviewed({
      id: 'F-2',
      kind: 'conflict',
      severity: 'error',
      ruleIds: ['R-110', 'R-115'],
      message: 'סעיף 1 מגביל את הגיל ל-70 וסעיף 8 מתיר גמלאים עד 75',
      blocking: true,
    }),
    reviewed({
      id: 'F-3',
      kind: 'gap',
      ruleIds: [],
      message: 'סעיף הוותק לעצמאים אינו מכוסה',
      blocking: true,
    }),
  ],
}

describe('DecisionTable, the header', () => {
  it('draws two header rows: the groups, then the fields in mono with the unit and the dashed derived tag', () => {
    renderTable()

    const [groups, names] = within(screen.getByRole('table')).getAllByRole('row')
    const groupNames = within(groups!)
      .getAllByRole('columnheader')
      .map((header) => header.textContent)
      .filter((text) => text !== '' && text !== 'Findings')
    expect(groupNames).toStrictEqual(['Rule', 'Conditions', 'Source', 'Action'])
    // a header's name says its unit and whether it is derived, which the eye reads from the small type beside it
    expect(
      within(names!)
        .getAllByRole('columnheader')
        .map((header) => header.getAttribute('aria-label') ?? header.textContent),
    ).toStrictEqual([
      'Label and id',
      'Priority',
      'age, years',
      'requested_amount, ₪',
      'term_months',
      'employment_type',
      'employment_months',
      'monthly_income, ₪',
      'credit_events_24m',
      'has_guarantor',
      'debt_to_income, derived',
    ])
    expect(
      within(screen.getByRole('columnheader', { name: 'age, years' })).getByText('years'),
    ).toHaveClass('unit')
    expect(
      within(screen.getByRole('columnheader', { name: 'requested_amount, ₪' })).getByText('₪'),
    ).toHaveClass('unit')
    expect(
      within(screen.getByRole('columnheader', { name: 'debt_to_income, derived' })).getByText(
        'derived',
      ),
    ).toHaveClass('derived')
    expect(cssRule(css, '.t-field')['font-family']).toBe('var(--font-mono)')
    expect(cssRule(css, '.t-field .derived').border).toBe('1px dashed var(--border-strong)')
  })

  it('breaks a field name at its underscores', () => {
    renderTable()

    const header = screen.getByRole('columnheader', { name: 'credit_events_24m' })
    expect(header.querySelectorAll('wbr')).toHaveLength(2)
  })

  it("carries the field's description, type, unit, values and paragraph in the header's title", () => {
    renderTable()

    // the spec, section 07, the header of age and of employment_type
    expect(screen.getByRole('columnheader', { name: 'age, years' })).toHaveAttribute(
      'title',
      'גיל המבקש בעת הגשת הבקשה · integer, years, 0 to 120 · ¶ 1',
    )
    expect(screen.getByRole('columnheader', { name: 'employment_type' })).toHaveAttribute(
      'title',
      'מעמד תעסוקתי · salaried, self_employed, retired, unemployed · ¶ 3',
    )
  })

  it('keeps both header rows in view, the groups at the top and the fields 26px under them', () => {
    expect(cssRule(css, '.table th')).toMatchObject({ position: 'sticky', top: '0' })
    expect(cssRule(css, '.table--decision thead tr:nth-child(2) th').top).toBe('26px')
    expect(cssRule(css, '.t-group').height).toBe('26px !important')
  })

  it('shows a scrollbar the reader can see', () => {
    expect(cssRule(css, '.table-scroll::-webkit-scrollbar').height).toBe('10px')
    expect(cssRule(css, '.table-scroll::-webkit-scrollbar-thumb').background).toBe(
      'var(--border-strong)',
    )
  })
})

describe('DecisionTable, the rows', () => {
  it('has one row header per rule, the count the end-to-end test relies on', () => {
    renderTable()

    expect(screen.getAllByRole('rowheader')).toHaveLength(lendingRuleSet.rules.length)
  })

  it('groups the rules into bands, each with its priority range', () => {
    renderTable()

    const band = screen.getByRole('columnheader', { name: 'Hard eligibility gates 100–199' })
    expect(within(band).getByText('100–199')).toHaveClass('t-band__count')
    expect(cssRule(css, '.t-band th').position).toBe('static')
  })

  it('reads the Hebrew label first, right to left, with the id under it in mono', () => {
    renderTable()

    const label = screen.getByText('דחייה: גיל נמוך מ-21')
    const id = screen.getByText('R-100', { selector: '.t-rule__id' })
    expect(label.tagName).toBe('BDI')
    expect(label).toHaveAttribute('lang', 'he')
    expect(label).toHaveAttribute('dir', 'rtl')
    expect(label.compareDocumentPosition(id) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(cssRule(css, '.t-rule__id')['font-family']).toBe('var(--font-mono)')
  })

  it('end-aligns the priority, in mono', () => {
    renderTable()

    expect(within(rowOf('R-100')).getByText('100')).toHaveClass('t-prio')
    expect(cssRule(css, '.t-prio')['text-align']).toBe('end')
    expect(cssRule(css, 'td.t-prio')['font-family']).toBe('var(--font-mono)')
  })

  it('leaves a cell empty, drawn as a dash, where a rule says nothing about the field', () => {
    renderTable()

    // R-100 compares the age alone, so the other eight columns are empty
    expect(within(rowOf('R-100')).getAllByRole('cell', { name: 'no comparison' })).toHaveLength(8)
    expect(cssRule(css, '.t-empty::before').content).toBe("'—'")
  })

  it('marks the selected row with the accent bar and tells assistive technology which one it is', () => {
    renderTable({ selectedRuleId: 'R-330' })

    expect(rowOf('R-330')).toHaveClass('t-selected')
    expect(rowOf('R-330')).toHaveAttribute('aria-current', 'true')
    expect(cssRule(css, '.t-selected > td:first-child')['box-shadow']).toBe(
      'inset 2px 0 0 var(--accent)',
    )
  })

  it('freezes the rule column at the start and the action column at the end', () => {
    renderTable()

    const ruleCell = screen.getByRole('rowheader', { name: /R-100/ })
    expect(ruleCell).toHaveClass('t-frozen', 't-frozen--shadow')
    expect(within(rowOf('R-100')).getByText('Decline').closest('td')).toHaveClass('t-frozen-end')
    expect(cssRule(css, '.t-frozen')).toMatchObject({
      position: 'sticky',
      'inset-inline-start': '0',
    })
    expect(cssRule(css, '.t-frozen-end')).toMatchObject({
      position: 'sticky',
      'inset-inline-end': '0',
    })
  })

  it('dims an inactive rule, says "inactive" beside its action, and strikes nothing through', () => {
    renderTable({ document: documentOf({ ...rule('R-310'), enabled: false }) })

    const row = rowOf('R-310')
    expect(row).toHaveClass('t-disabled')
    const action = within(row).getByText('Manual review').closest('td')!
    expect(within(action).getByText('inactive')).toBeInTheDocument()
    expect(within(action).getByText('Manual review')).toHaveClass('tag--quiet', 'tag--dot')
    // a strike means removed, and only the diff uses it (the spec, section 07)
    expect(row.querySelector('s, del, strike')).toBeNull()
    expect(cssRule(css, '.t-disabled > td')).toStrictEqual({ color: 'var(--ink-3)' })
  })
})

describe('DecisionTable, the cells', () => {
  it('writes a comparison as its operator in ink-3 and its value in ink, the unit left to the header', () => {
    renderTable()

    const cell = within(rowOf('R-100')).getByText('21').closest('td')!
    expect(cell).toHaveClass('t-cmp')
    expect(within(cell).getByText('<')).toHaveClass('op')
    expect(cell).toHaveTextContent(/^<21$/)
    expect(cssRule(css, '.t-cmp .op').color).toBe('var(--ink-3)')
    expect(cssRule(css, '.t-cmp .val').color).toBe('var(--ink)')
  })

  it('writes enum values in mono', () => {
    renderTable()

    expect(within(rowOf('R-140')).getByText('unemployed')).toHaveClass('val', 'enum')
    expect(cssRule(css, '.t-cmp .enum')['font-family']).toBe('var(--font-mono)')
  })

  it("shows a negated range in the field's column, and never as an input", () => {
    renderTable({ onEditCell: vi.fn() })

    const row = rowOf('R-120')
    expect(within(row).getByText('[10,000 .. 150,000]').closest('td')).toHaveTextContent(
      '∉[10,000 .. 150,000]',
    )
    expect(screen.queryByLabelText('R-120, requested_amount')).not.toBeInTheDocument()
  })

  it('marks an expression with ƒ and leaves it to the margin', () => {
    renderTable({ onEditCell: vi.fn() })

    const cell = within(rowOf('R-116')).getByText('ƒ').closest('td')!
    expect(cell).toHaveClass('t-cmp', 't-cmp--complex')
    expect(cell).toHaveTextContent('ƒ≥78 − term_months / 12')
    expect(screen.queryByLabelText('R-116, age')).not.toBeInTheDocument()
  })

  it('shows no input on a published or seeded version', () => {
    renderTable()

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })

  it("edits a draft's cell as an input in the same grammar", async () => {
    const user = userEvent.setup()
    const onEditCell = vi.fn()
    renderTable({ document: documentOf(rule('R-100')), onEditCell })

    const cell = screen.getByLabelText('R-100, age')
    expect(cell).toHaveValue('< 21')
    expect(cell).toHaveClass('input')
    await user.clear(cell)
    await user.type(cell, '< 23{Enter}')

    expect(onEditCell).toHaveBeenCalledExactlyOnceWith(
      'R-100',
      { field: 'age', op: 'lt', value: 21 },
      { field: 'age', op: 'lt', value: 23 },
    )
    expect(cssRule(css, '.t-cell-edit .input').height).toBe('26px')
    // at rest the draft's cell reads as text, its input showing on hover and focus
    expect(
      cssRule(css, '.t-cell-edit:not(.t-cell-invalid) .input:not(:hover):not(:focus)'),
    ).toStrictEqual({
      'border-color': 'transparent',
      background: 'transparent',
    })
  })

  it('says what to write instead when the unit is typed into the cell', async () => {
    const user = userEvent.setup()
    const onEditCell = vi.fn()
    renderTable({ document: documentOf(rule('R-170')), onEditCell })

    const cell = screen.getByLabelText('R-170, monthly_income')
    expect(cell).toHaveValue('< 8,000')
    await user.clear(cell)
    await user.type(cell, '< 8,000 ₪{Enter}')

    // the spec, section 07: R-170's bubble
    expect(screen.getByText('Write the number alone; the column is in ₪.')).toHaveClass(
      't-cell-problem',
    )
    expect(cell).toHaveAttribute('aria-invalid', 'true')
    expect(cell.closest('td')).toHaveClass('t-cell-edit', 't-cell-invalid')
    expect(onEditCell).not.toHaveBeenCalled()
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

    expect(cell).toHaveValue('< 21')
    expect(screen.queryByText(/Write =/)).not.toBeInTheDocument()
    expect(onEditCell).not.toHaveBeenCalled()
  })

  it('commits an edit when the cell loses focus', async () => {
    const user = userEvent.setup()
    const onEditCell = vi.fn()
    renderTable({ document: documentOf(rule('R-100')), onEditCell })

    const cell = screen.getByLabelText('R-100, age')
    await user.clear(cell)
    await user.type(cell, '< 23')
    await user.tab()

    expect(onEditCell).toHaveBeenCalledExactlyOnceWith(
      'R-100',
      { field: 'age', op: 'lt', value: 21 },
      { field: 'age', op: 'lt', value: 23 },
    )
  })

  it('shows the problem of a refused change under the cell its pointer names', () => {
    renderTable({
      document: documentOf(rule('R-010'), rule('R-020'), rule('R-100')),
      onEditCell: vi.fn(),
      problems: [
        { path: '/rules/2/condition/value', problem: 'below the minimum of the field' },
        {
          path: '/rules/1/actions/0/value',
          problem: 'the expression divides by a field that may be 0',
        },
      ],
    })

    // the states matrix of the spec, section 11: "422 on a cell: the bubble under the cell"
    const problem = screen.getByText('below the minimum of the field')
    expect(problem).toHaveClass('t-cell-problem')
    expect(problem.closest('td')).toContainElement(screen.getByLabelText('R-100, age'))
    // a pointer that names no cell marks its rule in the gutter
    expect(
      within(rowOf('R-020')).getByTitle('the expression divides by a field that may be 0'),
    ).toHaveClass('sev', 'sev--error')
  })
})

describe('DecisionTable, the source and the action', () => {
  it('shows the source as the paragraph chip, the person mark, or Pending', () => {
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

    // hovering the chip shows the text it cites (the spec, section 06)
    const chip = screen.getByTitle('גילו 21 עד 70 בעת הגשת הבקשה')
    expect(chip).toHaveClass('chip', 'chip--para')
    // the pilcrow and its number held together by a no-break space (the spec, section 03)
    expect(chip.textContent).toBe(`¶${NO_BREAK_SPACE}1`)
    expect(within(rowOf('R-200')).getByTitle('Written by an analyst')).toHaveClass(
      'actor',
      'actor--person',
    )
    // a rule a change request touched shows Pending until approval (the spec's glossary)
    expect(within(rowOf('R-900')).getByText('Pending')).toBeInTheDocument()
  })

  it('shows the action as a decision tag in the words of the action column, or the mono action', () => {
    renderTable()

    expect(within(rowOf('R-100')).getByText('Decline')).toHaveClass('tag', 'tag--decline')
    expect(within(rowOf('R-100')).getByText('Decline')).not.toHaveClass('tag--quiet')
    expect(within(rowOf('R-900')).getByText('Approve')).toHaveClass('tag--approve')
    expect(within(rowOf('R-330')).getByText('Manual review')).toHaveClass('tag--refer')
    // a derivation and a flag are machine tokens, breaking at an underscore
    const derivation = within(rowOf('R-010')).getByTitle('set monthly_installment')
    expect(derivation).toHaveClass('mono', 't-action')
    expect(derivation).toHaveTextContent('set monthly_installment')
    expect(derivation.querySelectorAll('wbr')).toHaveLength(1)
    expect(within(rowOf('R-420')).getByTitle('flag STABLE_INCOME_MANUAL_CHECK')).toHaveClass(
      't-action',
    )
  })

  it('writes each of several actions on its own line', () => {
    renderTable({
      document: documentOf({
        ...rule('R-410'),
        actions: [
          { type: 'flag', code: 'INCOME_NEAR_MINIMUM' },
          { type: 'decide', outcome: 'refer', terminal: true },
        ],
      }),
    })

    const action = within(rowOf('R-410')).getByTitle('flag INCOME_NEAR_MINIMUM').closest('td')!
    expect(action.querySelectorAll('.t-action-line')).toHaveLength(2)
    expect(within(action).getByText('Manual review')).toHaveClass('tag--refer')
  })
})

describe('DecisionTable, the findings', () => {
  it('puts a mark in the gutter for each finding on the row, named for assistive technology', () => {
    renderTable({ review })

    const conflict = within(rowOf('R-110')).getByTitle('F-2 Conflict')
    expect(conflict).toHaveClass('sev', 'sev--error')
    expect(conflict.closest('td')).toHaveClass('t-gutter')
    expect(within(conflict).getByText('F-2 Conflict')).toHaveClass('sr-only')
    expect(within(rowOf('R-115')).getByTitle('F-2 Conflict')).toHaveClass('sev--error')
    expect(within(rowOf('R-420')).getByTitle('F-1 Ambiguity')).toHaveClass('sev--warning')
    expect(rowOf('R-100').querySelector('.t-gutter .sev')).toBeNull()
    expect(cssRule(css, '.t-gutter').width).toBe('20px')
  })

  it('underlines the cell a finding names', () => {
    const findings: Finding[] = [
      {
        code: 'DSL-201',
        severity: 'error',
        path: '/rules/2/condition/value',
        message: 'below the minimum of the field',
        ruleIds: ['R-100'],
        fieldNames: ['age'],
      },
      {
        code: 'DSL-311',
        severity: 'warning',
        path: '/rules/10/condition/all/1',
        message: 'the comparison can never be true',
        ruleIds: ['R-150'],
        fieldNames: ['employment_months'],
      },
    ]
    renderTable({ findings })

    expect(within(rowOf('R-100')).getByText('21').closest('td')).toHaveClass('t-underline-err')
    expect(within(rowOf('R-150')).getByText('6').closest('td')).toHaveClass('t-underline-warn')
    expect(within(rowOf('R-150')).getByText('salaried').closest('td')).not.toHaveClass(
      't-underline-warn',
    )
    expect(cssRule(css, '.t-underline-err')['box-shadow']).toBe(
      'inset 0 -2px 0 var(--decline-mark)',
    )
  })
})

describe('DecisionTable, the strip', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('states the hit policy above the table', () => {
    renderTable()

    const strip = screen.getByText('First hit').closest('.table__strip')!
    expect(screen.getByText('First hit').tagName).toBe('B')
    expect(strip).toHaveTextContent(/^First hit · by priority; the first terminal decision stands/)
  })

  it('counts the findings that block publishing and those that warn', () => {
    renderTable({ review })

    expect(screen.getByText('2 block')).toHaveClass('sev', 'sev--error')
    expect(screen.getByText('1 warn')).toHaveClass('sev', 'sev--warning')
  })

  it('says nothing about findings when there are none', () => {
    renderTable()

    expect(screen.queryByText(/block$/)).not.toBeInTheDocument()
    expect(screen.queryByText(/warn$/)).not.toBeInTheDocument()
  })

  it("counts the fields to the right of the view from the container's width, and follows it", () => {
    let resized: () => void = () => undefined
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn()
        unobserve = vi.fn()
        disconnect = vi.fn()
        constructor(callback: () => void) {
          resized = callback
        }
      },
    )
    let viewRight = 900
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockImplementation(function (
      this: Element,
    ) {
      const fields = [...document.querySelectorAll('th.t-field')]
      const index = fields.indexOf(this)
      // nine field columns ending at 300, 400 … 1,100; the frozen action column 150px wide at the view's end
      const right = this.classList.contains('table-scroll')
        ? viewRight
        : index >= 0
          ? 300 + index * 100
          : 0
      const width = this.matches('th.t-frozen-end') ? 150 : 100
      return {
        right,
        width,
        left: right - width,
        top: 0,
        bottom: 0,
        height: 0,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }
    })
    renderTable()
    // an observer reports the size it starts observing, as a browser's does
    act(() => resized())

    // the view ends at 900 − 150: the columns ending at 800, 900, 1000 and 1100 are out of it
    expect(screen.getByText('4 fields to the right ›')).toHaveClass('table__more')

    viewRight = 1300
    act(() => resized())

    expect(screen.queryByText(/fields? to the right/)).not.toBeInTheDocument()
  })
})

describe('DecisionTable, a tag', () => {
  it('keeps the rows of the chosen tag', () => {
    renderTable({ tag: 'credit_history' })

    expect(screen.getAllByRole('rowheader').map((header) => header.textContent)).toStrictEqual([
      'דחייה: שני אירועי אשראי שליליים או יותרR-220',
      'בדיקת חתם: אירוע אשראי אחד ללא ערבR-330',
    ])
  })
})
