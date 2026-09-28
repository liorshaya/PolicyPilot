import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { rule, stylesheet } from '../../test/css'
import { DecisionTag, VersionTag } from './StatusTag'

/**
 * The only coloured words, and the grammar of an object's state (the spec, sections 04 and 06): Approved is a check,
 * Declined a cross, Manual review a person, Evaluation error stays in ink; a version is dashed while nothing decides with
 * it, solid when the engine may use it, struck when replaced, and on the well when seeded and read-only.
 */
const css = stylesheet('shared/ui/StatusTag.css')

describe('DecisionTag', () => {
  it('marks Approved with a check, Declined with a cross, Manual review with a person, and Evaluation error in ink', () => {
    render(
      <>
        <DecisionTag status="approve" />
        <DecisionTag status="reject" />
        <DecisionTag status="refer" />
        <DecisionTag status="error" />
      </>,
    )
    const glyph = (word: string) => screen.getByText(word).querySelector('svg')?.dataset.icon

    expect(glyph('Approved')).toBe('check')
    expect(glyph('Declined')).toBe('cross')
    expect(glyph('Manual review')).toBe('person')
    expect(glyph('Evaluation error')).toBeUndefined()
    expect(screen.getByText('Declined')).toHaveClass('tag', 'tag--decline')
    expect(screen.getByText('Evaluation error')).toHaveClass('tag--error')
    expect(rule(css, '.tag--error').color).toBe('var(--ink-2)')
  })

  it("writes a rule's action in the words of the action column, with the same colour and glyph", () => {
    // the spec's glossary: Approve · Decline · Manual review in a rule's Action column
    render(
      <>
        <DecisionTag status="approve" action />
        <DecisionTag status="reject" action />
        <DecisionTag status="refer" action />
      </>,
    )

    expect(screen.getByText('Approve')).toHaveClass('tag', 'tag--approve')
    expect(screen.getByText('Decline')).toHaveClass('tag', 'tag--decline')
    expect(screen.getByText('Decline').querySelector('svg')?.dataset.icon).toBe('cross')
    expect(screen.getByText('Manual review')).toHaveClass('tag--refer')
  })

  it('renders the dot form when quiet, without a glyph', () => {
    render(<DecisionTag status="refer" quiet />)
    const tag = screen.getByText('Manual review')

    expect(tag).toHaveClass('tag--refer', 'tag--quiet', 'tag--dot')
    expect(tag.querySelector('svg')).toBeNull()
  })
})

describe('VersionTag', () => {
  it('draws Draft dashed, Published solid, Superseded struck, and "Seeded · read-only" on the well', () => {
    render(
      <>
        <VersionTag status="DRAFT" versionNo={2} />
        <VersionTag status="PUBLISHED" versionNo={1} />
        <VersionTag status="SUPERSEDED" />
        <VersionTag status="PUBLISHED" seeded />
      </>,
    )

    expect(screen.getByText('Draft v2')).toHaveClass('vstatus', 'vstatus--draft')
    expect(rule(css, '.vstatus--draft').border).toBe('1px dashed var(--border-strong)')
    expect(screen.getByText('Published v1')).toHaveClass('vstatus--published')
    expect(rule(css, '.vstatus--published').border).toBe('1px solid var(--ink)')
    expect(screen.getByText('Superseded')).toHaveClass('vstatus--superseded')
    expect(rule(css, '.vstatus--superseded')['text-decoration']).toBe('line-through')
    expect(screen.getByText('Seeded · read-only')).toHaveClass('vstatus--seeded')
    expect(rule(css, '.vstatus--seeded').background).toBe('var(--well)')
  })
})
