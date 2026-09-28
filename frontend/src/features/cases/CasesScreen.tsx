import { useState } from 'react'
import { useDemoStep } from '../demo/useDemoStep'
import { ApiError } from '../../api/client'
import { publishedTarget } from '../../api/published'
import { useDecision, useRulesets, useRunFixtureSet, useStats, useVersion } from '../../api/queries'
import type { Decision, RuleSetDocument } from '../../api/types'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { SplitView } from '../../shared/layout/SplitView'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Actor } from '../../shared/ui/Actor'
import { Button } from '../../shared/ui/Button'
import { Note } from '../../shared/ui/Note'
import { Refusal } from '../../shared/ui/Refusal'
import { Section } from '../../shared/ui/Section'
import { EmptyState, ErrorState, LoadingRows } from '../../shared/ui/States'
import { VersionTag } from '../../shared/ui/StatusTag'
import type { VersionStatus } from '../../shared/ui/decisionLabels'
import { CaseForm } from './CaseForm'
import { Dashboard } from './Dashboard'
import { DecisionList } from './DecisionList'
import { TraceView } from './TraceView'
import './CasesScreen.css'

/** The seeded set of the demo (`policypilot.demo.fixture-set` in application.yml). */
const FIXTURE_SET = 'cases-200'

/** What names the margin while a decision is open: its case, or the decision alone for a case typed by an officer. */
function traceLabel(decision: Decision): string {
  return decision.caseNo === undefined ? 'Decision' : `Case ${String(decision.caseNo)}`
}

/**
 * The case runner (Work Plan day 6; the spec, section 10, the Cases screen): the 200 seeded cases are decided by the
 * engine, the run is summed up by outcome and by the rules that decided over its list, and every case opens its own
 * trace in the wide margin; Decide a case opens the officer's form there, built from the version's fields (section
 * 09). The engine decides; this screen only shows what it decided and why.
 */
export function CasesScreen({
  onOpenRule,
  rulesetId = null,
  demoAsked = false,
  onDemoHandled,
}: {
  onOpenRule: (ruleId: string | null) => void
  /** The rule set the workspace is on; without one the sandbox's first is used. */
  rulesetId?: string | null
  /** Step 2 of the guided demo: run the 200 seeded cases (Brief FR-23). */
  demoAsked?: boolean
  onDemoHandled?: () => void
}) {
  const rulesets = useRulesets()
  const list = rulesets.data ?? []
  // Document 2, decide: only a published version decides. The workspace may be on a draft written a moment ago, so
  // the cases run on its latest published version, or on the first rule set that has one, and the screen says so
  const target = publishedTarget(list, rulesetId)
  const ruleset = target ? { id: target.ruleset.id, versionNo: target.versionNo } : null
  const elsewhere = target?.elsewhere ?? false
  const version = useVersion(ruleset)
  const stats = useStats(ruleset)
  const run = useRunFixtureSet(ruleset ?? { id: '', versionNo: 1 })
  const [selectedId, setSelectedId] = useState<string | null>(null)
  // the margin holds the officer's form until a case is decided or another one is opened
  const [deciding, setDeciding] = useState(false)
  // the rule the figures filter the list by, which the list's own select changes too
  const [decidingRule, setDecidingRule] = useState<string | null>(null)
  const decision = useDecision(selectedId)
  // step 2 of the demo is the button a presenter would press, pressed for them once the version is known
  useDemoStep(demoAsked && ruleset !== null, () => run.mutate(FIXTURE_SET), onDemoHandled)

  const document = version.data?.ruleSet as RuleSetDocument | undefined
  const language: ContentLanguage = document?.language ?? 'en'
  const aggregates = run.data?.aggregates ?? stats.data
  const results = run.data?.results ?? []
  const refusal = run.error instanceof ApiError ? run.error : null
  const open = (decisionId: string) => {
    setDeciding(false)
    setSelectedId(decisionId)
  }

  return (
    <>
      <WorkspaceHeader
        title="Cases"
        provenance={
          version.data
            ? [
                <span key="domain" className="mono">
                  {version.data.domain}
                </span>,
                <span key="set">
                  <span className="mono">{FIXTURE_SET}</span>, the seeded set
                </span>,
                <Actor key="engine" kind="engine">
                  engine
                </Actor>,
              ]
            : ['The seeded cases, decided by the engine']
        }
        version={
          version.data ? (
            <VersionTag
              status={version.data.status as VersionStatus}
              versionNo={version.data.versionNo}
            />
          ) : null
        }
        secondary={
          <Button
            disabled={ruleset === null}
            onClick={() => {
              setDeciding(true)
              setSelectedId(null)
            }}
          >
            Decide a case
          </Button>
        }
        primary={
          <Button
            variant="primary"
            busy={run.isPending}
            disabled={ruleset === null}
            onClick={() => run.mutate(FIXTURE_SET)}
          >
            Run 200 cases
          </Button>
        }
      />
      <SplitView
        wide
        fill
        sideSheet={!deciding}
        sideOpen={deciding || selectedId !== null}
        sideLabel={
          deciding ? 'Decide a case' : decision.data ? traceLabel(decision.data) : undefined
        }
        main={
          <>
            {elsewhere || refusal ? (
              <div className="cases__notes">
                {elsewhere ? (
                  <Note>
                    This rule set has no published version yet; the cases ran on the seeded one.
                  </Note>
                ) : null}
                {refusal ? (
                  <Refusal
                    code={refusal.code}
                    title="The run was refused."
                    rows={[]}
                    explanation={
                      refusal.code === 'RATE_LIMITED'
                        ? 'Too many runs in a short time. Wait a minute, then try again.'
                        : undefined
                    }
                  />
                ) : null}
              </div>
            ) : null}

            <Section
              title={ruleset ? `Decisions on v${String(ruleset.versionNo)}` : 'Decisions'}
              subtitle={
                run.data
                  ? `${String(results.length)} ${results.length === 1 ? 'case' : 'cases'} · one run`
                  : 'Every case the engine has decided with this version'
              }
              flush
            >
              {stats.isPending && !run.data ? (
                <>
                  <Dashboard
                    aggregates={undefined}
                    rules={[]}
                    filter={null}
                    onFilter={setDecidingRule}
                  />
                  <LoadingRows label="Loading the statistics" />
                </>
              ) : null}
              {aggregates?.decisions ? (
                <Dashboard
                  aggregates={aggregates}
                  rules={document?.rules ?? []}
                  filter={decidingRule}
                  onFilter={setDecidingRule}
                />
              ) : null}
              {aggregates?.decisions === 0 ? (
                <EmptyState
                  title="Nothing decided yet."
                  description={`the seeded set, on version ${String(ruleset?.versionNo ?? 1)}`}
                  action={
                    <Button size="sm" busy={run.isPending} onClick={() => run.mutate(FIXTURE_SET)}>
                      Run 200 cases
                    </Button>
                  }
                />
              ) : null}
              {stats.error ? (
                <ErrorState
                  code={stats.error instanceof ApiError ? stats.error.code : undefined}
                  description="The statistics could not be read."
                  onRetry={() => void stats.refetch()}
                />
              ) : null}
              {/* the list is the run's own answer, so it appears with the run and not before it */}
              {run.isPending ? <LoadingRows label="Deciding the cases" /> : null}
              {results.length > 0 ? (
                <DecisionList
                  results={results}
                  selectedId={selectedId}
                  onSelect={open}
                  versionNo={ruleset?.versionNo}
                  decidingRule={decidingRule}
                  onDecidingRuleChange={setDecidingRule}
                />
              ) : null}
            </Section>
          </>
        }
        side={
          deciding && ruleset ? (
            document ? (
              <CaseForm
                fields={document.fields}
                language={language}
                version={ruleset}
                onDecided={(decided) => open(decided.id)}
                onClose={() => setDeciding(false)}
              />
            ) : (
              <LoadingRows label="Loading the fields" />
            )
          ) : (
            <div className="sheet">
              <div className="sheet__scroll">
                {decision.data ? (
                  <TraceView
                    key={decision.data.id}
                    decision={decision.data}
                    language={language}
                    fields={document?.fields ?? []}
                    onClose={() => setSelectedId(null)}
                    onSelectRule={onOpenRule}
                  />
                ) : (
                  <>
                    <div className="sec">
                      <h2 className="sec__title">Decision</h2>
                      <div className="sec__side">
                        <Button variant="quiet" size="sm" onClick={() => setSelectedId(null)}>
                          Close
                        </Button>
                      </div>
                    </div>
                    {decision.isPending ? <LoadingRows label="Loading the decision" /> : null}
                    {decision.error ? (
                      <ErrorState
                        code={decision.error instanceof ApiError ? decision.error.code : undefined}
                        description="The decision could not be read."
                        onRetry={() => void decision.refetch()}
                      />
                    ) : null}
                  </>
                )}
              </div>
            </div>
          )
        }
      />
    </>
  )
}
