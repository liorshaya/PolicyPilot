/**
 * The chat's events as the API sends them (Document 2, POST /chat/sessions/{id}/messages). SSE payloads are not in
 * the OpenAPI document, so they are typed here from the API's own records, as the decision object is in api/types.
 */

/** A source an answer cited: a paragraph, a rule, a decision or a simulation (Document 4, Marker resolution). */
export interface ChatCitation {
  id: string
  kind: 'PARAGRAPH' | 'RULE' | 'DECISION' | 'SIMULATION'
  paragraph?: number
  ruleId?: string
  label?: string
  applicationNumber?: number
  outcome?: 'approve' | 'reject' | 'refer' | 'error'
  /** A simulation's overrides, as its id names them. */
  detail?: string
}

/**
 * One tool call as the stream reports it when it ends, before the answer's first token (Document 2, the `tool` event):
 * the tool, what it ran on, the session's version and the call's time; for a decision or a simulation what the engine
 * decided; a refused call names its reason and has no outcome.
 */
export interface ChatToolCall {
  tool: 'getDecision' | 'simulate' | 'getDecisionStats' | 'listRules'
  applicationNumber: number | null
  /** A simulation's overrides as its citation id writes them: `has_guarantor=true`. */
  overrides: string | null
  tag: string | null
  versionNo: number
  micros: number
  outcome: 'approve' | 'reject' | 'refer' | 'error' | null
  decidingRuleId: string | null
  flags: string[]
  refused: 'not_found' | 'invalid_arguments' | 'limit' | null
}

/** A tool call as the exchange keeps it: the call, and its place among the answer's calls, from 1. */
export type ChatStep = ChatToolCall & { at: number }

/** The fixed sentence an answer is (Document 4), which the system says and no model wrote, or null. */
export type FixedAnswer = 'not_covered' | 'tool_limit' | null

/** One exchange as the screen holds it: the question, and the answer as far as it has arrived. */
export interface ChatExchange {
  /** The exchange's place in the conversation, from 1; a conversation only grows, so it never changes. */
  id: number
  question: string
  /** The tool calls the answer made, in the order they ended. */
  steps: ChatStep[]
  answer: string
  /** Null until the citations event arrives. */
  citations: ChatCitation[] | null
  /** Null until the answer is done, and for an answer a model wrote. */
  fixed: FixedAnswer
  status: 'streaming' | 'done' | 'failed'
  /** The error code of a failed answer. */
  code?: string
}
