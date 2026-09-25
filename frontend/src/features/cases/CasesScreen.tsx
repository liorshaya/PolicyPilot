import { useState } from 'react'
import { useDemoStep } from '../demo/useDemoStep'
import { ApiError } from '../../api/client'
import { publishedTarget } from '../../api/published'
import { useDecision, useRulesets, useRunFixtureSet, useStats, useVersion } from '../../api/queries'
import type { RuleSetDocument } from '../../api/types'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { SplitView } from '../../shared/layout/SplitView'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Button } from '../../shared/ui/Button'
import { Panel } from '../../shared/ui/Panel'
import { EmptyState, ErrorState, LoadingRows } from '../../shared/ui/States'
import { VersionTag } from '../../shared/ui/StatusTag'
import type { VersionStatus } from '../../shared/ui/decisionLabels'
import { Dashboard } from './Dashboard'
import { DecisionList } from './DecisionList'
import { ExplainPanel } from './ExplainPanel'
import { TraceView } from './TraceView'
import { engineTime } from './outcomes'
import './CasesScreen.css'

/** The seeded set of the demo (`policypilot.demo.fixture-set` in application.yml). */
const FIXTURE_SET = 'cases-200'

/**
 * The case runner (Work Plan day 6): the 200 seeded cases are decided by the engine, the run is summed up by
 * outcome and by the rules that decided, and every case opens its own trace. The engine decides; this screen only
 * shows what it decided and why.
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
  const decision = useDecision(selectedId)
  // step 2 of the demo is the button a presenter would press, pressed for them once the version is known
  useDemoStep(demoAsked && ruleset !== null, () => run.mutate(FIXTURE_SET), onDemoHandled)

  const document = version.data?.ruleSet as RuleSetDocument | undefined
  const language: ContentLanguage = document?.language ?? 'en'
  const aggregates = run.data?.aggregates ?? stats.data
  const results = run.data?.results ?? []
  const refusal = run.error instanceof ApiError ? run.error : null

  return (
    <>
      <WorkspaceHeader
        title="Cases"
        context={
          version.data ? (
            <>
              <bdi dir="auto">{version.data.name}</bdi> ·{' '}
              <span className="mono">{FIXTURE_SET}</span>, the seeded set of the demo
            </>
          ) : (
            'The seeded cases, decided by the engine'
          )
        }
        version={
          version.data ? (
            <>
              <span className="tabular">Version {version.data.versionNo}</span>
              <VersionTag status={version.data.status as VersionStatus} />
            </>
          ) : null
        }
        actions={
          <>
            <Button
              onClick={() => {
                onOpenRule(null)
              }}
            >
              Open the rules
            </Button>
            <Button
              variant="primary"
              loading={run.isPending}
              disabled={ruleset === null}
              onClick={() => run.mutate(FIXTURE_SET)}
            >
              Run 200 cases
            </Button>
          </>
        }
      />
      <SplitView
        sideOpen={selectedId !== null}
        main={
          <>
            {elsewhere ? (
              <p className="cases__elsewhere" role="note">
                The rule set on the workspace has no published version yet; the cases run on the
                seeded one.
              </p>
            ) : null}
            {refusal ? (
              <div className="cases__refusal" role="alert">
                The run was refused (<span className="mono">{refusal.code}</span>). Nothing was
                decided.
              </div>
            ) : null}

            <Panel
              title="This version's decisions"
              subtitle={
                run.data
                  ? `${String(run.data.results.length)} ${run.data.results.length === 1 ? 'case' : 'cases'} decided in this run, each with its own trace`
                  : 'Every case the engine has decided with this version'
              }
            >
              {stats.isPending && !run.data ? (
                <LoadingRows rows={2} label="Loading the statistics" />
              ) : null}
              {aggregates?.decisions ? <Dashboard aggregates={aggregates} /> : null}
              {aggregates?.decisions === 0 ? (
                <EmptyState
                  title="Nothing decided yet"
                  description={`Run the ${FIXTURE_SET} set to decide the 200 seeded cases with this version. The engine decides every one of them and keeps the trace it wrote.`}
                  action={
                    <Button
                      variant="primary"
                      loading={run.isPending}
                      onClick={() => run.mutate(FIXTURE_SET)}
                    >
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
            </Panel>

            {/* the list is the run's own answer, so it appears with the run and not before it */}
            {run.isPending || results.length > 0 ? (
              <Panel
                fill
                title="Cases of this run"
                subtitle="Choose a case to read the trace the engine wrote for it"
                flush
              >
                {run.isPending ? <LoadingRows rows={8} label="Deciding the cases" /> : null}
                {results.length > 0 ? (
                  <DecisionList
                    results={results}
                    selectedId={selectedId}
                    onSelect={setSelectedId}
                  />
                ) : null}
              </Panel>
            ) : null}
          </>
        }
        side={
          <Panel
            fill
            title={
              decision.data?.caseNo === undefined ? (
                'Decision'
              ) : (
                <>
                  Case <span className="tabular">{decision.data.caseNo}</span>
                </>
              )
            }
            subtitle={
              decision.data
                ? `Decided in ${engineTime(decision.data.durationMicros)} by the engine`
                : 'The trace of the chosen case'
            }
            actions={
              <button
                type="button"
                className="button button--ghost"
                onClick={() => setSelectedId(null)}
              >
                <span>Close</span>
              </button>
            }
          >
            {decision.isPending ? <LoadingRows rows={6} label="Loading the decision" /> : null}
            {decision.error ? (
              <ErrorState
                code={decision.error instanceof ApiError ? decision.error.code : undefined}
                description="The decision could not be read."
                onRetry={() => void decision.refetch()}
              />
            ) : null}
            {decision.data ? (
              <>
                <ExplainPanel
                  key={decision.data.id}
                  decisionId={decision.data.id}
                  language={language}
                  onOpenRule={onOpenRule}
                />
                <TraceView decision={decision.data} language={language} onSelectRule={onOpenRule} />
              </>
            ) : null}
          </Panel>
        }
      />
    </>
  )
}
