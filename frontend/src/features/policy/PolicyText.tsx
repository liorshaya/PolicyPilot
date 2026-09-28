import { useEffect, useRef, type ReactNode } from 'react'
import type { PolicyParagraph } from '../../api/types'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { EmptyState } from '../../shared/ui/States'
import { Paragraph } from './Paragraph'

interface PolicyTextProps {
  language: ContentLanguage
  paragraphs: PolicyParagraph[]
  /** The paragraph a selected rule cites; it is marked, and brought into view. */
  highlighted?: number | null
  /** On the Policies screen's sheet: each paragraph numbered in the gutter, in the serif at the sheet's size. */
  sheet?: boolean
  /** What stands under a paragraph: the rules that cite it, and the findings that name it. */
  cites?: (index: number) => ReactNode
}

/**
 * A policy as it is read (the spec, sections 09 and 10): its paragraphs in the document's own direction (NFR-5), each
 * with its number, the citation unit of Document 3, so it is shown beside every paragraph and never hidden.
 */
export function PolicyText({
  language,
  paragraphs,
  highlighted = null,
  sheet = false,
  cites,
}: PolicyTextProps) {
  const citedRef = useRef<HTMLDivElement>(null)
  // a rule and its source are read together, so the cited paragraph comes into view on its own
  useEffect(() => {
    citedRef.current?.scrollIntoView({ block: 'nearest' })
  }, [highlighted])

  if (paragraphs.length === 0) {
    return <EmptyState title="This version has no paragraphs." />
  }
  return (
    <div>
      {paragraphs.map((paragraph) => (
        <Paragraph
          key={paragraph.index}
          id={`paragraph-${String(paragraph.index)}`}
          ref={paragraph.index === highlighted ? citedRef : undefined}
          index={paragraph.index}
          text={paragraph.text}
          language={language}
          cited={paragraph.index === highlighted}
          current={paragraph.index === highlighted}
          sheet={sheet}
          cites={cites?.(paragraph.index)}
        />
      ))}
    </div>
  )
}
