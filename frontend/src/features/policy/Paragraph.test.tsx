import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { specRules, stylesheet, unported } from '../../test/css'
import { lendingParagraphs, lendingRuleSet } from '../../test/fixtures/lending'
import { Paragraph } from './Paragraph'

// @requirement FR-4

/**
 * One paragraph of a policy with the span a rule quotes (the spec, section 09, "Policy document and its list", and the
 * Rules margin of section 10; Document 3, Provenance: the paragraph index is the citation unit and the quote is a span
 * of its text). The paragraph and the quote are the committed lending fixture's own.
 */

const paragraphOne = lendingParagraphs.find((paragraph) => paragraph.index === 1)!
const r110 = lendingRuleSet.rules.find((rule) => rule.id === 'R-110')!
const quote = r110.provenance.kind === 'quoted' ? r110.provenance.quote : ''

describe('Paragraph', () => {
  it("marks the span a rule quotes inside the paragraph, in the paragraph's own direction", () => {
    render(<Paragraph index={1} text={paragraphOne.text} language="he" quote={quote} cited />)

    const marked = screen.getByText(quote)
    expect(marked.tagName).toBe('MARK')
    expect(marked).toHaveClass('para__hl')
    const text = marked.closest('p')!
    expect(text).toHaveTextContent(paragraphOne.text)
    expect(text).toHaveClass('para__text', 'doc', 'doc--sm')
    expect(text).toHaveAttribute('dir', 'rtl')
    expect(text).toHaveAttribute('lang', 'he')
    expect(text.closest('.para')).toHaveClass('para--cited')
    expect(text.nextElementSibling).toHaveTextContent('1')
  })

  it('marks nothing when the quote is not a span of the paragraph', () => {
    render(<Paragraph index={1} text={paragraphOne.text} language="he" quote="נוסח אחר" />)

    expect(document.querySelector('mark')).toBeNull()
    expect(document.querySelector('.para__text')).toHaveTextContent(paragraphOne.text)
  })
})

describe('Paragraph.css', () => {
  it("carries every rule of the spec's paragraphs, with the spec's declarations", () => {
    const paragraphs = specRules('/* Policy document and its list */', '/* Access gate').filter(
      ([selector]) => selector.startsWith('.para'),
    )

    expect(paragraphs).toHaveLength(11)
    expect(unported(stylesheet('features/policy/Paragraph.css'), paragraphs)).toEqual([])
  })
})
