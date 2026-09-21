import { describe, expect, it } from 'vitest'
import { chatReducer } from './chatReducer'
import type { ChatExchange } from './types'

/**
 * The conversation state (Document 2: token events, then citations, usage and done, or error in their place;
 * Document 6, Frontend: the chat reducer). Every expectation names the exact state an event leaves.
 */
describe('chatReducer', () => {
  const asked = chatReducer([], { type: 'asked', question: 'מהי תקופת ההחזר המקסימלית?' })

  it('opens an exchange for a question, with an empty answer that is streaming', () => {
    expect(asked).toEqual([
      {
        id: 1,
        question: 'מהי תקופת ההחזר המקסימלית?',
        answer: '',
        citations: null,
        status: 'streaming',
      },
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

    expect(chatReducer(cited, { type: 'done' })).toEqual([
      {
        id: 1,
        question: 'מהי תקופת ההחזר המקסימלית?',
        answer: 'The term is 84 months.[[p:2]]',
        citations: [{ id: 'p:2', kind: 'PARAGRAPH', paragraph: 2 }],
        status: 'done',
      },
    ])
  })

  it('writes only to the last exchange', () => {
    const done = chatReducer(chatReducer(asked, { type: 'token', text: 'first' }), { type: 'done' })
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
    const failed = chatReducer(chatReducer(asked, { type: 'token', text: 'half' }), {
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
        answer: '',
        citations: null,
        status: 'streaming',
      },
    ])
  })

  it('ignores an event that arrives with no exchange open', () => {
    expect(chatReducer([], { type: 'token', text: 'stray' })).toEqual([])
  })
})
