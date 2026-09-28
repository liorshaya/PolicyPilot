import type { ReactNode, Ref } from 'react'
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
  /** The paragraph a list of them is on, which a screen reader announces as the current one. */
  current?: boolean
  /** What stands under the paragraph: the rules that cite it, and on the sheet the findings that name it. */
  cites?: ReactNode
  /** On the Policies screen's sheet: the number in the gutter before the text, in the serif at the sheet's size. */
  sheet?: boolean
  /** The paragraph's anchor, "paragraph-4". */
  id?: string
  ref?: Ref<HTMLDivElement>
}

/**
 * One paragraph of a policy (the spec, section 09, "Policy document and its list"): its text in the serif and in its own
 * direction, its number, and the span a rule quotes marked in place, so a rule and its source read together. In the
 * margin the number stands at the end and the text is a size smaller; on the Policies screen's sheet the number is the
 * gutter and the text is the document's own size (section 10).
 */
export function Paragraph({
  index,
  text,
  language,
  quote,
  cited = false,
  current = false,
  cites,
  sheet = false,
  id,
  ref,
}: ParagraphProps) {
  const at = quote ? text.indexOf(quote) : -1
  const number = <span className="para__n">{index}</span>
  const body = (
    <p
      className={sheet ? 'para__text doc' : 'para__text doc doc--sm'}
      {...contentAttributes(language)}
    >
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
  )
  // a cited paragraph is what a rule, a chip or the palette opened: it flashes once (the deep link, section 02)
  const classes = ['para', sheet ? 'para--sheet' : '', cited ? 'para--cited flash' : '']
    .filter(Boolean)
    .join(' ')
  return (
    <div className={classes} id={id} ref={ref} aria-current={current ? 'true' : undefined}>
      {sheet ? number : body}
      {sheet ? body : number}
      {cites ? <div className="para__cites">{cites}</div> : null}
    </div>
  )
}
