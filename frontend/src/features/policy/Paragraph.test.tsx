import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { rule, specRules, stylesheet, unported } from '../../test/css'
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

  // the spec (v3.10), section 03: on the sheet the number is the row's first box, its gutter, and the text beside it
  // names its direction in markup, which is what the stylesheet turns an English row by
  it('puts the number first on the sheet, and only an English row is the one the stylesheet turns', () => {
    const turned = ".para--sheet:has(> .para__text[dir='ltr'])"
    const { container, rerender } = render(
      <Paragraph index={1} text={paragraphOne.text} language="he" sheet />,
    )

    const row = container.querySelector('.para')!
    expect([...row.children].map((child) => child.className)).toEqual(['para__n', 'para__text doc'])
    expect(row.matches(turned)).toBe(false)

    rerender(<Paragraph index={1} text="Applicants must be 21." language="en" sheet />)
    expect(container.querySelector('.para')!.matches(turned)).toBe(true)
    // in the margin the number stands after the text, and no row is turned
    rerender(<Paragraph index={1} text="Applicants must be 21." language="en" />)
    expect(container.querySelector('.para')!.matches(turned)).toBe(false)
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

  // the spec (v3.9), section 03: prose breaks a word too long for its line rather than run past its box; a pasted
  // address of 200 characters pushed the Hebrew sentence before it out of the sheet
  it('breaks a word too long for its line', () => {
    expect(rule(stylesheet('features/policy/Paragraph.css'), '.para__text')['overflow-wrap']).toBe(
      'anywhere',
    )
  })

  // the spec (v3.10), sections 03 and 10: a mark sits on the reading-start side, so on the sheet a paragraph's number is
  // the gutter on the right of a Hebrew policy and on the left of an English one. The gutter is the row's first column,
  // and a grid lays its columns out in the row's direction, so the row runs as the policy reads
  it("runs the sheet's row as the policy reads, the number's gutter on the reading-start side", () => {
    const css = stylesheet('features/policy/Paragraph.css')

    expect(rule(css, '.para--sheet')['grid-template-columns']).toBe('36px 1fr')
    expect(rule(css, '.para--sheet').direction).toBe('rtl')
    expect(rule(css, ".para--sheet:has(> .para__text[dir='ltr'])").direction).toBe('ltr')
  })
})
