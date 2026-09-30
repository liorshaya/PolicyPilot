import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import type { CaseResult, Rule } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { Button } from '../../shared/ui/Button'
import { Chip } from '../../shared/ui/Chip'
import { DECISION_LABELS, type DecisionStatus } from '../../shared/ui/decisionLabels'
import { Kbd } from '../../shared/ui/Kbd'
import { DecisionTag } from '../../shared/ui/StatusTag'
import '../../shared/ui/Table.css'
import './DecisionList.css'

interface DecisionListProps {
  results: CaseResult[]
  /** The rules of the version the run decided on, for the label under each deciding rule's id. */
  rules?: Rule[]
  /** The language of the policy, which the rules' labels are written in. */
  language?: ContentLanguage
  selectedId: string | null
  /** The case the palette opened: its row flashes once (the deep link, section 02). */
  openedId?: string | null
  onSelect: (decisionId: string) => void
  /** The version the run decided on, which the footer names as the list's scope. */
  versionNo?: number
  /** The deciding rule the list is filtered by, when the figures above it choose it; without it, the list keeps its own. */
  decidingRule?: string | null
  onDecidingRuleChange?: (ruleId: string | null) => void
}

type Density = 'comfortable' | 'compact'

/** The row height the reader chose, remembered in this browser (the spec, section 07: "a labelled, remembered choice"). */
const DENSITY_KEY = 'pp-row-height'

function storedDensity(): Density {
  try {
    return localStorage.getItem(DENSITY_KEY) === 'compact' ? 'compact' : 'comfortable'
  } catch {
    // a browser that refuses its storage keeps the comfortable rows
    return 'comfortable'
  }
}

function chooseDensity(density: Density): void {
  try {
    localStorage.setItem(DENSITY_KEY, density)
  } catch {
    // the choice then lasts as long as the page
  }
}

/** A case's decision as its tag and the outcome filter name it: an evaluation error is its own. */
const statusOf = (result: CaseResult): DecisionStatus =>
  result.status === 'ERROR' ? 'error' : (result.outcome ?? 'refer')

const OUTCOMES: DecisionStatus[] = ['approve', 'reject', 'refer', 'error']

/**
 * The decisions of a run, one row per case (Document 2, the batch answer; the Register spec, section 07, the base
 * table): the case number, what the engine decided, which rule decided it, named under its id, and what it flagged.
 * Filters narrow the list by case number, outcome and deciding rule, and Clear filters lifts them all at once; the
 * footer states the count, the scope and the keys. Choosing a row opens that decision's trace beside the list.
 */
export function DecisionList({
  results,
  rules = [],
  language = 'he',
  selectedId,
  openedId = null,
  onSelect,
  versionNo,
  decidingRule: chosenRule,
  onDecidingRuleChange,
}: DecisionListProps) {
  const [caseNo, setCaseNo] = useState('')
  const [outcome, setOutcome] = useState<DecisionStatus | ''>('')
  const [ownRule, setOwnRule] = useState('')
  const decidingRule = chosenRule === undefined ? ownRule : (chosenRule ?? '')
  const setDecidingRule = (ruleId: string) => {
    if (onDecidingRuleChange) {
      onDecidingRuleChange(ruleId === '' ? null : ruleId)
    } else {
      setOwnRule(ruleId)
    }
  }
  const [density, setDensity] = useState<Density>(storedDensity)
  const filterRef = useRef<HTMLInputElement>(null)
  const tableRef = useRef<HTMLTableElement>(null)
  const selectedRef = useRef<HTMLTableRowElement>(null)

  const labels = new Map(rules.map((rule) => [rule.id, rule.label]))
  const deciding = [...new Set(results.map((result) => result.decidingRuleId))]
    .filter((ruleId) => ruleId !== undefined)
    .sort()
  const filtering = caseNo !== '' || outcome !== '' || decidingRule !== ''
  const clearFilters = () => {
    setCaseNo('')
    setOutcome('')
    setDecidingRule('')
  }
  const shown = results.filter(
    (result) =>
      (caseNo === '' || String(result.caseNo ?? '').startsWith(caseNo)) &&
      (outcome === '' || statusOf(result) === outcome) &&
      (decidingRule === '' || result.decidingRuleId === decidingRule),
  )

  // the selected row is kept in view when the margin opens (the spec, section 08), from the palette too
  useEffect(() => {
    selectedRef.current?.scrollIntoView({ block: 'nearest' })
  }, [selectedId])

  // "/" goes to the case filter from anywhere the reader is not typing
  useEffect(() => {
    const onKey = (event: globalThis.KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      const typing =
        target !== null &&
        (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
      if (event.key === '/' && !typing && !event.metaKey && !event.ctrlKey && !event.altKey) {
        event.preventDefault()
        filterRef.current?.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  /** The arrows move from one case's button to the next one's; Enter on a button opens its case. */
  function onRowKey(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const step = event.key === 'ArrowDown' ? 1 : event.key === 'ArrowUp' ? -1 : 0
    if (step === 0) {
      return
    }
    event.preventDefault()
    const buttons = tableRef.current?.querySelectorAll<HTMLButtonElement>('tbody .decisions__open')
    buttons?.[index + step]?.focus()
  }

  const scope = versionNo === undefined ? 'one run' : `one run on v${String(versionNo)}`
  const count =
    shown.length === 0
      ? `Cases 0 of ${String(results.length)}`
      : `Cases 1–${String(shown.length)} of ${String(results.length)}`

  return (
    <>
      <div className="toolbar decisions__toolbar">
        <input
          ref={filterRef}
          className="input decisions__case"
          placeholder="Case number"
          aria-label="Jump to a case"
          inputMode="numeric"
          value={caseNo}
          onChange={(event) => setCaseNo(event.target.value.trim())}
          onKeyDown={(event) => {
            const first = shown[0]
            if (event.key === 'Enter' && caseNo !== '' && first !== undefined) {
              onSelect(first.id)
            }
          }}
        />
        <select
          className="select"
          aria-label="Outcome"
          value={outcome}
          onChange={(event) => setOutcome(event.target.value as DecisionStatus | '')}
        >
          <option value="">All outcomes</option>
          {OUTCOMES.map((status) => (
            <option key={status} value={status}>
              {DECISION_LABELS[status]}
            </option>
          ))}
        </select>
        <select
          className="select"
          aria-label="Deciding rule"
          value={decidingRule}
          onChange={(event) => setDecidingRule(event.target.value)}
        >
          <option value="">Any deciding rule</option>
          {deciding.map((ruleId) => (
            <option key={ruleId} value={ruleId}>
              {ruleId}
            </option>
          ))}
        </select>
        {filtering ? (
          <Button variant="quiet" size="sm" onClick={clearFilters}>
            Clear filters
          </Button>
        ) : null}
        <span className="toolbar__spacer" />
        <div className="segment" role="group" aria-label="Row height">
          {(['comfortable', 'compact'] as const).map((choice) => (
            <Button
              key={choice}
              variant="secondary"
              size="sm"
              aria-pressed={density === choice}
              onClick={() => {
                chooseDensity(choice)
                setDensity(choice)
              }}
            >
              {choice === 'comfortable' ? 'Comfortable' : 'Compact'}
            </Button>
          ))}
        </div>
      </div>
      <div className="table-scroll">
        <table
          ref={tableRef}
          className={`table table--last-borderless${density === 'compact' ? ' table--compact' : ''}`}
        >
          <caption className="sr-only">
            The cases of this run and what the engine decided for each
          </caption>
          <thead>
            <tr>
              <th scope="col" className="t-num">
                Case
              </th>
              <th scope="col">Decision</th>
              <th scope="col">Decided by</th>
              <th scope="col">Flags</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((result, index) => (
              <tr
                key={result.id}
                ref={result.id === selectedId ? selectedRef : undefined}
                className={
                  [
                    result.id === selectedId ? 't-selected' : '',
                    result.id === openedId ? 'flash' : '',
                  ]
                    .filter(Boolean)
                    .join(' ') || undefined
                }
                aria-current={result.id === selectedId ? 'true' : undefined}
                onClick={() => onSelect(result.id)}
              >
                <td role="rowheader" className="t-num">
                  <button
                    type="button"
                    className="decisions__open"
                    onClick={(event) => {
                      // the row answers the click too; the button is the case's way in for a keyboard
                      event.stopPropagation()
                      onSelect(result.id)
                    }}
                    onKeyDown={(event) => onRowKey(event, index)}
                  >
                    {result.caseNo ?? '—'}
                  </button>
                </td>
                <td>
                  <DecisionTag status={statusOf(result)} quiet />
                </td>
                {result.decidingRuleId === undefined ? (
                  <td className="t-empty" aria-label="no deciding rule" />
                ) : (
                  <td>
                    <div className="t-decided">
                      <span className="t-id">{result.decidingRuleId}</span>
                      {labels.has(result.decidingRuleId) ? (
                        <span
                          className="t-decided__label"
                          title={labels.get(result.decidingRuleId)}
                          {...contentAttributes(language)}
                        >
                          {labels.get(result.decidingRuleId)}
                        </span>
                      ) : null}
                    </div>
                  </td>
                )}
                {result.flags.length === 0 ? (
                  <td className="t-empty" aria-label="no flags" />
                ) : (
                  <td>
                    <span className="decisions__flags">
                      {result.flags.map((flag) => (
                        <Chip key={flag} kind="field">
                          {flag}
                        </Chip>
                      ))}
                    </span>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="sheet__foot">
        <span>{`${count} · ${scope}`}</span>
        <span className="muted">
          <Kbd>↑</Kbd> <Kbd>↓</Kbd> select · <Kbd>↵</Kbd> open · <Kbd>/</Kbd> filter
        </span>
      </div>
    </>
  )
}
