import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { lendingParagraphs } from '../../test/fixtures/lending'
import { PolicyText } from './PolicyText'

/**
 * The policy as it is read (NFR-5; Document 3, Provenance: the paragraph index is the citation unit; the spec, sections
 * 09 and 10). The text is the committed Hebrew fixture, so the direction and the numbering are the demo's own.
 */
describe('PolicyText', () => {
  it('numbers every paragraph in the document order', () => {
    const { container } = render(<PolicyText language="he" paragraphs={lendingParagraphs} />)

    const numbers = [...container.querySelectorAll('.para > .para__n')].map(
      (one) => one.textContent,
    )
    expect(numbers).toEqual(lendingParagraphs.map((paragraph) => String(paragraph.index)))
  })

  it('reads a Hebrew policy right to left and an English one left to right', () => {
    const { rerender } = render(<PolicyText language="he" paragraphs={lendingParagraphs} />)
    expect(screen.getByText(lendingParagraphs[0]!.text)).toHaveAttribute('dir', 'rtl')

    rerender(
      <PolicyText language="en" paragraphs={[{ index: 1, text: 'Applicants must be 21.' }]} />,
    )
    expect(screen.getByText('Applicants must be 21.')).toHaveAttribute('dir', 'ltr')
  })

  it('marks the paragraph a selected rule cites', () => {
    render(<PolicyText language="he" paragraphs={lendingParagraphs} highlighted={7} />)

    const cited = screen.getByText(lendingParagraphs[6]!.text).closest('.para')
    expect(cited).toHaveAttribute('id', 'paragraph-7')
    expect(cited).toHaveAttribute('aria-current', 'true')
    expect(cited).toHaveClass('para--cited')
    expect(document.querySelectorAll('[aria-current="true"]')).toHaveLength(1)
  })

  // The spec, section 10: on the Policies screen the number is the gutter, before the text at the sheet's size
  it("sets the paragraphs in the sheet's own setting on the Policies screen", () => {
    render(<PolicyText language="he" paragraphs={lendingParagraphs} sheet />)

    const first = screen.getByText(lendingParagraphs[0]!.text)
    expect(first).toHaveClass('para__text', 'doc')
    expect(first).not.toHaveClass('doc--sm')
    expect(first.previousElementSibling).toHaveClass('para__n')
    expect(first.closest('.para')).toHaveClass('para--sheet')
  })

  it('stands what cites a paragraph under it', () => {
    render(
      <PolicyText
        language="he"
        paragraphs={lendingParagraphs}
        sheet
        cites={(index) => (index === 2 ? <span className="chip chip--id">R-120</span> : null)}
      />,
    )

    const second = screen.getByText(lendingParagraphs[1]!.text).closest('.para')!
    expect(second.querySelector('.para__cites')).toHaveTextContent(/^R-120$/)
    expect(
      screen.getByText(lendingParagraphs[0]!.text).closest('.para')!.querySelector('.para__cites'),
    ).toBeNull()
  })

  it('says so when a version has no paragraphs', () => {
    render(<PolicyText language="en" paragraphs={[]} />)

    expect(screen.getByText('This version has no paragraphs.')).toHaveClass('empty__rule--text')
  })
})
