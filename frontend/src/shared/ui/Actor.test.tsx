import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { rule, stylesheet } from '../../test/css'
import { Actor } from './Actor'

/**
 * Who did what (the spec, section 04): the model's dotted circle, the engine's solid square, a person's silhouette, the
 * system's outlined square; a mark always carries its word or a title.
 */
const css = stylesheet('shared/ui/Actor.css')

describe('Actor', () => {
  it("draws each actor's mark beside its word", () => {
    render(
      <>
        <Actor kind="model">Model · proposed</Actor>
        <Actor kind="engine">Engine · 1.0.0</Actor>
        <Actor kind="person">Analyst</Actor>
        <Actor kind="system">System · reset</Actor>
      </>,
    )

    for (const [word, kind] of [
      ['Model · proposed', 'model'],
      ['Engine · 1.0.0', 'engine'],
      ['Analyst', 'person'],
      ['System · reset', 'system'],
    ] as const) {
      const actor = screen.getByText(word)
      expect(actor).toHaveClass('actor', `actor--${kind}`)
      expect(actor.querySelector('.actor__mark')).toHaveAttribute('aria-hidden', 'true')
    }
    expect(rule(css, '.actor--model .actor__mark').border).toBe('1.5px dashed var(--ink-2)')
  })

  it('names a mark that stands alone in its title', () => {
    const { container } = render(<Actor kind="system" />)

    expect(container.querySelector('.actor--system')).toHaveAttribute('title', 'System')
  })

  it('says what a mark alone stands for when it is told', () => {
    // the spec, section 07: the Source column's person mark for a rule an analyst wrote
    render(<Actor kind="person" title="Written by an analyst" />)

    expect(screen.getByTitle('Written by an analyst')).toHaveClass('actor', 'actor--person')
  })
})
