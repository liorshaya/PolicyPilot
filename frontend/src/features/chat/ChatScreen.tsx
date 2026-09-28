import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { SCRIPTED_QUESTIONS } from '../demo/steps'
import { useDemoStep } from '../demo/useDemoStep'
import { Paragraph } from '../policy/Paragraph'
import { publishedTarget } from '../../api/published'
import { useBudget, usePolicy, useRulesets } from '../../api/queries'
import type { RulesetSummary } from '../../api/types'
import {
  contentAttributes,
  directionOfText,
  isolated,
  type ContentLanguage,
} from '../../shared/i18n/direction'
import { durationText, timeOf } from '../../shared/i18n/time'
import { SplitView } from '../../shared/layout/SplitView'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Actor } from '../../shared/ui/Actor'
import { Button } from '../../shared/ui/Button'
import { Chip } from '../../shared/ui/Chip'
import { Note } from '../../shared/ui/Note'
import { EmptyState, LoadingRows } from '../../shared/ui/States'
import { DecisionTag } from '../../shared/ui/StatusTag'
import { DECISION_LABELS } from '../../shared/ui/decisionLabels'
import { failureText } from './failures'
import { markerLabel, placed, segments } from './markers'
import type { ChatCitation, ChatExchange, ChatToolCall } from './types'
import { useChat, type ChatTarget } from './useChat'
import './ChatScreen.css'

/** The composer's hint in the version's language; the Hebrew is the spec's own (section 09, the assistant). */
const PLACEHOLDERS: Record<ContentLanguage, string> = {
  he: 'שאל על כלל, על סעיף במדיניות או על מספר בקשה',
  en: 'Ask about a rule, a paragraph of the policy or a case number',
}

/** What a refused tool call says in its step line (Document 5, Tool argument validation and Tool call volume). */
const REFUSALS: Record<NonNullable<ChatToolCall['refused']>, string> = {
  not_found: 'not in this session',
  invalid_arguments: 'its arguments were refused',
  limit: "over this answer's limit of lookups",
}

/** A failure that the stream reports in place of the answer, where the thread shows it; the rest go under the composer. */
const WITHHELD = 'ANSWER_WITHHELD'
const BUDGET = 'BUDGET_EXHAUSTED'

/**
 * The assistant (Document 2, Flow 3; the Register spec, section 09, "The assistant: a paper trail, read right to left").
 * Questions about a published version, answered with citations the API has checked. The thread reads in the version's
 * language inside the English chrome: marks on the reading-start side, each tool call a step line above the answer that
 * used it, citations after the punctuation and repeated in the sources strip, and the system's mark on a fixed sentence
 * and on a withheld answer. The model explains; the engine decided every outcome an answer reports.
 */
export function ChatScreen({
  rulesetId = null,
  onOpenRule,
  onOpenCases,
  demoAsked = false,
  onDemoHandled,
}: {
  /** The rule set the workspace is on; without one the seeded rule set is used. */
  rulesetId?: string | null
  onOpenRule: (ruleId: string) => void
  onOpenCases: () => void
  /** Step 3 of the guided demo: open on the first scripted question (Brief FR-23). */
  demoAsked?: boolean
  onDemoHandled?: () => void
}) {
  const rulesets = useRulesets()
  const list = rulesets.data ?? []
  // Document 2, chat sessions: a session is opened on a PUBLISHED version. The workspace may be on a draft written a
  // moment ago (demo step 1), so the questions are asked of the first rule set that has a published version
  const target = publishedTarget(list, rulesetId)

  if (rulesets.isPending) {
    return <LoadingRows label="Loading the rule sets" />
  }
  if (target === null) {
    return (
      <>
        <WorkspaceHeader title="Assistant" />
        <EmptyState
          title="No published version to ask about"
          description="The assistant answers questions about a published version. Publish a rule set, then come back."
        />
      </>
    )
  }
  return (
    <Conversation
      // a new version is a new session, with a conversation of its own
      key={`${target.ruleset.id}:${String(target.versionNo)}`}
      ruleset={target.ruleset}
      elsewhere={target.elsewhere}
      target={{ rulesetId: target.ruleset.id, versionNo: target.versionNo }}
      onOpenRule={onOpenRule}
      onOpenCases={onOpenCases}
      demoAsked={demoAsked}
      onDemoHandled={onDemoHandled}
    />
  )
}

interface Opens {
  onOpenRule: (ruleId: string) => void
  onOpenCases: () => void
  onOpenParagraph: (index: number) => void
}

function Conversation({
  ruleset,
  elsewhere,
  target,
  onOpenRule,
  onOpenCases,
  demoAsked,
  onDemoHandled,
}: {
  ruleset: RulesetSummary
  /** The workspace is on a rule set with no published version; the questions are about this one instead. */
  elsewhere: boolean
  target: ChatTarget
  onOpenRule: (ruleId: string) => void
  onOpenCases: () => void
  demoAsked: boolean
  onDemoHandled?: () => void
}) {
  const chat = useChat(target)
  const budget = useBudget()
  const policy = usePolicy(ruleset.policyId ?? null)
  const [question, setQuestion] = useState('')
  const [openParagraph, setOpenParagraph] = useState<number | null>(null)
  // step 3 of the demo types the first scripted question; the presenter presses Ask and asks the next two
  useDemoStep(demoAsked, () => setQuestion(SCRIPTED_QUESTIONS[0]), onDemoHandled)
  const paragraphs = policy.data?.versions?.[policy.data.versions.length - 1]?.paragraphs ?? []
  const shownParagraph = paragraphs.find((paragraph) => paragraph.index === openParagraph)
  const last = chat.exchanges.at(-1)
  const failure = last?.status === 'failed' ? last.code : chat.openFailure
  const spentNow = failure === BUDGET
  const { refetch } = budget
  // a question that finds the day's budget spent reads the budget again, whose note then says when it resumes
  const spentOn = spentNow ? last?.id : undefined
  useEffect(() => {
    if (spentOn !== undefined) {
      void refetch()
    }
  }, [spentOn, refetch])
  const opens: Opens = { onOpenRule, onOpenCases, onOpenParagraph: setOpenParagraph }

  function submit(event: FormEvent) {
    event.preventDefault()
    const asked = question.trim()
    if (asked !== '' && chat.ready && !chat.streaming) {
      chat.ask(asked)
      setQuestion('')
    }
  }

  return (
    <>
      <WorkspaceHeader
        title="Assistant"
        provenance={[
          <bdi key="name" dir="auto" className="sans">
            {ruleset.name}
          </bdi>,
          'answers cite the policy and the rules; the engine decided every outcome they report',
        ]}
        version={<span className="tabular">Version {target.versionNo}</span>}
      />
      <SplitView
        sideOpen={shownParagraph !== undefined}
        sideLabel={shownParagraph ? `Paragraph ${String(shownParagraph.index)}` : undefined}
        side={
          shownParagraph ? (
            <section className="margin__section">
              <div className="margin__title">
                <span>
                  Policy{' '}
                  <Chip kind="para" active>
                    {shownParagraph.index}
                  </Chip>
                </span>
                <Button variant="link" onClick={() => setOpenParagraph(null)}>
                  Close
                </Button>
              </div>
              <Paragraph
                index={shownParagraph.index}
                text={shownParagraph.text}
                language={policy.data?.language === 'he' ? 'he' : 'en'}
                cited
              />
            </section>
          ) : undefined
        }
        main={
          <div className="thread" dir={chat.language === 'he' ? 'rtl' : 'ltr'}>
            {elsewhere ? (
              <Note>
                The rule set on the workspace has no published version yet; the questions are about
                the seeded one.
              </Note>
            ) : null}
            <div
              className="thread__log"
              role="log"
              aria-label="Conversation"
              aria-live="polite"
              {...contentAttributes(chat.language)}
            >
              {chat.exchanges.map((exchange) => (
                <Exchange
                  key={exchange.id}
                  exchange={exchange}
                  language={chat.language}
                  versionNo={target.versionNo}
                  opens={opens}
                />
              ))}
            </div>
            {chat.exchanges.length === 0 && !chat.openFailure ? (
              <div className="thread__starters" aria-label="The demo's questions" role="group">
                {SCRIPTED_QUESTIONS.map((scripted) => (
                  <Button key={scripted} variant="quiet" onClick={() => setQuestion(scripted)}>
                    {scripted}
                  </Button>
                ))}
              </div>
            ) : null}
            <form className="composer" onSubmit={submit}>
              <textarea
                className="textarea textarea--he"
                dir="auto"
                aria-label="Question"
                placeholder={PLACEHOLDERS[chat.language]}
                rows={2}
                maxLength={1000}
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    submit(event)
                  }
                }}
              />
              <Button
                type="submit"
                variant="primary"
                size="lg"
                busy={chat.streaming}
                disabled={!chat.ready || chat.streaming || question.trim() === ''}
              >
                Ask
              </Button>
            </form>
            {budget.data?.spent || spentNow ? (
              <Note tone="warning" label="Budget">
                {`Today's model budget is spent${budget.data ? ` until ${timeOf(budget.data.resumesAt)}` : ''}. The demo's questions are still answered from the cache.`}
              </Note>
            ) : null}
            {failure !== undefined &&
            failure !== null &&
            failure !== BUDGET &&
            failure !== WITHHELD ? (
              <Note tone="error">
                {failureText(failure)}
                {last?.status === 'failed' ? (
                  <>
                    {' '}
                    <Button variant="link" onClick={chat.retry}>
                      Try again
                    </Button>
                  </>
                ) : null}
              </Note>
            ) : null}
          </div>
        }
      />
    </>
  )
}

/** One question and its answer: two turns of the thread, each with the mark of who speaks on the reading-start side. */
function Exchange({
  exchange,
  language,
  versionNo,
  opens,
}: {
  exchange: ChatExchange
  language: ContentLanguage
  versionNo: number
  opens: Opens
}) {
  const asked: ContentLanguage = directionOfText(exchange.question) === 'rtl' ? 'he' : 'en'
  const withheld = exchange.status === 'failed' && exchange.code === WITHHELD
  return (
    <>
      <div className="turn turn--q">
        <span className="turn__who">
          <Actor kind="person" large title="The question" />
        </span>
        <p className="turn__body" {...contentAttributes(asked)}>
          {isolated(exchange.question, asked)}
        </p>
      </div>
      {withheld ? (
        <div className="turn turn--a">
          <span className="turn__who">
            <Actor kind="system" large title="The system" />
          </span>
          <div className="turn__body">
            <div className="note note--warning">
              <span>{failureText(WITHHELD)}</span>
            </div>
          </div>
        </div>
      ) : exchange.status !== 'failed' ? (
        <Answer exchange={exchange} language={language} versionNo={versionNo} opens={opens} />
      ) : null}
    </>
  )
}

function Answer({
  exchange,
  language,
  versionNo,
  opens,
}: {
  exchange: ChatExchange
  language: ContentLanguage
  versionNo: number
  opens: Opens
}) {
  const fixed = exchange.fixed !== null
  const streaming = exchange.status === 'streaming'
  const citations = exchange.citations
  const classes = ['answer', fixed ? 'answer--fixed' : '', streaming ? 'streaming' : '']
  return (
    <div className="turn turn--a">
      <span className="turn__who">
        {fixed ? (
          <Actor kind="system" large title="A fixed sentence, not the model's" />
        ) : (
          <Actor kind="model" large title="The model's answer" />
        )}
      </span>
      <div className="turn__body">
        {exchange.steps.length > 0 ? (
          <ol className="thread__steps" aria-label="Tool calls">
            {exchange.steps.map((step) => (
              <StepLine key={step.at} step={step} />
            ))}
          </ol>
        ) : null}
        <p className={classes.filter(Boolean).join(' ')} {...contentAttributes(language)}>
          {placed(segments(exchange.answer)).map((part) => {
            if (part.kind === 'text') {
              return <span key={part.at}>{isolated(part.text, language)}</span>
            }
            // once the citations have arrived, the first source of the claim the API cited is the chip inline
            const id =
              citations === null
                ? part.ids[0]
                : part.ids.find((one) => citations.some((cited) => cited.id === one))
            return id === undefined ? null : (
              <CitationChip
                key={part.at}
                id={id}
                citation={citations?.find((cited) => cited.id === id)}
                opens={opens}
              />
            )
          })}
        </p>
        {fixed ? (
          <div className="sources">
            <span>No source · a fixed sentence, not the model&apos;s</span>
          </div>
        ) : citations !== null ? (
          <div className="sources" role="group" aria-label="Sources">
            {citations.length === 0 ? (
              <span>No source</span>
            ) : (
              <>
                <span>Cited</span>
                {citations.map((citation) => (
                  <CitationChip
                    key={citation.id}
                    id={citation.id}
                    citation={citation}
                    opens={opens}
                  />
                ))}
              </>
            )}
            {citations.some((cited) => cited.kind === 'DECISION' || cited.kind === 'SIMULATION') ? (
              <span className="end">
                <Actor kind="engine">the outcome is the engine&apos;s, on v{versionNo}</Actor>
              </span>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  )
}

/**
 * One tool call as a step line (the spec, section 09: "the tool chip, what it ran on, how long it took, the outcome as
 * a real decision tag"): "what-if · case 17 · has_guarantor=true · ran on v1 · 58 µs · Approved · flag …".
 */
function StepLine({ step }: { step: ChatToolCall }) {
  return (
    <li className="steps">
      <Chip kind="tool">{toolText(step)}</Chip>
      <span>
        ran on <span className="mono">v{step.versionNo}</span> · {durationText(step.micros)}
      </span>
      {step.refused !== null ? (
        <span>{REFUSALS[step.refused]}</span>
      ) : step.outcome !== null ? (
        <DecisionTag status={step.outcome} quiet />
      ) : null}
      {step.flags.map((flag) => (
        <span key={flag}>
          · flag <span className="mono">{flag}</span>
        </span>
      ))}
    </li>
  )
}

/** What the tool chip names: the what-if with its case and change, the case read, the statistics, the rule listing. */
function toolText(step: ChatToolCall): string {
  const theCase =
    step.applicationNumber === null ? 'case' : `case ${String(step.applicationNumber)}`
  switch (step.tool) {
    case 'simulate':
      return ['what-if', theCase, step.overrides].filter(Boolean).join(' · ')
    case 'getDecision':
      return theCase
    case 'getDecisionStats':
      return 'statistics'
    case 'listRules':
      return step.tag === null ? 'rules' : `rules · ${step.tag}`
  }
}

/**
 * A citation as a chip (the spec, section 06): a paragraph's pill opens it in the margin, a rule's id opens the rule,
 * a case opens the cases, and a simulation is the tool chip that says what the engine simulated.
 */
function CitationChip({
  id,
  citation,
  opens,
}: {
  id: string
  citation: ChatCitation | undefined
  opens: Opens
}) {
  const label = markerLabel(id)
  if (citation?.kind === 'PARAGRAPH' && citation.paragraph !== undefined) {
    const paragraph = citation.paragraph
    return (
      <Chip
        kind="para"
        label={`Paragraph ${String(paragraph)}`}
        title={`Paragraph ${String(paragraph)} of the policy`}
        onClick={() => opens.onOpenParagraph(paragraph)}
      >
        {paragraph}
      </Chip>
    )
  }
  if (citation?.kind === 'RULE' && citation.ruleId) {
    const ruleId = citation.ruleId
    return (
      <Chip title={`${ruleId} · ${citation.label ?? ''}`} onClick={() => opens.onOpenRule(ruleId)}>
        {ruleId}
      </Chip>
    )
  }
  if (citation?.kind === 'DECISION') {
    return (
      <Chip
        title={`${label} · ${outcomeWords(citation)}, as the engine decided`}
        onClick={opens.onOpenCases}
      >
        {label}
      </Chip>
    )
  }
  if (citation?.kind === 'SIMULATION') {
    return (
      <Chip
        kind="tool"
        title={`What if ${citation.detail ?? ''}: ${outcomeWords(citation)}, as the engine simulated`}
      >
        {label}
      </Chip>
    )
  }
  // a marker whose citation has not arrived yet: its label, and nothing to open until the API vouches for it
  return (
    <Chip kind={id.startsWith('sim:') ? 'tool' : id.startsWith('p:') ? 'para' : 'id'}>
      {chipText(id)}
    </Chip>
  )
}

/** A chip's text before its citation arrives: a paragraph pill writes its number, which the pill's ¶ precedes. */
function chipText(id: string): ReactNode {
  return id.startsWith('p:') ? id.slice(2) : markerLabel(id)
}

function outcomeWords(citation: ChatCitation): string {
  return citation.outcome ? DECISION_LABELS[citation.outcome] : 'no outcome'
}
