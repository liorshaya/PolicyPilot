import { useState } from 'react'
import { ApiError } from '../../api/client'
import { latestPublished } from '../../api/published'
import {
  useAcknowledge,
  useDiff,
  usePolicy,
  usePublish,
  useReplaceRules,
  useRulesets,
  useRunReview,
  useStats,
  useVersion,
} from '../../api/queries'
import type { GapResolution, RuleSetDocument, VersionResponse } from '../../api/types'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { SplitView } from '../../shared/layout/SplitView'
import { BudgetNote } from '../../shared/layout/BudgetNote'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Button } from '../../shared/ui/Button'
import { Refusal } from '../../shared/ui/Refusal'
import { Section } from '../../shared/ui/Section'
import { EmptyState, ErrorState, LoadingRows } from '../../shared/ui/States'
import type { VersionStatus } from '../../shared/ui/decisionLabels'
import { VersionTag } from '../../shared/ui/StatusTag'
import { PolicyText } from '../policy/PolicyText'
import { DecisionTable } from './DecisionTable'
import { FieldsPanel } from './FieldsPanel'
import { RulesetSwitcher, VersionPicker } from './Pickers'
import { publishBlockers, publishGates } from './findings'
import { PublishBox, ReviewPanel } from './ReviewPanel'
import { RuleDrawer } from './RuleDrawer'
import { columnsOf, sinceOf, tagsOf, withEnabled, withLeaf } from './tableModel'
import type { Leaf } from './cellGrammar'
import './RulesScreen.css'

type SidePanel = 'source' | 'rule' | 'fields' | 'json'

/** The margin's four views, as the switch names them (the spec, section 05: Policy · Rule · Fields · JSON). */
const PANELS: [SidePanel, string][] = [
  ['source', 'Policy'],
  ['rule', 'Rule'],
  ['fields', 'Fields'],
  ['json', 'JSON'],
]

interface RulesScreenProps {
  onOpenCases: () => void
  /** Where a rule set is generated from a policy, which the empty screen offers. */
  onOpenPolicies: () => void
  /** A rule another screen asked for, such as the step that decided a case; a click here replaces it. */
  focusRuleId?: string | null
  /** A finding of the review the palette opened: the margin opens on the review with it current (section 08). */
  focusFindingId?: string | null
  /** A version the palette opened, shown instead of the latest until the reader picks another. */
  focusVersionNo?: number | null
  /** The rule set another screen asked for; without one the sandbox's first is shown. */
  rulesetId?: string | null
  /** Told when the reader switches rule sets, so the choice outlives this screen. */
  onChooseRuleset?: (rulesetId: string) => void
}

/**
 * The rule set screen (Work Plan day 6; the spec, section 10, the Rules screen): the decision table with the cell
 * grammar, the 422 pointers shown on the cells they name, and in the margin the review with its publish box until a
 * rule is chosen, then the chosen rule, the paragraph it cites and the findings that name it (the owner's answer of
 * 2026-09-28 to phase 3's fifth question), the policy, the fields the version declares or the JSON the engine runs; a
 * version with no review opens on its fields until a rule is chosen (the owner's answer of 2026-09-28 to phase 5's
 * second question). A rule, its source and its findings are shown together, because that pairing is what makes a
 * published version auditable.
 */
export function RulesScreen({
  onOpenCases,
  onOpenPolicies,
  focusRuleId = null,
  focusFindingId = null,
  focusVersionNo = null,
  rulesetId = null,
  onChooseRuleset,
}: RulesScreenProps) {
  const rulesets = useRulesets()
  const list = rulesets.data ?? []
  // the one that was asked for; a policy screen or a generation names it, and the first is only the fallback
  const chosen = list.find((one) => one.id === rulesetId) ?? list[0]
  // the latest version unless the reader picked another; a pick belongs to its rule set (Work Plan day 14)
  const [picked, setPicked] = useState<{ rulesetId: string; versionNo: number } | null>(() =>
    focusVersionNo !== null && rulesetId !== null ? { rulesetId, versionNo: focusVersionNo } : null,
  )
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
  // the rule a chip on this screen opened: its row is brought into view and flashes once (the deep link, section 02)
  const [chipped, setChipped] = useState<string | null>(null)
  // the margin's view as the reader chose it; until then, a draft or a reviewed version opens on its rules' review, and
  // any other on its fields until a rule is chosen, then on the paragraph the rule cites
  const [panel, setPanel] = useState<SidePanel | null>(null)
  // "All n, in the review" goes back to the review over a chosen rule, until another rule is chosen
  const [reviewing, setReviewing] = useState(false)
  const [asked, setAsked] = useState<number | null>(null)
  const document = shown?.ruleSet as RuleSetDocument | undefined
  const tags = document ? tagsOf(document) : []
  // a tag the version on the screen does not carry filters nothing: all tags are shown
  const [chosenTag, setChosenTag] = useState<string | null>(null)
  const tag = chosenTag !== null && tags.includes(chosenTag) ? chosenTag : null
  const tagged = document?.rules.filter((rule) => tag === null || (rule.tags ?? []).includes(tag))
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
  const gates = shown ? publishGates(shown, review, findings) : []
  const toAcknowledge = review?.findings.filter((finding) => finding.blocking).length ?? 0
  const view: SidePanel =
    panel ?? (draft || review ? 'rule' : selectedRule !== undefined ? 'source' : 'fields')
  const showsRule = view === 'rule' && selectedRule !== undefined && !reviewing
  const showsReview = view === 'rule' && !showsRule && (draft || review !== undefined)
  // what the rule decided in the last run on this rule set's published version, when the statistics name it
  const publishedNo = chosen ? latestPublished(chosen) : undefined
  const stats = useStats(
    chosen && publishedNo !== undefined ? { id: chosen.id, versionNo: publishedNo } : null,
  )
  const topRule = stats.data?.topDecidingRules.find((one) => one.ruleId === selectedRule?.id)
  // "Since": the version before the one on the screen, and the diff between them
  const previousNo = chosen?.versions
    .map((one) => one.versionNo)
    .filter((no) => no < (shown?.versionNo ?? 0))
    .pop()
  const diff = useDiff(
    chosen && shown && previousNo !== undefined
      ? { rulesetId: chosen.id, from: previousNo, to: shown.versionNo }
      : null,
  )
  const since =
    selectedRule && shown
      ? previousNo === undefined
        ? sinceOf(selectedRule.id, shown.versionNo, null)
        : diff.data
          ? sinceOf(selectedRule.id, shown.versionNo, { versionNo: previousNo, diff: diff.data })
          : undefined
      : undefined

  function editCell(ruleId: string, previous: Leaf, next: Leaf) {
    if (!document) {
      return
    }
    replaceRules.mutate(withLeaf(document, ruleId, previous, next), { onSuccess: setLatest })
  }

  function selectRule(ruleId: string) {
    setChosenRuleId(ruleId)
    setChipped(null)
    setAsked(null)
    setReviewing(false)
    if (view === 'json') {
      setPanel('rule')
    } else if (view === 'fields') {
      // the rule opens where its version opens a chosen rule: its paragraph, or the rule on a draft
      setPanel(null)
    }
  }

  /** A chip in the margin that opens a rule opens it in the table as a deep link. */
  function openRule(ruleId: string) {
    selectRule(ruleId)
    setChipped(ruleId)
  }

  function showParagraph(index: number) {
    setAsked(index)
    setPanel('source')
  }

  const acknowledging = acknowledge.isPending ? (acknowledge.variables.findingId ?? null) : null
  const onAcknowledge = (findingId: string, resolution?: GapResolution, note?: string) =>
    acknowledge.mutate({ findingId, resolution, note }, { onSuccess: setLatest })

  return (
    <>
      <WorkspaceHeader
        title="Rules"
        goTo
        provenance={
          shown && document
            ? [
                <span key="domain" className="mono">
                  {shown.domain}
                </span>,
                ...(shown.forkedFromId === undefined ? [] : ['your copy of the seeded rule set']),
                <span key="size">
                  <b>{document.rules.length}</b> rules · <b>{columnsOf(document).length}</b> fields
                  compared
                </span>,
              ]
            : ['The decision table of the rule set, and where every rule comes from']
        }
        version={
          shown ? (
            <>
              <VersionTag status={shown.status as VersionStatus} versionNo={shown.versionNo} />
              {shown.protected ? (
                // the glossary's words for a seeded rule set, on the seeded status's well (the spec, sections 01 and 04)
                <>
                  {' '}
                  <span className="vstatus vstatus--seeded">Seeded, read-only</span>
                </>
              ) : null}
            </>
          ) : null
        }
        secondary={shown ? <Button onClick={onOpenCases}>Run cases</Button> : null}
        reason={
          shown
            ? !draft
              ? 'Only a draft is published'
              : review?.status === 'DONE' && toAcknowledge > 0
                ? `${String(toAcknowledge)} finding${toAcknowledge === 1 ? '' : 's'} to acknowledge`
                : blockers.length > 0
                  ? blockers.join(' ')
                  : undefined
            : null
        }
        primary={
          shown ? (
            <Button
              variant="primary"
              busy={publish.isPending}
              disabled={gates.some((gate) => gate.state !== 'ok')}
              onClick={() => publish.mutate(undefined, { onSuccess: setLatest })}
            >
              Publish version {shown.versionNo}
            </Button>
          ) : null
        }
      />
      <BudgetNote />
      <SplitView
        sideOpen
        fill
        sideSheet={showsReview}
        main={
          <>
            {refusal || publishRefusal || reviewRefusal || acknowledgeRefusal ? (
              <div className="sheet__notes">
                {refusal ? <Refused error={refusal} act="edit" /> : null}
                {publishRefusal ? <Refused error={publishRefusal} act="publish" /> : null}
                {reviewRefusal ? <Refused error={reviewRefusal} act="review" /> : null}
                {acknowledgeRefusal ? (
                  <Refused error={acknowledgeRefusal} act="acknowledge" />
                ) : null}
              </div>
            ) : null}
            <Section
              title="Decision table"
              subtitle={
                document && tagged
                  ? tag === null
                    ? `${String(document.rules.length)} rules`
                    : `${String(tagged.length)} of ${String(document.rules.length)} rules`
                  : 'The rules of this version'
              }
              actions={
                <>
                  {shown && list.length > 1 && onChooseRuleset ? (
                    <RulesetSwitcher
                      bare
                      className="rules__ruleset"
                      rulesets={list}
                      value={chosen?.id ?? ''}
                      onChange={onChooseRuleset}
                    />
                  ) : null}
                  {shown && chosen && chosen.versions.length > 1 ? (
                    <VersionPicker
                      bare
                      className="rules__version"
                      ruleset={chosen}
                      value={versionNo}
                      onChange={(next) => {
                        setPicked({ rulesetId: chosen.id, versionNo: next })
                        // the last answer was about the version on the screen, which the reader has just left
                        setAnswered(null)
                      }}
                    />
                  ) : null}
                  {tags.length > 0 ? (
                    <select
                      className="select select--sm rules__tag"
                      aria-label="Tag"
                      value={tag ?? ''}
                      onChange={(event) => setChosenTag(event.target.value || null)}
                    >
                      <option value="">All tags</option>
                      {tags.map((one) => (
                        <option key={one} value={one}>
                          {one}
                        </option>
                      ))}
                    </select>
                  ) : null}
                  <div className="segment" role="group" aria-label="Show in the margin">
                    {PANELS.map(([id, label]) => (
                      <Button
                        key={id}
                        variant="secondary"
                        size="sm"
                        aria-pressed={view === id}
                        onClick={() => {
                          setPanel(id)
                          setReviewing(false)
                        }}
                      >
                        {label}
                      </Button>
                    ))}
                  </div>
                </>
              }
              flush
            >
              {ruleset !== null && version.isPending ? (
                <LoadingRows label="Loading the rule set" />
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
                  openedRuleId={chipped ?? (chosenRuleId === null ? focusRuleId : null)}
                  onSelect={selectRule}
                  onEditCell={draft ? editCell : undefined}
                  problems={refusal?.details}
                  review={review}
                  findings={findings}
                  tag={tag}
                />
              ) : null}
              {!document && !(ruleset !== null && version.isPending) && !version.error ? (
                <EmptyState
                  title="No rule set yet"
                  action={
                    <Button size="sm" onClick={onOpenPolicies}>
                      Generate rules from the policy
                    </Button>
                  }
                />
              ) : null}
            </Section>
          </>
        }
        side={
          view === 'json' ? (
            <section className="margin__section" aria-label="Rule set JSON">
              <div className="margin__title">
                <span>Rule set JSON</span>
                <span className="quiet">the document the engine runs, as it is stored</span>
              </div>
              <pre className="rules__json mono">{JSON.stringify(document ?? {}, null, 2)}</pre>
            </section>
          ) : view === 'fields' && document ? (
            <FieldsPanel document={document} findings={findings} onShowParagraph={showParagraph} />
          ) : showsReview && shown ? (
            <>
              <div className="sheet">
                <div className="sheet__scroll">
                  <ReviewPanel
                    review={review}
                    language={language}
                    draft={draft}
                    running={runReview.isPending}
                    onRunReview={() => runReview.mutate(undefined, { onSuccess: setLatest })}
                    acknowledging={acknowledging}
                    onAcknowledge={onAcknowledge}
                    onSelectRule={(ruleId) => {
                      openRule(ruleId)
                      setPanel('rule')
                    }}
                    onShowParagraph={showParagraph}
                    current={focusFindingId}
                  />
                </div>
              </div>
              <PublishBox
                version={shown}
                review={review}
                findings={findings}
                publishing={publish.isPending}
                onPublish={() => publish.mutate(undefined, { onSuccess: setLatest })}
                running={runReview.isPending}
                onRunReview={() => runReview.mutate(undefined, { onSuccess: setLatest })}
                justPublished={publish.isSuccess}
              />
            </>
          ) : showsRule && document && shown ? (
            <RuleDrawer
              rule={selectedRule}
              language={language}
              versionStatus={shown.status as VersionStatus}
              paragraph={citedParagraph}
              citedBy={document.rules
                .filter(
                  (rule) =>
                    rule.provenance.kind === 'quoted' && rule.provenance.paragraph === citedIndex,
                )
                .map((rule) => rule.id)}
              findings={findings.filter((finding) => finding.ruleIds.includes(selectedRule.id))}
              reviewFindings={(review?.findings ?? []).filter((finding) =>
                finding.ruleIds.includes(selectedRule.id),
              )}
              reviewTotal={review?.findings.length ?? 0}
              editable={draft}
              acknowledging={acknowledging}
              onAcknowledge={onAcknowledge}
              onSelectRule={openRule}
              onShowParagraph={showParagraph}
              decided={
                topRule && stats.data
                  ? { count: topRule.count, decisions: stats.data.decisions }
                  : undefined
              }
              since={since}
              onOpenReview={
                review
                  ? () => {
                      setReviewing(true)
                      setPanel('rule')
                    }
                  : undefined
              }
              onToggleEnabled={
                draft
                  ? (enabled) =>
                      replaceRules.mutate(withEnabled(document, selectedRule.id, enabled), {
                        onSuccess: setLatest,
                      })
                  : undefined
              }
            />
          ) : (
            <section className="margin__section" aria-label="Policy">
              <div className="margin__title">
                <span>Policy</span>
                <span className="quiet">
                  {asked !== null
                    ? `Paragraph ${String(asked)}, which a finding of the review names`
                    : selectedRule?.provenance.kind === 'quoted'
                      ? `Paragraph ${selectedRule.provenance.paragraph} is the source of ${selectedRule.id}`
                      : 'Choose a rule to see the paragraph it cites'}
                </span>
              </div>
              {policy.data ? (
                <PolicyText
                  language={policy.data.language as ContentLanguage}
                  paragraphs={paragraphs}
                  highlighted={highlighted}
                />
              ) : (
                <LoadingRows label="Loading the policy" />
              )}
            </section>
          )
        }
      />
    </>
  )
}

/** What each act on a version was, when the API refused it, in the refusal's title and its 409's sentence. */
const REFUSED: Record<
  'edit' | 'publish' | 'review' | 'acknowledge',
  { title: string; draft: string }
> = {
  edit: { title: 'The edit was refused.', draft: 'Only a draft is edited.' },
  publish: { title: 'The version was not published.', draft: 'Only a draft is published.' },
  review: { title: 'The review did not run.', draft: 'Only a draft is reviewed.' },
  acknowledge: {
    title: 'The finding was not acknowledged.',
    draft: "Only a draft's findings are acknowledged.",
  },
}

/**
 * What the API refused, as section 08's refusal block (Document 2: 422 with the pointers it named, 409 on a version
 * that is not a draft): the code, a row per pointer and problem, and the closing fact.
 */
function Refused({ error, act }: { error: ApiError; act: keyof typeof REFUSED }) {
  return (
    <Refusal
      code={error.code}
      title={REFUSED[act].title}
      rows={error.details.map((detail) => ({ pointer: detail.path, problem: detail.problem }))}
      explanation={error.code === 'VERSION_STATUS_CONFLICT' ? REFUSED[act].draft : undefined}
    />
  )
}
