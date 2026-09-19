import type { PolicyParagraph } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import './PolicyText.css'

interface PolicyTextProps {
  language: ContentLanguage
  paragraphs: PolicyParagraph[]
  /** The paragraph a selected rule cites; it is marked with the same blue bar a selected row carries. */
  highlighted?: number | null
}

/**
 * A policy as it is read: numbered paragraphs in the document's own direction (NFR-5). The number is the citation
 * unit of Document 3, so it is shown beside every paragraph and never hidden.
 */
export function PolicyText({ language, paragraphs, highlighted = null }: PolicyTextProps) {
  if (paragraphs.length === 0) {
    return <p className="policy-text__empty">This version has no paragraphs.</p>
  }
  return (
    <ol className="policy-text" {...contentAttributes(language)}>
      {paragraphs.map((paragraph) => (
        <li
          key={paragraph.index}
          id={`paragraph-${paragraph.index}`}
          className={`policy-text__paragraph${paragraph.index === highlighted ? ' policy-text__paragraph--cited' : ''}`}
          aria-current={paragraph.index === highlighted ? 'true' : undefined}
        >
          <span className="policy-text__number tabular" aria-hidden="true">
            {paragraph.index}
          </span>
          <span className="policy-text__body">{paragraph.text}</span>
        </li>
      ))}
    </ol>
  )
}
