import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { lendingRuleSet } from '../../test/fixtures/lending'
import { specRules, stylesheet, unported } from '../../test/css'
import { Palette } from './Palette'
import type { PaletteItem } from './paletteQuery'
import { Severity } from './Severity'
import { DecisionTag } from './StatusTag'

/**
 * The command palette (the spec, section 08, "Go to anything"): an input, then the matches grouped by kind, each row the
 * object's own chip, its state and what opening does; the arrow keys move the selection, Enter opens it, Escape closes.
 * The rows are the spec's own example: Case 17 on manual review and Case 170 declined (cases-expected.json), R-170 with
 * its label (ruleset.v1.json), a paragraph and the conflict F-1.
 */

const css = stylesheet('shared/ui/Palette.css')
const NO_BREAK_SPACE = String.fromCodePoint(0x00a0)
const R_170 = lendingRuleSet.rules.find((rule) => rule.id === 'R-170')!

const ITEMS: PaletteItem<string>[] = [
  {
    kind: 'case',
    code: 'Case 17',
    digits: '17',
    target: 'case 17',
    state: <DecisionTag status="refer" quiet />,
  },
  {
    kind: 'case',
    code: 'Case 170',
    digits: '170',
    target: 'case 170',
    state: <DecisionTag status="reject" quiet />,
  },
  {
    kind: 'rule',
    code: 'R-170',
    digits: '170',
    target: 'rule R-170',
    state: (
      <bdi className="step__label" lang="he" dir="rtl">
        {R_170.label}
      </bdi>
    ),
  },
  { kind: 'paragraph', code: '1', digits: '1', target: 'paragraph 1' },
  { kind: 'finding', code: 'F-1', digits: '1', target: 'F-1', state: <Severity kind="conflict" /> },
]

/** The palette over the items; before a run of the cases, the session holds no case to reach. */
function renderPalette(ran = true) {
  const onOpen = vi.fn()
  const onClose = vi.fn()
  const items = ran ? ITEMS : ITEMS.filter((item) => item.kind !== 'case')
  render(<Palette items={items} ran={ran} onOpen={onOpen} onClose={onClose} />)
  return { onOpen, onClose, user: userEvent.setup() }
}

/** The rows as a reader sees them: the chip, the state, what opening does. */
function rows(): string[] {
  return screen.getAllByRole('option').map((row) => row.textContent ?? '')
}

describe('Palette', () => {
  it('opens on its input, named Go to, with Esc beside it, and asks for an identifier', () => {
    renderPalette()

    const input = screen.getByRole('combobox', { name: 'Go to' })
    expect(input).toHaveFocus()
    expect(screen.getByRole('dialog', { name: 'Go to' })).toHaveTextContent('Esc')
    expect(screen.getByText('A case number, R-330, ¶ 7, CR-0001, F-1 or v1')).toBeVisible()
  })

  it("draws section 08's rows for 17: the chip, the state and what opening does, grouped by kind", async () => {
    const { user } = renderPalette()

    await user.type(screen.getByRole('combobox', { name: 'Go to' }), '17')

    // each group titled by its kind, in the spec's order
    expect(
      screen.getAllByRole('group').map((group) => group.firstElementChild?.textContent),
    ).toStrictEqual(['Cases', 'Rules'])
    expect(screen.getByRole('group', { name: 'Cases' })).toContainElement(
      screen.getAllByRole('option')[0]!,
    )
    expect(rows()).toStrictEqual([
      'Case 17Manual reviewopen the trace',
      'Case 170Declinedopen the trace',
      `R-170${R_170.label}open in the table`,
    ])
    // the first is selected, drawn on the accent wash
    expect(screen.getAllByRole('option')[0]).toHaveAttribute('aria-selected', 'true')
    const rule = screen.getAllByRole('option')[2]!
    expect(within(rule).getByText(R_170.label)).toHaveAttribute('lang', 'he')
  })

  it('writes a paragraph as its pilcrow chip and a finding with its severity', async () => {
    const { user } = renderPalette()

    await user.type(screen.getByRole('combobox', { name: 'Go to' }), '1')

    expect(rows()).toContain(`¶${NO_BREAK_SPACE}1open in the policy`)
    expect(rows()).toContain('F-1Conflictopen in the review')
  })

  it('moves the selection with the arrow keys and opens the selected row with Enter', async () => {
    const { user, onOpen, onClose } = renderPalette()
    const input = screen.getByRole('combobox', { name: 'Go to' })
    await user.type(input, '17')

    await user.keyboard('{ArrowDown}')
    expect(screen.getAllByRole('option')[1]).toHaveAttribute('aria-selected', 'true')
    expect(input).toHaveAttribute(
      'aria-activedescendant',
      screen.getAllByRole('option')[1]!.getAttribute('id'),
    )
    await user.keyboard('{ArrowDown}{ArrowDown}')
    // past the last it comes round to the first
    expect(screen.getAllByRole('option')[0]).toHaveAttribute('aria-selected', 'true')
    await user.keyboard('{ArrowUp}{Enter}')

    expect(onOpen).toHaveBeenCalledWith(ITEMS[2])
    expect(onClose).toHaveBeenCalled()
  })

  it('opens a row that is clicked', async () => {
    const { user, onOpen } = renderPalette()
    await user.type(screen.getByRole('combobox', { name: 'Go to' }), '17')

    await user.click(screen.getByRole('option', { name: /Case 170/ }))

    expect(onOpen).toHaveBeenCalledWith(ITEMS[1])
  })

  it('closes on Escape without opening anything', async () => {
    const { user, onOpen, onClose } = renderPalette()
    await user.type(screen.getByRole('combobox', { name: 'Go to' }), '17')

    await user.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalled()
    expect(onOpen).not.toHaveBeenCalled()
  })

  it('says how to reach a case before a run, and when nothing matches', async () => {
    const { user } = renderPalette(false)
    const input = screen.getByRole('combobox', { name: 'Go to' })

    await user.type(input, '17')
    expect(screen.getByText('Run the cases to reach a case by its number')).toBeVisible()
    expect(rows()).toStrictEqual([`R-170${R_170.label}open in the table`])

    await user.clear(input)
    await user.type(input, 'R-999')
    expect(screen.getByText('Nothing matches R-999.')).toBeVisible()
    expect(screen.queryAllByRole('option')).toHaveLength(0)
  })

  it("draws the spec's palette rules as it writes them", () => {
    const palette = specRules('/* Command palette', '.shortcuts')

    expect(palette.map(([selector]) => selector)).toHaveLength(7)
    expect(unported(css, palette)).toEqual([])
  })
})
