import { useState, type FormEvent } from 'react'
import { publishedTarget } from '../../api/published'
import { useRulesets, useVersion } from '../../api/queries'
import type { RuleSetDocument } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { SplitView } from '../../shared/layout/SplitView'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Button } from '../../shared/ui/Button'
import { Field } from '../../shared/ui/Field'
import { Panel } from '../../shared/ui/Panel'
import { VersionTag } from '../../shared/ui/StatusTag'
import { DECISION_LABELS, type VersionStatus } from '../../shared/ui/decisionLabels'
import { DiffView } from './DiffView'
import { RegressionReport } from './RegressionReport'
import { decisionFailureText, proposeFailureText } from './failures'
import { useChange } from './useChange'
import {
  CHANGE_STAGES,
  type Candidates,
  type ChangeStage,
  type Patch,
  type Proposal,
  type StreamFailure,
} from './types'
import './ChangeScreen.css'

/**
 * The chat message's limit, which a change request and a note share (Document 5, Input limits: 2 KB); 1,000 characters
 * keep a Hebrew text under it, as the chat composer does.
 */
const TEXT_LIMIT = 1000

const STAGE_LABELS: Record<ChangeStage, string> = {
  analyzing: 'Finding the rules the request touches',
  proposing: 'Writing the patches',
  validating: 'Checking the patched rules against the policy',
  regression: "Deciding this sandbox's cases again",
}

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
}

/**
 * The change screen (Brief, demo step 4; Document 2, Frontend Architecture: "change request box, diff view (side by
 * side, rule level), regression report, approve and reject"). The model proposes, the engine decides this sandbox's
 * cases again on the proposal, and a person approves or rejects with a note: nothing is published before that. A
 * proposal the validator refused is shown with its refusals and what the model attempted (Document 5, RT-04).
 */
export function ChangeScreen({
  rulesetId = null,
  onPublished,
  onOpenRules,
  onOpenAudit,
}: ChangeScreenProps) {
  const rulesets = useRulesets()
  // Document 2: a change is proposed on a PUBLISHED version; the workspace may be on a draft written a moment ago
  const target = publishedTarget(rulesets.data ?? [], rulesetId)
  const base = target ? { id: target.ruleset.id, versionNo: target.versionNo } : null
  const version = useVersion(base)
  const change = useChange(base ? { rulesetId: base.id, versionNo: base.versionNo } : null)
  const [text, setText] = useState('')
  const [note, setNote] = useState('')
  const [proposedOn, setProposedOn] = useState<ProposedOn | null>(null)
  const [verdict, setVerdict] = useState<'approve' | 'reject'>('approve')
  const document = version.data?.ruleSet as RuleSetDocument | undefined
  const language: ContentLanguage = document?.language ?? 'en'
  const state = change.state
  const running = state.status === 'running'
  const proposal = state.status === 'proposed' || state.status === 'decided' ? state.proposal : null

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
        context={
          version.data ? (
            <>
              <bdi dir="auto">{version.data.name}</bdi> ·{' '}
              <span className="mono">{version.data.domain}</span>
            </>
          ) : (
            'A change request, measured on the cases before a person approves it'
          )
        }
        version={
          version.data ? (
            <>
              <span className="tabular">Version {version.data.versionNo}</span>
              <VersionTag status={version.data.status as VersionStatus} />
              {version.data.protected ? <span className="change__seeded">Seeded</span> : null}
            </>
          ) : null
        }
      />
      <SplitView
        sideOpen={false}
        main={
          <>
            <Panel
              title="Change request"
              subtitle="In the policy's own terms. The model proposes, the engine decides this sandbox's cases again, and a person approves"
            >
              {target?.elsewhere ? (
                <p className="change__elsewhere" role="note">
                  The rule set on the workspace has no published version yet; the change is proposed
                  on the seeded one.
                </p>
              ) : null}
              <form className="change__form" onSubmit={submit}>
                <Field label="What should change" htmlFor="change-request">
                  <textarea
                    id="change-request"
                    className="change__input"
                    dir="auto"
                    rows={3}
                    maxLength={TEXT_LIMIT}
                    value={text}
                    onChange={(event) => setText(event.target.value)}
                  />
                </Field>
                <Button
                  variant="primary"
                  type="submit"
                  loading={running}
                  disabled={base === null || version.data === undefined || text.trim() === ''}
                >
                  Propose the change
                </Button>
              </form>
              {state.status === 'running' ? (
                <Progress stage={state.stage} candidates={state.candidates} />
              ) : null}
              {state.status === 'failed' ? <Refused failure={state.failure} /> : null}
            </Panel>
            {proposal && proposedOn ? (
              <>
                <ProposalPanel proposal={proposal} language={language} />
                <Panel
                  title="What changes"
                  subtitle={`Version ${proposedOn.versionNo} beside the proposal, rule by rule`}
                >
                  <DiffView
                    diff={proposal.diff}
                    language={language}
                    fields={document?.fields}
                    beforeLabel={`Version ${proposedOn.versionNo}`}
                    afterLabel="Proposed"
                  />
                </Panel>
                <Panel
                  title="Regression"
                  subtitle={`This sandbox's decisions on version ${proposedOn.versionNo}, decided again by the proposal`}
                >
                  <RegressionReport
                    regression={proposal.regression}
                    baseVersionNo={proposedOn.versionNo}
                  />
                </Panel>
                <Panel
                  title="Decision"
                  subtitle="A person approves or rejects; the note goes into the audit log with the request"
                >
                  {state.status === 'decided' ? (
                    <div className="change__decided" role="status">
                      {state.decision.status === 'APPROVED' ? (
                        <>
                          <p>{approvedText(state.decision.result?.versionNo, proposedOn)}</p>
                          <div className="change__actions">
                            {onOpenAudit ? (
                              <Button variant="primary" onClick={onOpenAudit}>
                                Open the audit log
                              </Button>
                            ) : null}
                            {onOpenRules ? (
                              <Button onClick={onOpenRules}>Open the rules</Button>
                            ) : null}
                          </div>
                        </>
                      ) : (
                        <p>Rejected. Nothing was published.</p>
                      )}
                    </div>
                  ) : (
                    <form className="change__form" onSubmit={(event) => event.preventDefault()}>
                      <Field label="Note for the audit log" htmlFor="change-note">
                        <textarea
                          id="change-note"
                          className="change__input"
                          dir="auto"
                          rows={2}
                          maxLength={TEXT_LIMIT}
                          value={note}
                          onChange={(event) => setNote(event.target.value)}
                        />
                      </Field>
                      <div className="change__actions">
                        <Button
                          variant="primary"
                          loading={change.deciding && verdict === 'approve'}
                          disabled={change.deciding}
                          onClick={() => decide('approve')}
                        >
                          Approve and publish
                        </Button>
                        <Button
                          loading={change.deciding && verdict === 'reject'}
                          disabled={change.deciding}
                          onClick={() => decide('reject')}
                        >
                          Reject
                        </Button>
                      </div>
                      {change.decisionError ? (
                        <p className="change__refused" role="alert">
                          The {verdict === 'approve' ? 'approval' : 'rejection'} was refused (
                          <span className="mono">{change.decisionError.code}</span>).{' '}
                          {decisionFailureText(change.decisionError.code)}
                        </p>
                      ) : null}
                    </form>
                  )}
                </Panel>
              </>
            ) : null}
          </>
        }
      />
    </>
  )
}

function approvedText(published: number | undefined, base: ProposedOn): string {
  const where = base.protected ? " in this sandbox's own copy of the seeded rule set" : ''
  return `Approved. Version ${published ?? '?'} is published${where}; version ${base.versionNo} is unchanged.`
}

/** Where the stream is: the four stages in order, and the rules the model is shown once they are known. */
function Progress({
  stage,
  candidates,
}: {
  stage: ChangeStage | null
  candidates: Candidates | null
}) {
  return (
    <div className="change__progress" role="status" aria-live="polite">
      <ol className="change__stages" aria-label="Progress">
        {CHANGE_STAGES.map((one, index) => (
          <li
            key={one}
            className={`change__stage${one === stage ? ' change__stage--now' : ''}`}
            aria-current={one === stage ? 'step' : undefined}
          >
            <span className="change__stage-number tabular">{index + 1}</span>
            <span>{STAGE_LABELS[one]}</span>
          </li>
        ))}
      </ol>
      {candidates ? (
        <dl className="change__considered">
          <dt>Rules considered</dt>
          <dd className="mono">{candidates.candidates.join(', ')}</dd>
          <dt>Fields</dt>
          <dd className="mono">{candidates.fields.join(', ')}</dd>
        </dl>
      ) : null}
    </div>
  )
}

/** What the model proposes, patch by patch with its rationale, and what it considered and left as it was. */
function ProposalPanel({ proposal, language }: { proposal: Proposal; language: ContentLanguage }) {
  const content = contentAttributes(language)
  return (
    <Panel title="Proposal" subtitle="Nothing is published until a person approves">
      <p className="change__summary" {...content}>
        {proposal.summary}
      </p>
      <ul className="change__patches" aria-label="Rules to change">
        {proposal.patches.map((patch) => (
          <li key={patchKey(patch)} className="change__patch">
            <span className="change__patch-head">
              {OP_LABELS[patch.op]} <span className="mono">{patchTarget(patch)}</span>
            </span>
            {'rule' in patch ? (
              <span className="change__patch-label" {...content}>
                {patch.rule.label}
              </span>
            ) : null}
            <span className="change__rationale" {...content}>
              {patch.rationale}
            </span>
          </li>
        ))}
      </ul>
      {proposal.untouched.length > 0 ? (
        <p className="change__untouched">
          <span>Considered and left unchanged</span>{' '}
          <span className="mono">{proposal.untouched.join(', ')}</span>
        </p>
      ) : null}
      {proposal.notes.trim() !== '' ? (
        <p className="change__notes" {...content}>
          {proposal.notes}
        </p>
      ) : null}
    </Panel>
  )
}

/** A refused request: RT-04's refusals with what the model attempted, or why the request did not go through. */
function Refused({ failure }: { failure: StreamFailure }) {
  if (failure.code !== 'RULESET_INVALID') {
    return (
      <p className="change__refused" role="alert">
        The change could not be proposed (<span className="mono">{failure.code}</span>).{' '}
        {proposeFailureText(failure.code)}
      </p>
    )
  }
  const attempted = patchesOf(failure.document)
  return (
    <div className="change__refused" role="alert">
      <p>
        The proposal was refused (<span className="mono">{failure.code}</span>). Nothing was stored.
      </p>
      {failure.findings.length > 0 ? (
        <ul className="change__reasons" aria-label="Why it was refused">
          {failure.findings.map((finding) => (
            <li key={`${finding.code}${finding.path}`}>
              <span className="mono">{finding.code}</span>{' '}
              <span className="mono">{finding.path}</span> <span>{finding.message}</span>
            </li>
          ))}
        </ul>
      ) : null}
      {attempted.length > 0 ? (
        <>
          <p className="change__attempted-title">What the model proposed</p>
          <ul className="change__attempted" aria-label="What the model proposed">
            {attempted.map(({ pointer, patch }) => (
              <li key={pointer}>{attemptText(patch)}</li>
            ))}
          </ul>
        </>
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
    ? (patches as Patch[]).map((patch, index) => ({ pointer: `/patches/${index}`, patch }))
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
