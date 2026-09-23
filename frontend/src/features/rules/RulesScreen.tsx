import { useState } from 'react'
import { ApiError } from '../../api/client'
import {
  useAcknowledge,
  usePolicy,
  usePublish,
  useReplaceRules,
  useRulesets,
  useRunReview,
  useVersion,
} from '../../api/queries'
import type { RuleSetDocument, VersionResponse } from '../../api/types'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { SplitView } from '../../shared/layout/SplitView'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Button } from '../../shared/ui/Button'
import { Panel } from '../../shared/ui/Panel'
import { EmptyState, ErrorState, LoadingRows } from '../../shared/ui/States'
import type { VersionStatus } from '../../shared/ui/decisionLabels'
import { VersionTag } from '../../shared/ui/StatusTag'
import { PolicyText } from '../policy/PolicyText'
import { DecisionTable } from './DecisionTable'
import { RulesetSwitcher, VersionPicker } from './Pickers'
import { findingsByRule, publishBlockers } from './findings'
import { ReviewPanel } from './ReviewPanel'
import { RuleDrawer } from './RuleDrawer'
import { withLeaf } from './tableModel'
import type { Leaf } from './cellGrammar'
import './RulesScreen.css'

type SidePanel = 'source' | 'rule' | 'json'

interface RulesScreenProps {
  onOpenCases: () => void
  /** A rule another screen asked for, such as the step that decided a case; a click here replaces it. */
  focusRuleId?: string | null
  /** The rule set another screen asked for; without one the sandbox's first is shown. */
  rulesetId?: string | null
  /** Told when the reader switches rule sets, so the choice outlives this screen. */
  onChooseRuleset?: (rulesetId: string) => void
}

/**
 * The rule set screen (Work Plan day 6): the decision table with the cell grammar, the raw JSON view, the 422
 * pointers shown on the cells they name, and publish. A rule, its source paragraph and its findings are shown
 * together, because that pairing is what makes a published version auditable.
 */
export function RulesScreen({
  onOpenCases,
  focusRuleId = null,
  rulesetId = null,
  onChooseRuleset,
}: RulesScreenProps) {
  const rulesets = useRulesets()
  const list = rulesets.data ?? []
  // the one that was asked for; a policy screen or a generation names it, and the first is only the fallback
  const chosen = list.find((one) => one.id === rulesetId) ?? list[0]
  // the latest version unless the reader picked another; a pick belongs to its rule set (Work Plan day 14)
  const [picked, setPicked] = useState<{ rulesetId: string; versionNo: number } | null>(null)
  const latestNo = chosen?.versions[chosen.versions.length - 1]?.versionNo ?? 1
  const versionNo = picked !== null && picked.rulesetId === chosen?.id ? picked.versionNo : latestNo
  const ruleset = chosen ? { id: chosen.id, versionNo } : null
  const version = useVersion(ruleset)
  // the version on the screen is the last answer the API gave: an edit, a review, an acknowledgement or a publish
  // replaces it, and an edit of the seeded set answers with the sandbox's own copy, which is what the rest acts on
  const [answered, setAnswered] = useState<{ ruleset: string; version: VersionResponse } | null>(
    null,
  )
  // an answer belongs to the rule set it was asked about; choosing another one leaves it behind
  const latest = answered !== null && answered.ruleset === chosen?.id ? answered.version : null
  const setLatest = (version: VersionResponse) =>
    setAnswered({ ruleset: chosen?.id ?? '', version })
  const shown: VersionResponse | undefined = latest ?? version.data
  const target = latest
    ? { id: latest.rulesetId, versionNo: latest.versionNo }
    : (ruleset ?? { id: '', versionNo: 1 })
  const replaceRules = useReplaceRules(target)
  const publish = usePublish(target)
  const runReview = useRunReview(target)
  const acknowledge = useAcknowledge(target)
  const [chosenRuleId, setChosenRuleId] = useState<string | null>(null)
  const selectedRuleId = chosenRuleId ?? focusRuleId
  const [panel, setPanel] = useState<SidePanel>('source')
  const [asked, setAsked] = useState<number | null>(null)
  const document = shown?.ruleSet as RuleSetDocument | undefined
  const language: ContentLanguage = document?.language ?? 'en'
  const policy = usePolicy(chosen?.policyId ?? null)
  const selectedRule = document?.rules.find((rule) => rule.id === selectedRuleId)
  const provenance = selectedRule?.provenance
  const citedIndex = provenance?.kind === 'quoted' ? provenance.paragraph : null
  const paragraphs = policy.data?.versions?.[0]?.paragraphs ?? []
  const citedParagraph =
    citedIndex === null ? undefined : paragraphs.find((one) => one.index === citedIndex)
  // a paragraph a finding points at wins over the selected rule's until another rule is chosen
  const highlighted = asked ?? citedParagraph?.index ?? null

  const refusal = replaceRules.error instanceof ApiError ? replaceRules.error : null
  const publishRefusal = publish.error instanceof ApiError ? publish.error : null
  const reviewRefusal = runReview.error instanceof ApiError ? runReview.error : null
  const acknowledgeRefusal = acknowledge.error instanceof ApiError ? acknowledge.error : null
  const findings = shown?.findings ?? []
  const draft = shown?.status === 'DRAFT'
  const review = shown?.review
  const blockers = draft ? publishBlockers(review) : []

  function editCell(ruleId: string, previous: Leaf, next: Leaf) {
    if (!document) {
      return
    }
    replaceRules.mutate(withLeaf(document, ruleId, previous, next), { onSuccess: setLatest })
  }

  function selectRule(ruleId: string) {
    setChosenRuleId(ruleId)
    setAsked(null)
    if (panel === 'json') {
      setPanel('rule')
    }
  }

  return (
    <>
      <WorkspaceHeader
        title="Rules"
        context={
          shown ? (
            <>
              <bdi dir="auto">{shown.name}</bdi> · <span className="mono">{shown.domain}</span>
            </>
          ) : (
            'The decision table of the rule set, and where every rule comes from'
          )
        }
        version={
          shown ? (
            <>
              <span className="tabular">Version {shown.versionNo}</span>
              <VersionTag status={shown.status as VersionStatus} />
              {shown.protected ? <span className="rules__seeded">Seeded, read-only</span> : null}
            </>
          ) : null
        }
        actions={
          shown ? (
            <>
              {list.length > 1 && onChooseRuleset ? (
                <RulesetSwitcher
                  rulesets={list}
                  value={chosen?.id ?? ''}
                  onChange={onChooseRuleset}
                />
              ) : null}
              {chosen && chosen.versions.length > 1 ? (
                <VersionPicker
                  ruleset={chosen}
                  value={versionNo}
                  onChange={(next) => {
                    setPicked({ rulesetId: chosen.id, versionNo: next })
                    // the last answer was about the version on the screen, which the reader has just left
                    setAnswered(null)
                  }}
                />
              ) : null}
              <Button onClick={onOpenCases}>Run cases</Button>
              <Button
                variant="primary"
                loading={publish.isPending}
                disabled={
                  !draft ||
                  findings.some((finding) => finding.severity === 'error') ||
                  blockers.length > 0
                }
                title={
                  draft
                    ? blockers.length > 0
                      ? blockers.join(' ')
                      : undefined
                    : 'Only a draft is published'
                }
                onClick={() => publish.mutate(undefined, { onSuccess: setLatest })}
              >
                Publish version
              </Button>
            </>
          ) : null
        }
      />
      <SplitView
        sideOpen
        main={
          <>
            {refusal ? <RefusedEdit error={refusal} /> : null}
            {publishRefusal ? <RefusedEdit error={publishRefusal} /> : null}
            {reviewRefusal ? <RefusedEdit error={reviewRefusal} /> : null}
            {acknowledgeRefusal ? <RefusedEdit error={acknowledgeRefusal} /> : null}
            {draft || review ? (
              <Panel
                title="Review"
                subtitle="What the reviewer found against the policy; a person decides what stands"
                flush
              >
                <ReviewPanel
                  review={review}
                  language={language}
                  draft={draft}
                  running={runReview.isPending}
                  onRunReview={() => runReview.mutate(undefined, { onSuccess: setLatest })}
                  acknowledging={
                    acknowledge.isPending ? (acknowledge.variables.findingId ?? null) : null
                  }
                  onAcknowledge={(findingId, resolution, note) =>
                    acknowledge.mutate({ findingId, resolution, note }, { onSuccess: setLatest })
                  }
                  onSelectRule={(ruleId) => {
                    selectRule(ruleId)
                    setPanel('rule')
                  }}
                  onShowParagraph={(index) => {
                    setAsked(index)
                    setPanel('source')
                  }}
                />
              </Panel>
            ) : null}
            <Panel
              fill
              title="Decision table"
              subtitle={
                document
                  ? `${document.rules.length} rules in evaluation order · ${draft ? 'edit a cell to change a rule' : 'published versions are read-only'}`
                  : 'The rules of this version'
              }
              actions={
                <div
                  className="rules__tabs"
                  role="group"
                  aria-label="What to show beside the table"
                >
                  {(['source', 'rule', 'json'] as const).map((id) => (
                    <Button
                      key={id}
                      variant={panel === id ? 'primary' : 'secondary'}
                      aria-pressed={panel === id}
                      onClick={() => setPanel(id)}
                    >
                      {id === 'source' ? 'Policy' : id === 'rule' ? 'Rule' : 'JSON'}
                    </Button>
                  ))}
                </div>
              }
              flush
            >
              {ruleset !== null && version.isPending ? (
                <LoadingRows rows={8} label="Loading the rule set" />
              ) : null}
              {version.error ? (
                <ErrorState
                  code={version.error instanceof ApiError ? version.error.code : undefined}
                  description="The rule set could not be read."
                  onRetry={() => void version.refetch()}
                />
              ) : null}
              {document ? (
                <DecisionTable
                  document={document}
                  selectedRuleId={selectedRuleId}
                  onSelect={selectRule}
                  onEditCell={draft ? editCell : undefined}
                  problems={refusal?.details}
                  reviewFindings={findingsByRule(review)}
                />
              ) : null}
              {!document && !(ruleset !== null && version.isPending) && !version.error ? (
                <EmptyState
                  title="No rule set yet"
                  description="The seeded lending rule set is loaded with the demo data; a rule set of your own is generated from a policy on day 7."
                />
              ) : null}
            </Panel>
          </>
        }
        side={
          panel === 'json' ? (
            <Panel
              title="Rule set JSON"
              subtitle="The document the engine runs, exactly as it is stored"
              flush
            >
              <pre className="rules__json mono">{JSON.stringify(document ?? {}, null, 2)}</pre>
            </Panel>
          ) : panel === 'rule' && selectedRule ? (
            <RuleDrawer
              rule={selectedRule}
              language={language}
              paragraph={citedParagraph}
              findings={findings.filter((finding) => finding.ruleIds.includes(selectedRule.id))}
              onClose={() => setPanel('source')}
            />
          ) : (
            <Panel
              fill
              title="Policy"
              subtitle={
                asked !== null
                  ? `Paragraph ${String(asked)}, which a finding of the review names`
                  : selectedRule?.provenance.kind === 'quoted'
                    ? `Paragraph ${selectedRule.provenance.paragraph} is the source of ${selectedRule.id}`
                    : 'Choose a rule to see the paragraph it cites'
              }
              flush
            >
              {policy.data ? (
                <PolicyText
                  language={policy.data.language as ContentLanguage}
                  paragraphs={paragraphs}
                  highlighted={highlighted}
                />
              ) : (
                <LoadingRows rows={5} label="Loading the policy" />
              )}
            </Panel>
          )
        }
      />
    </>
  )
}

/** What the API refused, with the pointers it named (Document 2, 422 with the error list). */
function RefusedEdit({ error }: { error: ApiError }) {
  return (
    <div className="rules__refusal" role="alert">
      <p className="rules__refusal-title">
        The change was refused (<span className="mono">{error.code}</span>)
      </p>
      {error.details.length > 0 ? (
        <ul className="rules__refusal-list">
          {error.details.map((detail) => (
            <li key={detail.path}>
              <span className="mono">{detail.path}</span>
              <span>{detail.problem}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p>The version on the screen is unchanged.</p>
      )}
    </div>
  )
}
