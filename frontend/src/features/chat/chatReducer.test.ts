import { describe, expect, it } from 'vitest'
import { chatReducer } from './chatReducer'
import type { ChatExchange, ChatToolCall } from './types'

/**
 * Two tool calls as the chat stream reports them (Document 2, the tool event): application 17 read as the Python
 * reference decides it (refer by R-330, no flag), and the same with a guarantor (approve by R-900, flagged).
 */
const lookup: ChatToolCall = {
  tool: 'getDecision',
  applicationNumber: 17,
  overrides: null,
  tag: null,
  versionNo: 1,
  micros: 1240,
  outcome: 'refer',
  decidingRuleId: 'R-330',
  flags: [],
  refused: null,
}
const whatIf: ChatToolCall = {
  ...lookup,
  tool: 'simulate',
  overrides: 'has_guarantor=true',
  micros: 58,
  outcome: 'approve',
  decidingRuleId: 'R-900',
  flags: ['STABLE_INCOME_MANUAL_CHECK'],
}

/**
 * The conversation state (Document 2: tool events, token events, then citations, usage and done, or error in their place;
 * Document 6, Frontend: the chat reducer). Every expectation names the exact state an event leaves.
 */
describe('chatReducer', () => {
  const asked = chatReducer([], { type: 'asked', question: 'מהי תקופת ההחזר המקסימלית?' })

  it('opens an exchange for a question, with an empty answer that is streaming', () => {
    expect(asked).toEqual([
      {
        id: 1,
        question: 'מהי תקופת ההחזר המקסימלית?',
        steps: [],
        answer: '',
        citations: null,
        fixed: null,
        status: 'streaming',
      },
    ])
  })

  // Document 2, the tool event (2026-09-28): each tool call as it ends, before the answer's first token
  it('keeps each tool call the answer reports, in the order they ended', () => {
    const first = chatReducer(asked, { type: 'tool', call: lookup })
    const both = chatReducer(first, { type: 'tool', call: whatIf })

    expect(both[0]!.steps).toEqual([
      { ...lookup, at: 1 },
      { ...whatIf, at: 2 },
    ])
  })

  it('grows the answer token by token, then takes the citations and ends done', () => {
    const tokens = ['The term ', 'is 84 months.', '[[p:2]]'].reduce<ChatExchange[]>(
      (state, text) => chatReducer(state, { type: 'token', text }),
      asked,
    )
    const cited = chatReducer(tokens, {
      type: 'citations',
      citations: [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }],
    })

    expect(chatReducer(cited, { type: 'done', fixed: null })).toEqual([
      {
        id: 1,
        question: 'מהי תקופת ההחזר המקסימלית?',
        steps: [],
        answer: 'The term is 84 months.[[p:2]]',
        citations: [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }],
        fixed: null,
        status: 'done',
      },
    ])
  })

  // Document 2, the done event: the fixed sentence the answer is, not_covered or tool_limit, or null
  it('records the fixed sentence an answer is when it ends', () => {
    expect(chatReducer(asked, { type: 'done', fixed: 'not_covered' })[0]).toMatchObject({
      status: 'done',
      fixed: 'not_covered',
    })
  })

  it('writes only to the last exchange', () => {
    const done = chatReducer(chatReducer(asked, { type: 'token', text: 'first' }), {
      type: 'done',
      fixed: null,
    })
    const second = chatReducer(chatReducer(done, { type: 'asked', question: 'and?' }), {
      type: 'token',
      text: 'second',
    })

    expect(second.map((exchange) => [exchange.id, exchange.answer])).toEqual([
      [1, 'first'],
      [2, 'second'],
    ])
  })

  it('records the code of a failed answer, and a retry starts that answer again', () => {
    const called = chatReducer(asked, { type: 'tool', call: lookup })
    const failed = chatReducer(chatReducer(called, { type: 'token', text: 'half' }), {
      type: 'failed',
      code: 'PROVIDER_UNAVAILABLE',
    })
    expect(failed[0]).toMatchObject({
      status: 'failed',
      code: 'PROVIDER_UNAVAILABLE',
      answer: 'half',
    })

    expect(chatReducer(failed, { type: 'retried' })).toEqual([
      {
        id: 1,
        question: 'מהי תקופת ההחזר המקסימלית?',
        steps: [],
        answer: '',
        citations: null,
        fixed: null,
        status: 'streaming',
      },
    ])
  })

  it('ignores an event that arrives with no exchange open', () => {
    expect(chatReducer([], { type: 'token', text: 'stray' })).toEqual([])
  })
})
