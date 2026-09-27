import { Fragment, type ReactNode } from 'react'
import './Refusal.css'

interface RefusalProps {
  /** The API's code, as its envelope carries it. */
  code: string
  /** What was not done, in one sentence: "No rule set was written." */
  title: ReactNode
  /** One row per pointer into the refused document and its problem. */
  rows: { pointer: string; problem: ReactNode }[]
  /** Why, when there is more to say than the rows: what was tried before refusing. */
  explanation?: ReactNode
  /** The next step after the closing fact, such as a link to what the model proposed. */
  next?: ReactNode
}

/**
 * What the API refused and why, and that nothing was stored (the spec, section 08): the code in mono, one row per
 * pointer and problem, and the closing fact. The same block serves a request, a version conflict, an unavailable
 * provider and the 422 of a manual edit.
 */
export function Refusal({ code, title, rows, explanation, next }: RefusalProps) {
  return (
    <div className="refusal" role="alert">
      <div className="refusal__head">
        <span className="mono">{code}</span>
        <span>{title}</span>
      </div>
      {rows.length > 0 ? (
        <dl className="refusal__rows">
          {rows.map((row) => (
            <Fragment key={row.pointer}>
              <dt>{row.pointer}</dt>
              <dd>{row.problem}</dd>
            </Fragment>
          ))}
        </dl>
      ) : null}
      <div className="refusal__foot">
        {explanation === undefined ? null : <>{explanation} </>}
        Nothing was stored.
        {next === undefined ? null : <> {next}</>}
      </div>
    </div>
  )
}
