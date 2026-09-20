import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { lendingParagraphs } from '../../test/fixtures/lending'
import { PolicyText } from './PolicyText'

/**
 * The policy as it is read (NFR-5; Document 3, Provenance: the paragraph index is the citation unit). The text is
 * the committed Hebrew fixture, so the direction and the numbering are the demo's own.
 */
describe('PolicyText', () => {
  it('numbers every paragraph in the document order', () => {
    render(<PolicyText language="he" paragraphs={lendingParagraphs} />)

    expect(screen.getAllByRole('listitem')).toHaveLength(lendingParagraphs.length)
    expect(screen.getByText('1')).toBeInTheDocument()
    expect(screen.getByText(String(lendingParagraphs.length))).toBeInTheDocument()
  })

  it('reads a Hebrew policy right to left and an English one left to right', () => {
    const { rerender } = render(<PolicyText language="he" paragraphs={lendingParagraphs} />)
    expect(screen.getByRole('list')).toHaveAttribute('dir', 'rtl')

    rerender(
      <PolicyText language="en" paragraphs={[{ index: 1, text: 'Applicants must be 21.' }]} />,
    )
    expect(screen.getByRole('list')).toHaveAttribute('dir', 'ltr')
  })

  it('marks the paragraph a selected rule cites', () => {
    render(<PolicyText language="he" paragraphs={lendingParagraphs} highlighted={7} />)

    const cited = screen
      .getAllByRole('listitem')
      .find((item) => item.getAttribute('aria-current') === 'true')
    expect(cited).toHaveAttribute('id', 'paragraph-7')
    expect(cited).toHaveTextContent(lendingParagraphs[6]!.text)
  })

  it('says so when a version has no paragraphs', () => {
    render(<PolicyText language="en" paragraphs={[]} />)

    expect(screen.getByText('This version has no paragraphs.')).toBeInTheDocument()
  })
})
