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
  outcome?: 'approve' | 'reject' | 'refer'
  /** A simulation's overrides, as its id names them. */
  detail?: string
}

/** One exchange as the screen holds it: the question, and the answer as far as it has arrived. */
export interface ChatExchange {
  /** The exchange's place in the conversation, from 1; a conversation only grows, so it never changes. */
  id: number
  question: string
  answer: string
  /** Null until the citations event arrives. */
  citations: ChatCitation[] | null
  status: 'streaming' | 'done' | 'failed'
  /** The error code of a failed answer. */
  code?: string
}
