import { useState, type FormEvent } from 'react'
import { SCRIPTED_QUESTIONS } from '../demo/steps'
import { useDemoStep } from '../demo/useDemoStep'
import { publishedTarget } from '../../api/published'
import { usePolicy, useRulesets } from '../../api/queries'
import type { RulesetSummary } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Button } from '../../shared/ui/Button'
import { Field } from '../../shared/ui/Field'
import { EmptyState, LoadingRows } from '../../shared/ui/States'
import { failureText } from './failures'
import { markerLabel, segments } from './markers'
import type { ChatCitation, ChatExchange } from './types'
import { useChat, type ChatTarget } from './useChat'
import './ChatScreen.css'

/**
 * The assistant (Document 2, Flow 3; Work Plan day 9): questions about a published version, answered with citations
 * the API has checked. A paragraph chip opens the paragraph, a rule chip opens the rule beside its source, and a
 * decision chip leads to the cases. The interface is English; an answer takes the direction of the rule set's
 * language. The model explains; the engine decided every outcome an answer reports.
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
  const policy = usePolicy(ruleset.policyId ?? null)
  const [question, setQuestion] = useState('')
  const [openParagraph, setOpenParagraph] = useState<number | null>(null)
  // step 3 of the demo types the first scripted question; the presenter presses Send and asks the next two
  useDemoStep(demoAsked, () => setQuestion(SCRIPTED_QUESTIONS[0]), onDemoHandled)
  const paragraphs = policy.data?.versions?.[policy.data.versions.length - 1]?.paragraphs ?? []
  const shownParagraph = paragraphs.find((paragraph) => paragraph.index === openParagraph)

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
        context={
          <>
            <bdi dir="auto">{ruleset.name}</bdi> · answers cite the policy and the rules; the engine
            decided every outcome they report
          </>
        }
        version={<span className="tabular">Version {target.versionNo}</span>}
      />
      <div className="chat">
        {elsewhere ? (
          <p className="chat__elsewhere" role="note">
            The rule set on the workspace has no published version yet; the questions are about the
            seeded one.
          </p>
        ) : null}
        <section className="chat__conversation" aria-label="Conversation">
          {chat.openFailure ? (
            <p className="chat__failure" role="alert">
              {failureText(chat.openFailure)}
            </p>
          ) : null}
          {chat.exchanges.length === 0 && !chat.openFailure ? (
            <p className="chat__intro">
              Ask about the policy, a rule, or an application by its number. Every statement links
              to its source.
            </p>
          ) : null}
          <ol className="chat__log" aria-live="polite">
            {chat.exchanges.map((exchange) => (
              <li key={exchange.id} className="chat__exchange">
                <p className="chat__question" dir="auto">
                  {exchange.question}
                </p>
                <Answer
                  exchange={exchange}
                  language={chat.language}
                  onOpenRule={onOpenRule}
                  onOpenCases={onOpenCases}
                  onOpenParagraph={setOpenParagraph}
                />
                {exchange.status === 'failed' ? (
                  <div className="chat__failure" role="alert">
                    <span>{failureText(exchange.code)}</span>
                    <Button variant="secondary" onClick={chat.retry}>
                      Try again
                    </Button>
                  </div>
                ) : null}
              </li>
            ))}
          </ol>
          <form className="chat__composer" onSubmit={submit}>
            <Field label="Question" htmlFor="chat-question">
              <textarea
                id="chat-question"
                className="chat__input"
                dir="auto"
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
            </Field>
            <Button
              type="submit"
              variant="primary"
              loading={chat.streaming}
              disabled={!chat.ready || chat.streaming || question.trim() === ''}
            >
              Ask
            </Button>
          </form>
        </section>
        {shownParagraph ? (
          <aside className="chat__source" aria-label={`Paragraph ${String(shownParagraph.index)}`}>
            <header className="chat__source-header">
              <span>Paragraph {shownParagraph.index}</span>
              <Button variant="ghost" onClick={() => setOpenParagraph(null)}>
                Close
              </Button>
            </header>
            <p
              className="chat__source-text"
              {...contentAttributes(policyLanguage(policy.data?.language))}
            >
              {shownParagraph.text}
            </p>
          </aside>
        ) : null}
      </div>
    </>
  )
}

function Answer({
  exchange,
  language,
  onOpenRule,
  onOpenCases,
  onOpenParagraph,
}: {
  exchange: ChatExchange
  language: ContentLanguage
  onOpenRule: (ruleId: string) => void
  onOpenCases: () => void
  onOpenParagraph: (index: number) => void
}) {
  if (exchange.status === 'streaming' && exchange.answer === '') {
    return <p className="chat__thinking">Looking it up…</p>
  }
  return (
    <p
      className="chat__answer"
      {...contentAttributes(language)}
      aria-busy={exchange.status === 'streaming'}
    >
      {segments(exchange.answer).map((segment) => {
        if (segment.kind === 'text') {
          return <span key={segment.at}>{segment.text}</span>
        }
        const citation = exchange.citations?.find((cited) => cited.id === segment.id)
        // once the citations have arrived, a marker the API did not cite is not shown
        if (exchange.citations !== null && citation === undefined) {
          return null
        }
        return (
          <CitationChip
            key={segment.at}
            id={segment.id}
            citation={citation}
            onOpenRule={onOpenRule}
            onOpenCases={onOpenCases}
            onOpenParagraph={onOpenParagraph}
          />
        )
      })}
    </p>
  )
}

function CitationChip({
  id,
  citation,
  onOpenRule,
  onOpenCases,
  onOpenParagraph,
}: {
  id: string
  citation: ChatCitation | undefined
  onOpenRule: (ruleId: string) => void
  onOpenCases: () => void
  onOpenParagraph: (index: number) => void
}) {
  const label = markerLabel(id)
  const title = citation ? chipTitle(citation) : label
  if (citation?.kind === 'RULE' && citation.ruleId) {
    const ruleId = citation.ruleId
    return (
      <button
        type="button"
        className="chat__chip"
        dir="ltr"
        title={title}
        onClick={() => onOpenRule(ruleId)}
      >
        {label}
      </button>
    )
  }
  if (citation?.kind === 'PARAGRAPH' && citation.paragraph !== undefined) {
    const paragraph = citation.paragraph
    return (
      <button
        type="button"
        className="chat__chip"
        dir="ltr"
        title={title}
        onClick={() => onOpenParagraph(paragraph)}
      >
        {label}
      </button>
    )
  }
  if (citation?.kind === 'DECISION') {
    return (
      <button type="button" className="chat__chip" dir="ltr" title={title} onClick={onOpenCases}>
        {label}
      </button>
    )
  }
  return (
    <span className="chat__chip chat__chip--static" dir="ltr" title={title}>
      {label}
    </span>
  )
}

/** What a chip says on hover: the source it stands for, and for a decision or a simulation its outcome. */
function chipTitle(citation: ChatCitation): string {
  switch (citation.kind) {
    case 'PARAGRAPH':
      return `Paragraph ${String(citation.paragraph)} of the policy`
    case 'RULE':
      return `${citation.ruleId ?? ''} · ${citation.label ?? ''}`
    case 'DECISION':
      return `Application ${String(citation.applicationNumber)} · ${citation.outcome ?? ''}, as the engine decided`
    case 'SIMULATION':
      return `If ${citation.detail ?? ''}: ${citation.outcome ?? ''}, as the engine simulated`
  }
}

function policyLanguage(language: string | undefined): ContentLanguage {
  return language === 'he' ? 'he' : 'en'
}
