import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { rule, stylesheet } from '../../test/css'
import { Provenance } from './Provenance'

/**
 * The provenance line (the spec, section 04): one line, mono, never wrapping; the separators are drawn, never typed, so
 * none can dangle; when it does not fit, segments drop from the end.
 */
const css = stylesheet('shared/ui/Provenance.css')

const SEGMENTS = [
  'Change CR-0001',
  'Proposed',
  'model · 2 of 20 rules touched',
  'base v1',
  'regression on 200 decisions',
]

describe('Provenance', () => {
  it('never wraps, and ends in an ellipsis when it does not fit', () => {
    const line = rule(css, '.prov')

    expect(line['white-space']).toBe('nowrap')
    expect(line['text-overflow']).toBe('ellipsis')
    expect(line.overflow).toBe('hidden')
  })

  it('draws the separators in CSS, so the text carries none', () => {
    const { container } = render(
      <Provenance segments={['Case 17', 'decided on v1', '2026-09-23 10:14:07']} />,
    )
    const line = container.querySelector('.prov')!

    expect([...line.children].map((segment) => segment.textContent)).toStrictEqual([
      'Case 17',
      'decided on v1',
      '2026-09-23 10:14:07',
    ])
    expect(rule(css, '.prov > * + *::before').content).toBe("'·'")
  })

  it('drops its last segments first when maxSegments is given', () => {
    const { container } = render(<Provenance segments={SEGMENTS} maxSegments={3} />)

    expect(
      [...container.querySelector('.prov')!.children].map((segment) => segment.textContent),
    ).toStrictEqual(SEGMENTS.slice(0, 3))
  })
})
