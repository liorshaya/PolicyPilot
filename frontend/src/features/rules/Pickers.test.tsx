import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { isolate } from '../../shared/i18n/direction'
import { copyRuleset } from '../../test/fixtures/audit'
import { rulesets, secondRuleset } from '../../test/msw/handlers'
import { RulesetSwitcher, VersionPicker } from './Pickers'

// @requirement FR-20

/**
 * What a screen shows: a rule set, and one of its versions (Work Plan day 14: the version picker). An approval on the
 * seeded rule set publishes into the sandbox's own copy, which has the seeded one's name and domain (Document 3,
 * Version lineage), so the switcher has to tell the two apart.
 */
describe('VersionPicker', () => {
  it('lists every version of the rule set with its status, and reports the one chosen', async () => {
    const onChange = vi.fn()
    render(<VersionPicker ruleset={copyRuleset} value={2} onChange={onChange} />)

    const picker = screen.getByRole('combobox', { name: 'Version' })
    expect(picker).toHaveValue('2')
    expect(
      within(picker)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toStrictEqual(['Version 1 · Published', 'Version 2 · Published'])
    await userEvent.selectOptions(picker, '1')

    expect(onChange).toHaveBeenCalledWith(1)
  })
})

describe('RulesetSwitcher', () => {
  it("tells the seeded rule set and the sandbox's copy apart, the Hebrew name isolated", async () => {
    const onChange = vi.fn()
    const seeded = rulesets.rulesets[0]!
    render(
      <RulesetSwitcher
        rulesets={[seeded, copyRuleset, secondRuleset]}
        value={seeded.id}
        onChange={onChange}
      />,
    )

    const switcher = screen.getByRole('combobox', { name: 'Rule set' })
    // the name is Hebrew inside an English label, so it is isolated to keep its own order (Document 2, key decision 5)
    expect(
      within(switcher)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toStrictEqual([
      `${isolate(seeded.name)} · consumer-lending · seeded`,
      `${isolate(copyRuleset.name)} · consumer-lending · your copy`,
      `${isolate(secondRuleset.name)} · rental-deposit`,
    ])
    await userEvent.selectOptions(switcher, copyRuleset.id)

    expect(onChange).toHaveBeenCalledWith(copyRuleset.id)
  })
})
