import type { ReactNode } from 'react'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import './Paragraph.css'

interface ParagraphProps {
  /** The paragraph's number, the citation unit of Document 3. */
  index: number
  text: string
  language: ContentLanguage
  /** The span a rule quotes from the paragraph; it is marked where it stands in the text. */
  quote?: string
  /** The paragraph the selected rule cites. */
  cited?: boolean
  /** The rules that cite the paragraph, as chips under it. */
  cites?: ReactNode
}

/**
 * One paragraph of a policy (the spec, section 09, "Policy document and its list"): its text in the serif and in its own
 * direction, its number at the end, and the span a rule quotes marked in place, so a rule and its source read together.
 */
export function Paragraph({ index, text, language, quote, cited = false, cites }: ParagraphProps) {
  const at = quote ? text.indexOf(quote) : -1
  return (
    <div className={`para${cited ? ' para--cited' : ''}`}>
      <p className="para__text doc doc--sm" {...contentAttributes(language)}>
        {quote && at >= 0 ? (
          <>
            {text.slice(0, at)}
            <mark className="para__hl">{quote}</mark>
            {text.slice(at + quote.length)}
          </>
        ) : (
          text
        )}
      </p>
      <span className="para__n">{index}</span>
      {cites ? <div className="para__cites">{cites}</div> : null}
    </div>
  )
}
