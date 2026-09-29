import { useEffect, useLayoutEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { SCRIPTED_QUESTIONS } from '../demo/steps'
import { useDemoStep } from '../demo/useDemoStep'
import { Paragraph } from '../policy/Paragraph'
import { publishedTarget } from '../../api/published'
import {
  useBudget,
  useChatSession,
  useChatSessions,
  useLastRun,
  usePolicy,
  useRulesets,
  useVersion,
} from '../../api/queries'
import type {
  ChatConversationResponse,
  ChatSessionSummary,
  RuleSetDocument,
  RulesetSummary,
} from '../../api/types'
import {
  contentAttributes,
  directionOfText,
  isolated,
  type ContentLanguage,
} from '../../shared/i18n/direction'
import { dateTimeOf, durationText, timeOf } from '../../shared/i18n/time'
import { SplitView } from '../../shared/layout/SplitView'
import { useWide } from '../../shared/layout/useWide'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Actor, PERSON } from '../../shared/ui/Actor'
import { Button } from '../../shared/ui/Button'
import { Chip } from '../../shared/ui/Chip'
import { Counter } from '../../shared/ui/Field'
import { Icon } from '../../shared/ui/Icon'
import { Kbd } from '../../shared/ui/Kbd'
import { Note } from '../../shared/ui/Note'
import { Popover } from '../../shared/ui/Overlay'
import { Provenance } from '../../shared/ui/Provenance'
import { EmptyState, LoadingRows } from '../../shared/ui/States'
import { DecisionTag, VersionTag } from '../../shared/ui/StatusTag'
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

/** The length of a question the API takes (Document 5, Input limits), which the composer counts against. */
const QUESTION_LIMIT = 1000

/** Which conversation the screen shows: a new one, counted so each is its own, or one read back to be resumed. */
type Open = { kind: 'new'; count: number } | { kind: 'resume'; id: string }

/**
 * The assistant (Document 2, Flow 3; the Register spec, section 09, "The assistant: a paper trail, read right to left").
 * Questions about a published version, answered with citations the API has checked. The thread reads in the version's
 * language inside the English chrome: marks on the reading-start side, each tool call a step line above the answer that
 * used it, citations after the punctuation and repeated in the sources strip, and the system's mark on a fixed sentence
 * and on a withheld answer. The model explains; the engine decided every outcome an answer reports. The sandbox's
 * earlier conversations stand beside the thread, each to be opened again as it was shown (GET /chat/sessions/{id}),
 * and a new one can be started at any time.
 */
export function ChatScreen({
  rulesetId = null,
  onOpenRule,
  onOpenCases,
  onOpenCase,
  demoAsked = false,
  onDemoHandled,
}: {
  /** The rule set the workspace is on; without one the seeded rule set is used. */
  rulesetId?: string | null
  onOpenRule: (ruleId: string) => void
  onOpenCases: () => void
  /** Opens a decision's trace, for a cited case this session has run (the spec, section 08). */
  onOpenCase?: (decisionId: string) => void
  /** Step 3 of the guided demo: open on the first scripted question (Brief FR-23). */
  demoAsked?: boolean
  onDemoHandled?: () => void
}) {
  const rulesets = useRulesets()
  const list = rulesets.data ?? []
  // Document 2, chat sessions: a session is opened on a PUBLISHED version. The workspace may be on a draft written a
  // moment ago (demo step 1), so the questions are asked of the first rule set that has a published version
  const target = publishedTarget(list, rulesetId)
  const [open, setOpen] = useState<Open>({ kind: 'new', count: 0 })
  const resumed = useChatSession(open.kind === 'resume' ? open.id : null)
  const startNew = () =>
    setOpen((current) => ({ kind: 'new', count: current.kind === 'new' ? current.count + 1 : 1 }))
  const openConversation = (id: string) => setOpen({ kind: 'resume', id })

  if (rulesets.isPending) {
    return <LoadingRows label="Loading the rule sets" />
  }
  if (open.kind === 'resume') {
    const ruleset = list.find((candidate) => candidate.id === resumed.data?.rulesetId)
    if (resumed.isError || (resumed.data !== undefined && ruleset === undefined)) {
      return (
        <>
          <WorkspaceHeader title="Assistant" />
          <EmptyState
            title="This conversation is no longer available"
            description="Its session is gone; a new conversation asks about the published version."
            action={<Button onClick={startNew}>New conversation</Button>}
          />
        </>
      )
    }
    if (resumed.data === undefined || ruleset === undefined) {
      return (
        <>
          <WorkspaceHeader title="Assistant" />
          <LoadingRows label="Loading the conversation" />
        </>
      )
    }
    return (
      <Conversation
        key={`resume:${open.id}`}
        ruleset={ruleset}
        elsewhere={false}
        target={{ rulesetId: resumed.data.rulesetId, versionNo: resumed.data.versionNo }}
        resumed={resumed.data}
        onOpenRule={onOpenRule}
        onOpenCases={onOpenCases}
        onOpenCase={onOpenCase}
        onOpenConversation={openConversation}
        onNewConversation={startNew}
        demoAsked={demoAsked}
        onDemoHandled={onDemoHandled}
      />
    )
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
      // a new version is a new session, with a conversation of its own, and so is each conversation started anew
      key={`new:${String(open.count)}:${target.ruleset.id}:${String(target.versionNo)}`}
      ruleset={target.ruleset}
      elsewhere={target.elsewhere}
      target={{ rulesetId: target.ruleset.id, versionNo: target.versionNo }}
      resumed={null}
      onOpenRule={onOpenRule}
      onOpenCases={onOpenCases}
      onOpenCase={onOpenCase}
      onOpenConversation={openConversation}
      onNewConversation={startNew}
      demoAsked={demoAsked}
      onDemoHandled={onDemoHandled}
    />
  )
}

interface Opens {
  onOpenRule: (ruleId: string) => void
  /** A cited case: its trace, when this session has run the cases on the answer's version, else the Cases screen. */
  onOpenCase: (caseNo: number | undefined) => void
  onOpenParagraph: (index: number) => void
}

function Conversation({
  ruleset,
  elsewhere,
  target,
  resumed,
  onOpenRule,
  onOpenCases,
  onOpenCase,
  onOpenConversation,
  onNewConversation,
  demoAsked,
  onDemoHandled,
}: {
  ruleset: RulesetSummary
  /** The workspace is on a rule set with no published version; the questions are about this one instead. */
  elsewhere: boolean
  target: ChatTarget
  /** The conversation read back to be resumed, or null for a new one. */
  resumed: ChatConversationResponse | null
  onOpenRule: (ruleId: string) => void
  onOpenCases: () => void
  onOpenCase?: (decisionId: string) => void
  /** Opens an earlier conversation of the sandbox as it was shown. */
  onOpenConversation: (id: string) => void
  /** Starts a new conversation, on the workspace's published version. */
  onNewConversation: () => void
  demoAsked: boolean
  onDemoHandled?: () => void
}) {
  const chat = useChat(target, resumed)
  // the sandbox's conversations, beside the thread on a wide window and in a popover from the toolbar below 1200px
  const sessions = useChatSessions()
  const wide = useWide()
  const [listAnchor, setListAnchor] = useState<HTMLElement | null>(null)
  // "New conversation" in a conversation that has no question yet only asks for the question
  const [focusKey, setFocusKey] = useState(0)
  // the log follows the newest turn: a question asked, and its answer as it arrives, unless the reader has scrolled up
  const scrollRef = useRef<HTMLDivElement>(null)
  const logRef = useRef<HTMLDivElement>(null)
  const seenRef = useRef(0)
  useEffect(() => {
    const scroller = scrollRef.current
    const newest = logRef.current?.lastElementChild
    if (scroller === null || !(newest instanceof HTMLElement)) {
      return
    }
    const asked = chat.exchanges.length !== seenRef.current
    seenRef.current = chat.exchanges.length
    const following = scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight < 120
    if (asked || following) {
      newest.scrollIntoView({ block: 'end' })
    }
  }, [chat.exchanges])
  // this session's run on the version the answers are about, whose cases a cited case chip opens
  const run = useLastRun({ id: target.rulesetId, versionNo: target.versionNo })
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
  const opens: Opens = {
    onOpenRule,
    onOpenCase: (caseNo) => {
      const decided = run.data?.results.find((result) => result.caseNo === caseNo)
      if (decided !== undefined && onOpenCase !== undefined) {
        onOpenCase(decided.id)
      } else {
        onOpenCases()
      }
    },
    onOpenParagraph: setOpenParagraph,
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const asked = question.trim()
    if (asked !== '' && chat.ready && !chat.streaming) {
      chat.ask(asked)
      setQuestion('')
    }
  }

  function startNew() {
    setListAnchor(null)
    if (resumed === null && chat.exchanges.length === 0) {
      setFocusKey((key) => key + 1)
    } else {
      onNewConversation()
    }
  }

  const conversations = (
    <ConversationList
      sessions={sessions.data ?? []}
      currentId={chat.sessionId}
      onOpen={(id) => {
        setListAnchor(null)
        if (id !== chat.sessionId) {
          onOpenConversation(id)
        }
      }}
      onNew={startNew}
    />
  )
  const count = sessions.data?.length ?? 0

  return (
    <>
      <WorkspaceHeader
        title="Assistant"
        provenance={[
          <span key="domain" className="mono">
            {ruleset.domain}
          </span>,
          'answers cite the policy and the rules; the engine decided every outcome they report',
        ]}
        version={<span className="tabular">Version {target.versionNo}</span>}
      />
      <SplitView
        fill
        sideOpen={wide || shownParagraph !== undefined}
        sideLabel={shownParagraph ? `Paragraph ${String(shownParagraph.index)}` : 'Conversations'}
        side={
          <>
            {shownParagraph ? (
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
            ) : null}
            {wide ? (
              <section className="margin__section" aria-label="Conversations">
                <div className="margin__title">
                  <span>Conversations</span>
                  {count > 0 ? <span className="quiet tabular">{count}</span> : null}
                </div>
                {conversations}
              </section>
            ) : null}
          </>
        }
        main={
          <>
            {listAnchor ? (
              // outside the thread, which reads right to left: the popover is English chrome
              <Popover
                anchor={listAnchor}
                label="Conversations"
                align="start"
                onClose={() => setListAnchor(null)}
              >
                <div className="conversations--popover">{conversations}</div>
              </Popover>
            ) : null}
            <section className="thread" dir={chat.language === 'he' ? 'rtl' : 'ltr'}>
              {wide ? null : (
                <div className="toolbar thread__toolbar">
                  <Button
                    variant="quiet"
                    size="sm"
                    aria-expanded={listAnchor !== null}
                    onClick={(event) => setListAnchor(listAnchor ? null : event.currentTarget)}
                  >
                    {count > 0 ? `Conversations · ${String(count)}` : 'Conversations'}
                  </Button>
                  <Button variant="secondary" size="sm" icon="plus" onClick={startNew}>
                    New conversation
                  </Button>
                </div>
              )}
              <div className="thread__scroll" ref={scrollRef}>
                {elsewhere ? (
                  <Note>
                    The rule set on the workspace has no published version yet; the questions are
                    about the seeded one.
                  </Note>
                ) : null}
                <div
                  ref={logRef}
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
                  <Opening
                    ruleset={ruleset}
                    target={target}
                    paragraphs={policy.data === undefined ? null : paragraphs.length}
                    onChoose={setQuestion}
                  />
                ) : null}
              </div>
              <div className="thread__foot">
                <Composer
                  question={question}
                  language={chat.language}
                  busy={chat.streaming}
                  disabled={!chat.ready || chat.streaming || question.trim() === ''}
                  focusKey={focusKey}
                  onChange={setQuestion}
                  onSubmit={submit}
                />
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
            </section>
          </>
        }
      />
    </>
  )
}

/**
 * Before the first question (the spec, section 09, v3.3): what the questions are about, the rule set's name in its own
 * language with the published version's line under it, and the demo's three questions as quiet buttons on ruled rows,
 * numbered in the demo's order, each filling the composer. The counts are the API's: the policy's paragraphs and the
 * version's rules, each left out until it has been read.
 */
function Opening({
  ruleset,
  target,
  paragraphs,
  onChoose,
}: {
  ruleset: RulesetSummary
  target: ChatTarget
  paragraphs: number | null
  onChoose: (question: string) => void
}) {
  const version = useVersion({ id: target.rulesetId, versionNo: target.versionNo })
  const rules = (version.data?.ruleSet as RuleSetDocument | undefined)?.rules.length
  const named: ContentLanguage = directionOfText(ruleset.name) === 'rtl' ? 'he' : 'en'
  return (
    <section className="thread__opening" aria-label="Before the first question">
      <p className="name" {...contentAttributes(named)}>
        {ruleset.name}
      </p>
      <Provenance
        segments={[
          <VersionTag key="version" status="PUBLISHED" versionNo={target.versionNo} />,
          ...(paragraphs === null ? [] : [`${String(paragraphs)} paragraphs`]),
          ...(rules === undefined ? [] : [`${String(rules)} rules`]),
        ]}
      />
      <div className="starters" role="group" aria-labelledby="starters-head">
        <div className="starters__head" id="starters-head">
          The demo&apos;s questions
        </div>
        {SCRIPTED_QUESTIONS.map((scripted, at) => (
          <Button key={scripted} variant="quiet" onClick={() => onChoose(scripted)}>
            <span className="starters__n" aria-hidden="true">
              {at + 1}
            </span>
            <span {...contentAttributes('he')}>{scripted}</span>
          </Button>
        ))}
      </div>
    </section>
  )
}

/**
 * The sandbox's conversations (the spec, section 09, v3.3), newest first as the API lists them: New conversation first,
 * then each conversation named by its first question in its own language, with its version, its count of questions and
 * when it last answered in the mono; the open one is marked.
 */
function ConversationList({
  sessions,
  currentId,
  onOpen,
  onNew,
}: {
  sessions: ChatSessionSummary[]
  currentId: string | null
  onOpen: (id: string) => void
  onNew: () => void
}) {
  return (
    <ul className="conversations">
      <li>
        <button type="button" className="conversation conversation--new" onClick={onNew}>
          <Icon name="plus" />
          New conversation
        </button>
      </li>
      {sessions.map((session) => {
        const asked: ContentLanguage =
          directionOfText(session.firstQuestion) === 'rtl' ? 'he' : 'en'
        return (
          <li key={session.id}>
            <button
              type="button"
              className="conversation"
              aria-current={session.id === currentId ? 'true' : undefined}
              onClick={() => onOpen(session.id)}
            >
              <span className="conversation__q" {...contentAttributes(asked)}>
                {session.firstQuestion}
              </span>
              <span className="conversation__meta">
                {`v${String(session.versionNo)} · ${String(session.turns)} question${session.turns === 1 ? '' : 's'} · ${dateTimeOf(session.lastAt)}`}
              </span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}

/**
 * The composer, the sheet's foot (the spec, section 09, v3.3): one box that holds the question, which grows with it to
 * a few lines, the keys that ask it, the count against the API's limit and the one primary action of the screen.
 * Enter asks; Shift+Enter breaks the line.
 */
function Composer({
  question,
  language,
  busy,
  disabled,
  focusKey,
  onChange,
  onSubmit,
}: {
  question: string
  language: ContentLanguage
  busy: boolean
  disabled: boolean
  /** Counts the requests to take the focus, each one bringing the question's box into focus. */
  focusKey: number
  onChange: (question: string) => void
  onSubmit: (event: FormEvent) => void
}) {
  const fieldRef = useRef<HTMLTextAreaElement>(null)
  useEffect(() => {
    if (focusKey > 0) {
      fieldRef.current?.focus()
    }
  }, [focusKey])
  // the box grows with the question and shrinks back, up to the height the stylesheet caps it at
  useLayoutEffect(() => {
    const element = fieldRef.current
    if (element !== null) {
      element.style.height = 'auto'
      element.style.height = `${String(element.scrollHeight)}px`
    }
  }, [question])
  return (
    <form className="composer" onSubmit={onSubmit}>
      <textarea
        ref={fieldRef}
        className="textarea textarea--he"
        dir="auto"
        aria-label="Question"
        placeholder={PLACEHOLDERS[language]}
        rows={1}
        maxLength={QUESTION_LIMIT}
        value={question}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey) {
            onSubmit(event)
          }
        }}
      />
      <div className="composer__bar">
        <span className="composer__hint">
          <Kbd>↵</Kbd> asks · <Kbd>⇧ ↵</Kbd> breaks the line
        </span>
        <Counter value={question.length} max={QUESTION_LIMIT} />
        <Button type="submit" variant="primary" busy={busy} disabled={disabled}>
          Ask
        </Button>
      </div>
    </form>
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
        <span className="turn__head">
          <b>{PERSON}</b>
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
          <span className="turn__head">
            <b>System</b>
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
  const calls = exchange.steps.length
  return (
    <div className="turn turn--a">
      <span className="turn__who">
        {fixed ? (
          <Actor kind="system" large title="A fixed sentence, not the model's" />
        ) : (
          <Actor kind="model" large title="The model's answer" />
        )}
      </span>
      <span className="turn__head">
        <b>{fixed ? 'System' : 'Model'}</b>
        {!fixed && calls > 0 ? (
          // a count leads the phrase, so the phrase is isolated left to right inside the Hebrew thread
          <span>
            <bdi dir="ltr">{`${String(calls)} tool call${calls === 1 ? '' : 's'}`}</bdi>
          </span>
        ) : null}
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
        onClick={() => opens.onOpenCase(citation.applicationNumber)}
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
