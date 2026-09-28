import { Fragment, useId, useState, type FormEvent } from 'react'
import { publishedTarget } from '../../api/published'
import { useRulesets, useStats, useVersion } from '../../api/queries'
import type { ChangeDecision, RuleSetDocument } from '../../api/types'
import { contentAttributes, isolated, type ContentLanguage } from '../../shared/i18n/direction'
import { dateTimeOf, durationText } from '../../shared/i18n/time'
import { SplitView } from '../../shared/layout/SplitView'
import { BudgetNote } from '../../shared/layout/BudgetNote'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Actor, PERSON } from '../../shared/ui/Actor'
import { Button } from '../../shared/ui/Button'
import { Chip } from '../../shared/ui/Chip'
import { Field } from '../../shared/ui/Field'
import { Note } from '../../shared/ui/Note'
import { Refusal } from '../../shared/ui/Refusal'
import { findingRows } from '../../shared/ui/refusalRows'
import { Seal } from '../../shared/ui/Seal'
import { Section } from '../../shared/ui/Section'
import '../../shared/ui/States.css'
import { DecisionTag, VersionTag } from '../../shared/ui/StatusTag'
import { DECISION_LABELS, type VersionStatus } from '../../shared/ui/decisionLabels'
import { SCRIPTED_CHANGE_REQUEST } from '../demo/steps'
import { useDemoStep } from '../demo/useDemoStep'
import { BothTraces } from './BothTraces'
import { DiffView } from './DiffView'
import { RegressionReport } from './RegressionReport'
import { decisionFailureText, proposeFailureText } from './failures'
import type { ChangeState } from './changeReducer'
import { changeRequestName } from './names'
import { useChange } from './useChange'
import {
  CHANGE_STAGES,
  type ChangeStage,
  type Flip,
  type Patch,
  type Proposal,
  type StageEnded,
  type StreamFailure,
} from './types'
import './ChangeScreen.css'

/**
 * The chat message's limit, which a change request and a note share (Document 5, Input limits: 2 KB); 1,000 characters
 * keep a Hebrew text under it, as the chat composer does.
 */
const TEXT_LIMIT = 1000

const COUNT = new Intl.NumberFormat('en-US')

const STAGE_LABELS: Record<ChangeStage, string> = {
  analyzing: 'Finding the rules the request touches',
  proposing: 'Writing the patches',
  validating: 'Checking the patched rules against the policy',
  regression: "Deciding this sandbox's cases again",
}

/** A proposal's operations, in the spec's words (section 09). */
const OP_LABELS: Record<Patch['op'], string> = {
  add: 'Add',
  replace: 'Replace',
  remove: 'Remove',
  add_field: 'Add the field',
  set_defaults: 'Set the defaults',
}

/** The version a proposal was made on, kept as it was when it was proposed: an approval moves the workspace on. */
interface ProposedOn {
  rulesetId: string
  versionNo: number
  protected: boolean
}

interface ChangeScreenProps {
  /** The rule set the workspace is on; the change is proposed on its latest published version. */
  rulesetId?: string | null
  /** Told which version an approval published, so the workspace moves to it. */
  onPublished?: (published: { rulesetId: string; versionNo: number; versionId: string }) => void
  /** Opens the rule set, where the published version shows its rules. */
  onOpenRules?: () => void
  /** Opens the audit log, where the published version holds the approval's entry. */
  onOpenAudit?: () => void
  /** Step 4 of the guided demo: the scripted request filled in (Brief FR-23). */
  demoAsked?: boolean
  onDemoHandled?: () => void
}

/**
 * The change screen (Brief, demo step 4; the spec, section 09, "The change request"): a change is proposed by the model
 * (dashed), measured by the engine (solid), approved by a person (sealed), and the sheet walks that order: the request,
 * the four stages with the rules considered, the proposal as patches with the model's rationale, the diff, the
 * regression led by the count that matters, and the decision with its note for the audit log. Nothing is published
 * before a person approves; a proposal the validator refused shows its refusals and what the model attempted
 * (Document 5, RT-04). A flipped case opens its two traces in the margin.
 */
export function ChangeScreen({
  rulesetId = null,
  onPublished,
  onOpenRules,
  onOpenAudit,
  demoAsked = false,
  onDemoHandled,
}: ChangeScreenProps) {
  const rulesets = useRulesets()
  // Document 2: a change is proposed on a PUBLISHED version; the workspace may be on a draft written a moment ago
  const target = publishedTarget(rulesets.data ?? [], rulesetId)
  const base = target ? { id: target.ruleset.id, versionNo: target.versionNo } : null
  const version = useVersion(base)
  // the regression decides again every case the sandbox decided on the base version, which its statistics count
  const stats = useStats(base)
  const change = useChange(base ? { rulesetId: base.id, versionNo: base.versionNo } : null)
  const [text, setText] = useState('')
  const [note, setNote] = useState('')
  const [proposedOn, setProposedOn] = useState<ProposedOn | null>(null)
  const [verdict, setVerdict] = useState<'approve' | 'reject'>('approve')
  const [traced, setTraced] = useState<Flip | null>(null)
  const document = version.data?.ruleSet as RuleSetDocument | undefined
  const language: ContentLanguage = document?.language ?? 'en'
  const state = change.state
  const running = state.status === 'running'
  const proposal = state.status === 'proposed' || state.status === 'decided' ? state.proposal : null
  // step 4 of the demo types the request a presenter would; proposing it is still the presenter's click
  useDemoStep(demoAsked, () => setText(SCRIPTED_CHANGE_REQUEST), onDemoHandled)

  function submit(event: FormEvent) {
    event.preventDefault()
    if (base === null || version.data === undefined || running || text.trim() === '') {
      return
    }
    setProposedOn({
      rulesetId: base.id,
      versionNo: base.versionNo,
      protected: version.data.protected,
    })
    setTraced(null)
    change.propose(text)
  }

  function decide(chosen: 'approve' | 'reject') {
    setVerdict(chosen)
    change.decide(chosen, note, (decision) => {
      if (decision.result !== undefined) {
        onPublished?.(decision.result)
      }
    })
  }

  return (
    <>
      <WorkspaceHeader
        title="Change"
        provenance={
          version.data
            ? [
                <span key="domain" className="mono">
                  {version.data.domain}
                </span>,
              ]
            : ['A change request, measured on the cases before a person approves it']
        }
        version={
          version.data ? (
            <>
              <span className="tabular">Version {version.data.versionNo}</span>
              <VersionTag status={version.data.status as VersionStatus} />
            </>
          ) : null
        }
      />
      <BudgetNote />
      <SplitView
        wide
        sideSheet
        sideOpen={traced !== null && proposal !== null}
        sideLabel="Both traces"
        side={
          traced && proposal ? (
            <BothTraces
              key={traced.decisionId}
              flip={traced}
              changeId={proposal.id}
              language={language}
              fields={document?.fields ?? []}
              onClose={() => setTraced(null)}
            />
          ) : null
        }
        main={
          <>
            <Section title="Change request">
              <div className="change">
                {target?.elsewhere ? (
                  <Note>
                    The rule set on the workspace has no published version yet; the change is
                    proposed on the seeded one.
                  </Note>
                ) : null}
                <form className="change__form" onSubmit={submit}>
                  <Field
                    label="What should change"
                    htmlFor="change-request"
                    hint="In the policy's own terms. The model proposes; the engine decides this sandbox's cases again; a person approves."
                    counter={{ value: text.length, max: TEXT_LIMIT }}
                  >
                    <textarea
                      id="change-request"
                      className="textarea textarea--he change__request"
                      dir="auto"
                      rows={2}
                      maxLength={TEXT_LIMIT}
                      aria-describedby="change-request-hint"
                      value={text}
                      onChange={(event) => setText(event.target.value)}
                    />
                  </Field>
                  <div className="change__actions">
                    {base ? (
                      <span className="reason">
                        On <span className="mono">Published v{base.versionNo}</span>
                      </span>
                    ) : null}
                    <Button
                      variant="primary"
                      type="submit"
                      busy={running}
                      disabled={base === null || version.data === undefined || text.trim() === ''}
                    >
                      Propose the change
                    </Button>
                  </div>
                </form>
                {started(state) ? (
                  <Progress state={state} decisions={stats.data?.decisions} />
                ) : null}
                {state.status === 'failed' ? <Refused failure={state.failure} /> : null}
              </div>
            </Section>
            {proposal && proposedOn ? (
              <>
                <ProposalSection
                  proposal={proposal}
                  proposedOn={proposedOn}
                  language={language}
                  document={document}
                />
                <Section
                  title="Regression"
                  actions={<Actor kind="engine">{engineLine(proposal, proposedOn)}</Actor>}
                >
                  <RegressionReport
                    regression={proposal.regression}
                    baseVersionNo={proposedOn.versionNo}
                    onBothTraces={setTraced}
                  />
                </Section>
                <Section title="Decision">
                  {state.status === 'decided' ? (
                    <Decided
                      decision={state.decision}
                      proposedOn={proposedOn}
                      onOpenRules={onOpenRules}
                      onOpenAudit={onOpenAudit}
                    />
                  ) : (
                    <form className="change__form" onSubmit={(event) => event.preventDefault()}>
                      <Field label="Note for the audit log" htmlFor="change-note">
                        <textarea
                          id="change-note"
                          className="textarea textarea--he change__note"
                          dir="auto"
                          rows={2}
                          maxLength={TEXT_LIMIT}
                          value={note}
                          onChange={(event) => setNote(event.target.value)}
                        />
                      </Field>
                      <div className="change__actions">
                        <span className="reason">{publishesText(proposedOn)}</span>
                        <Button
                          variant="danger"
                          busy={change.deciding && verdict === 'reject'}
                          disabled={change.deciding}
                          onClick={() => decide('reject')}
                        >
                          Reject
                        </Button>
                        <Button
                          variant="primary"
                          busy={change.deciding && verdict === 'approve'}
                          disabled={change.deciding}
                          onClick={() => decide('approve')}
                        >
                          {`Approve and publish v${String(nextVersion(proposedOn))}`}
                        </Button>
                      </div>
                      {change.decisionError ? (
                        <Refusal
                          code={change.decisionError.code}
                          title={`The ${verdict === 'approve' ? 'approval' : 'rejection'} was refused.`}
                          rows={[]}
                          explanation={decisionFailureText(change.decisionError.code)}
                        />
                      ) : null}
                    </form>
                  )}
                </Section>
              </>
            ) : null}
          </>
        }
      />
    </>
  )
}

/**
 * The version an approval publishes (Document 2, approve): the next one of the rule set, or, on a protected base,
 * version 2 of the sandbox's own copy, whose version 1 is the base as published.
 */
function nextVersion(base: ProposedOn): number {
  return base.protected ? 2 : base.versionNo + 1
}

/** What approving does, beside the primary (the spec's states table: a seeded base publishes into the copy). */
function publishesText(base: ProposedOn): string {
  const next = `v${String(nextVersion(base))}`
  return base.protected
    ? `Approving publishes ${next} in this sandbox's copy.`
    : `Approving publishes ${next}.`
}

/** The engine's line over the regression (the spec, section 09): "engine · 200 decisions of v1 · 0.9 s". */
function engineLine(proposal: Proposal, base: ProposedOn): string {
  const decisions = COUNT.format(proposal.regression.decisions)
  return `engine · ${decisions} decisions of v${String(base.versionNo)} · ${durationText(proposal.ended.ms * 1_000)}`
}

/** What a finished stage took: its time, and what its answers spent when it asked the model: "4.1 s · 2,140 tokens". */
function spentText(timing: StageEnded): string {
  const time = durationText(timing.ms * 1_000)
  return timing.tokens === null ? time : `${time} · ${COUNT.format(timing.tokens)} tokens`
}

/** A run the stages have something to say about: a request refused before the stream opened has none. */
function started(state: ChangeState): state is Exclude<ChangeState, { status: 'idle' }> {
  return (
    state.status !== 'idle' &&
    !(state.status === 'failed' && state.timings.length === 0 && state.candidates === null)
  )
}

/**
 * The four stages (the spec, section 08's progress): the one running now, the ones done with their time and the tokens
 * their answers spent, and under the first the rules considered, the ones the proposal left unchanged faded once it has
 * arrived, so the model's reach is visible beside its result. A stage a failure ended keeps its time but no mark.
 */
function Progress({
  state,
  decisions,
}: {
  state: Exclude<ChangeState, { status: 'idle' }>
  /** How many decisions the regression decides again, from the base version's statistics. */
  decisions: number | undefined
}) {
  const consideredId = useId()
  const ended = new Map(state.timings.map((timing) => [timing.stage, timing]))
  const now = state.status === 'running' ? state.stage : null
  const failedAt = state.status === 'failed' ? (state.failure.ended?.stage ?? null) : null
  const untouched = new Set(
    state.status === 'proposed' || state.status === 'decided' ? state.proposal.untouched : [],
  )
  return (
    <div className="progress change__progress" role="group" aria-label="Progress">
      {CHANGE_STAGES.map((stage) => {
        const timing = ended.get(stage)
        const done = timing !== undefined && stage !== now && stage !== failedAt
        const meta =
          timing !== undefined
            ? spentText(timing)
            : stage === now && stage === 'regression' && decisions !== undefined
              ? `${COUNT.format(decisions)} ${decisions === 1 ? 'case' : 'cases'}`
              : null
        const mark = stage === now ? ' progress__step--now' : done ? ' progress__step--done' : ''
        return (
          <Fragment key={stage}>
            <div
              className={`progress__step${mark}`}
              aria-current={stage === now ? 'step' : undefined}
            >
              <span className="progress__mark" />
              <span>{STAGE_LABELS[stage]}</span>
              {meta === null ? null : <span className="progress__meta">{meta}</span>}
            </div>
            {stage === 'analyzing' && state.candidates ? (
              <div className="progress__sub">
                <span className="candidates" role="group" aria-labelledby={consideredId}>
                  <span id={consideredId}>Rules considered</span>
                  {state.candidates.candidates.map((ruleId) => (
                    <Chip key={ruleId} unchanged={untouched.has(ruleId)}>
                      {ruleId}
                    </Chip>
                  ))}
                </span>
              </div>
            ) : null}
          </Fragment>
        )
      })}
    </div>
  )
}

/**
 * What the model proposes (the spec, section 09): the patches by operation, each with its rule and the model's
 * rationale in the policy's language, then the rules it considered and left unchanged, its notes, and the diff of the
 * base against the patched copy, one row per changed cell.
 */
function ProposalSection({
  proposal,
  proposedOn,
  language,
  document,
}: {
  proposal: Proposal
  proposedOn: ProposedOn
  language: ContentLanguage
  document: RuleSetDocument | undefined
}) {
  const untouchedId = useId()
  const content = contentAttributes(language)
  const count = proposal.patches.length
  return (
    <Section
      title="Proposal"
      subtitle={`${String(count)} ${count === 1 ? 'patch' : 'patches'}`}
      actions={
        <>
          <span className="vstatus vstatus--pending">Proposed</span>
          <Actor kind="model">model</Actor>
        </>
      }
      flush
    >
      <ul className="change__patches" aria-label="Patches">
        {proposal.patches.map((patch) => {
          const label = labelOf(patch, document)
          return (
            <li key={patchKey(patch)} className="patch">
              <div className="patch__head">
                <span className="patch__op">{OP_LABELS[patch.op]}</span>
                <PatchTarget patch={patch} />
                {label === undefined ? null : (
                  <span className="step__label" {...content}>
                    {isolated(label, language)}
                  </span>
                )}
              </div>
              <p className="patch__rationale" {...content}>
                {isolated(patch.rationale, language)}
              </p>
            </li>
          )
        })}
      </ul>
      {proposal.untouched.length > 0 ? (
        <div className="patch">
          <div className="candidates" role="group" aria-labelledby={untouchedId}>
            <span id={untouchedId}>Considered and left unchanged</span>
            {proposal.untouched.map((ruleId) => (
              <Chip key={ruleId} unchanged>
                {ruleId}
              </Chip>
            ))}
          </div>
        </div>
      ) : null}
      {proposal.notes.trim() !== '' ? (
        <div className="change__notes">
          <Note tone="proposal">
            <span {...content}>{isolated(proposal.notes, language)}</span>
          </Note>
        </div>
      ) : null}
      <div className="change__diff">
        <DiffView
          diff={proposal.diff}
          language={language}
          fields={document?.fields}
          rules={document?.rules}
          beforeLabel={`Published v${String(proposedOn.versionNo)}`}
          afterLabel="Proposed"
        />
      </div>
    </Section>
  )
}

/** What a patch works on: a rule's id, a field's name, or the outcome the defaults would take. */
function PatchTarget({ patch }: { patch: Patch }) {
  switch (patch.op) {
    case 'add_field':
      return <Chip kind="field">{patch.field.name}</Chip>
    case 'set_defaults':
      return <DecisionTag status={patch.defaults.outcome} action quiet />
    default:
      return <Chip>{patch.ruleId}</Chip>
  }
}

/** The label of the rule a patch writes, or of the rule it removes, as the base version has it. */
function labelOf(patch: Patch, document: RuleSetDocument | undefined): string | undefined {
  if (patch.op === 'add' || patch.op === 'replace') {
    return patch.rule.label
  }
  if (patch.op === 'remove') {
    return document?.rules.find((rule) => rule.id === patch.ruleId)?.label
  }
  return undefined
}

/** A person's decision, sealed (the spec, section 04: the seal is where a person's authority becomes a fact). */
function Decided({
  decision,
  proposedOn,
  onOpenRules,
  onOpenAudit,
}: {
  decision: ChangeDecision
  proposedOn: ProposedOn
  onOpenRules?: () => void
  onOpenAudit?: () => void
}) {
  const approved = decision.status === 'APPROVED'
  return (
    <div className="change__decided" role="status">
      <Seal
        kicker={approved ? 'Approved' : 'Rejected'}
        line={`${changeRequestName(decision.number)} · ${dateTimeOf(decision.decidedAt)}`}
        by={PERSON}
        stamp
      />
      <span className="change__sentence">
        {approved ? (
          <>
            <span>
              {approvedText(decision.result?.versionNo ?? nextVersion(proposedOn), proposedOn)}
            </span>
            {onOpenRules ? (
              <>
                {' '}
                <Button variant="link" onClick={onOpenRules}>
                  Open the rules
                </Button>
              </>
            ) : null}
            {onOpenAudit ? (
              <>
                {onOpenRules ? ' · ' : ' '}
                <Button variant="link" onClick={onOpenAudit}>
                  Open the audit log
                </Button>
              </>
            ) : null}
          </>
        ) : (
          <span>Rejected. Nothing was published.</span>
        )}
      </span>
    </div>
  )
}

/** The product's sentence after an approval (the spec, section 09), where the version was published. */
function approvedText(published: number, base: ProposedOn): string {
  const where = base.protected ? " in this sandbox's own copy of the rule set" : ''
  return `Approved. Version ${String(published)} is published${where}; the cases decide on it from now on.`
}

/**
 * A refused request as the refusal block of section 08: the code, a row per pointer into the model's answer and its
 * problem, and the closing fact; RT-04's refusals keep what the model attempted behind a link (Document 5).
 */
function Refused({ failure }: { failure: StreamFailure }) {
  const [shown, setShown] = useState(false)
  const attempted = patchesOf(failure.document)
  const invalid = failure.code === 'RULESET_INVALID'
  return (
    <div className="change__refused">
      <Refusal
        code={failure.code}
        title={invalid ? 'The proposal was refused.' : 'The change could not be proposed.'}
        rows={findingRows(failure.findings)}
        explanation={invalid ? undefined : proposeFailureText(failure.code)}
        next={
          attempted.length > 0 ? (
            <Button variant="link" aria-expanded={shown} onClick={() => setShown(!shown)}>
              What the model proposed
            </Button>
          ) : undefined
        }
      />
      {shown ? (
        <ul className="change__attempted" aria-label="What the model proposed">
          {attempted.map(({ pointer, patch }) => (
            <li key={pointer}>{attemptText(patch)}</li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

/**
 * The patches of the model's last answer, when it was a Patches object at all, each with its JSON pointer into the
 * answer: the path the refusals name (Document 3, Patch validation).
 */
function patchesOf(document: unknown): { pointer: string; patch: Patch }[] {
  const patches = (document as { patches?: unknown } | null)?.patches
  return Array.isArray(patches)
    ? (patches as Patch[]).map((patch, index) => ({ pointer: `/patches/${String(index)}`, patch }))
    : []
}

function patchTarget(patch: Patch): string {
  switch (patch.op) {
    case 'add_field':
      return patch.field.name
    case 'set_defaults':
      return ''
    default:
      return patch.ruleId
  }
}

function patchKey(patch: Patch): string {
  return `${patch.op}:${patchTarget(patch)}`
}

function attemptText(patch: Patch): string {
  if (patch.op === 'set_defaults') {
    return `${OP_LABELS[patch.op]} to ${DECISION_LABELS[patch.defaults.outcome]}`
  }
  return `${OP_LABELS[patch.op]} ${patchTarget(patch)}`
}
