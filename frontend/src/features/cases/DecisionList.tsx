import type { CaseResult } from '../../api/types'
import { DecisionTag } from '../../shared/ui/StatusTag'
import './DecisionList.css'

interface DecisionListProps {
  results: CaseResult[]
  selectedId: string | null
  onSelect: (decisionId: string) => void
}

/**
 * The decisions of a run, one row per case (Document 2, the batch answer). The row says what the engine decided,
 * which rule decided it and what it flagged; choosing a row opens that decision's trace beside the list.
 */
export function DecisionList({ results, selectedId, onSelect }: DecisionListProps) {
  return (
    <div className="table-scroll">
      <table className="table decisions">
        <caption className="sr-only">
          The cases of this run and what the engine decided for each
        </caption>
        <thead>
          <tr>
            <th scope="col" className="decisions__no">
              Case
            </th>
            <th scope="col">Decision</th>
            <th scope="col">Decided by</th>
            <th scope="col">Flags</th>
          </tr>
        </thead>
        <tbody>
          {results.map((result) => (
            <tr
              key={result.id}
              className={`table__row${result.id === selectedId ? ' table__row--selected' : ''}`}
              aria-current={result.id === selectedId ? 'true' : undefined}
              onClick={() => onSelect(result.id)}
            >
              <th scope="row" className="decisions__no">
                <button
                  type="button"
                  className="decisions__open"
                  onClick={() => onSelect(result.id)}
                >
                  <span className="tabular">{result.caseNo ?? '—'}</span>
                </button>
              </th>
              <td>
                {result.status === 'ERROR' ? (
                  <DecisionTag status="error" quiet />
                ) : (
                  <DecisionTag status={result.outcome ?? 'refer'} quiet />
                )}
              </td>
              <td className="mono decisions__rule">{result.decidingRuleId ?? '—'}</td>
              <td className="decisions__flags">
                {result.flags.length === 0 ? (
                  <span className="decisions__none" aria-label="no flags">
                    ·
                  </span>
                ) : (
                  result.flags.map((flag) => (
                    <span key={flag} className="decisions__flag mono">
                      {flag}
                    </span>
                  ))
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
