import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { rule, specRules, stylesheet, unported } from '../../test/css'
import { Button } from './Button'
import { Refusal } from './Refusal'
import { EmptyState, LoadingRows } from './States'

/**
 * States without dashed frames or shimmer (the spec, section 08): the sheet's own ruled lines for an empty state, still
 * rows and a line of text while loading, and a refusal that names the code, every pointer and its problem, and that
 * nothing was stored.
 */
const css = stylesheet('shared/ui/States.css')

describe('EmptyState', () => {
  it('draws ruled lines: the sentence on the first, the one action on the second, and no dashed frame', () => {
    const { container } = render(
      <EmptyState
        title="Nothing decided yet on version 2."
        description="the seeded set, on this draft"
        action={<Button size="sm">Run 200 cases</Button>}
      />,
    )
    const rules = container.querySelectorAll('.empty > .empty__rule')

    expect(rules).toHaveLength(4)
    expect(rules[0]).toHaveClass('empty__rule--text')
    expect(rules[0]).toHaveTextContent('Nothing decided yet on version 2.')
    expect(rules[1]).toHaveClass('empty__rule--action')
    expect(screen.getAllByRole('button')).toHaveLength(1)
    expect(rules[1]).toHaveTextContent('Run 200 casesthe seeded set, on this draft')
    expect(Object.values(rule(css, '.empty')).join(' ')).not.toMatch(/dashed/)
    expect(rule(css, '.empty__rule')['border-bottom']).toBe('1px solid var(--hairline)')
  })
})

describe('LoadingRows', () => {
  it('shows three still rows and one line of text, with no animation', () => {
    const { container } = render(<LoadingRows label="Loading the rule set" />)

    expect(container.querySelectorAll('.loading > .loading__row')).toHaveLength(3)
    expect(container.querySelector('.loading__text')).toHaveTextContent('Loading the rule set')
    expect(rule(css, '.loading__row').animation).toBeUndefined()
    expect(rule(css, '.loading__row span').animation).toBeUndefined()
  })

  // the spec (v3.9), section 08, Loading: a placeholder narrows with a narrow box, as in the margin, where the rows'
  // fixed columns ran 50px past it
  it("carries the spec's loading rows, whose placeholders narrow with their box", () => {
    const loading = specRules('.loading {', '.progress {')

    expect(loading).toHaveLength(5)
    expect(unported(css, loading)).toEqual([])
    expect(rule(css, '.loading__row')['grid-template-columns']).toBe(
      'minmax(0, 60px) minmax(0, 120px) minmax(0, 90px) minmax(0, 1fr)',
    )
  })
})

describe('Refusal', () => {
  it('names the code in mono, a row per pointer and its problem, and closes with "Nothing was stored."', () => {
    render(
      <Refusal
        code="RULESET_INVALID"
        title="No rule set was written."
        rows={[
          {
            pointer: '/rules/7/condition',
            problem:
              'BETWEEN_RANGE_INVALID · the lower bound 150,000 is above the upper bound 10,000',
          },
          {
            pointer: '/rules/12/actions/0',
            problem: 'ENUM_VALUE_UNKNOWN · "self-employed" is not a value of employment_type',
          },
        ]}
        explanation="The model was asked twice to repair the draft; both attempts failed the validator."
      />,
    )

    expect(screen.getByText('RULESET_INVALID')).toHaveClass('mono')
    expect(screen.getAllByRole('term').map((term) => term.textContent)).toStrictEqual([
      '/rules/7/condition',
      '/rules/12/actions/0',
    ])
    expect(screen.getAllByRole('definition')).toHaveLength(2)
    expect(document.querySelector('.refusal__foot')?.textContent).toMatch(/Nothing was stored\.$/)
  })
})
