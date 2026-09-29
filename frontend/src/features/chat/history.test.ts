import { describe, expect, it } from 'vitest'
import type { ChatConversationResponse } from '../../api/types'
import { exchangesOf } from './history'
import type { ChatToolCall } from './types'

/**
 * A conversation read back (Document 2, GET /chat/sessions/{id}) becomes the exchanges the thread showed the first
 * time: nothing recomputed, the tool calls numbered as the stream numbered them, the fixed sentence as the API marked
 * it. The values are the API's own shapes, as ChatStreamIT reads them back.
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

const conversation: ChatConversationResponse = {
  id: '0f4c1c9e-0000-4000-8000-0000000000d1',
  rulesetId: '0f4c1c9e-0000-4000-8000-0000000000b1',
  versionNo: 1,
  language: 'he',
  openedAt: '2026-09-29T09:00:00Z',
  turns: [
    {
      turn: 1,
      question: 'למה בקשה מספר 17 הופנתה לבדיקה?',
      askedAt: '2026-09-29T09:01:00Z',
      answer: 'בקשה 17 הופנתה לבדיקת חתם.[[d:17]] [[p:7]]',
      answeredAt: '2026-09-29T09:01:04Z',
      citations: [
        { id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer' },
        { id: 'p:7', kind: 'PARAGRAPH', paragraph: 7 },
      ],
      toolCalls: [lookup, whatIf],
      fixed: null,
    },
    {
      turn: 2,
      question: 'מהי הריבית המקסימלית שהבנק רשאי לגבות?',
      askedAt: '2026-09-29T09:02:00Z',
      answer: 'המסמכים אינם עוסקים בשאלה הזו; אפשר לשאול על כלל, על סעיף או על מספר בקשה.',
      answeredAt: '2026-09-29T09:02:01Z',
      citations: [],
      toolCalls: [],
      fixed: 'not_covered',
    },
  ],
}

describe('exchangesOf', () => {
  it('turns each turn into a done exchange, its tool calls numbered from 1, its citations and its fixed sentence kept', () => {
    expect(exchangesOf(conversation)).toEqual([
      {
        id: 1,
        question: 'למה בקשה מספר 17 הופנתה לבדיקה?',
        steps: [
          { ...lookup, at: 1 },
          { ...whatIf, at: 2 },
        ],
        answer: 'בקשה 17 הופנתה לבדיקת חתם.[[d:17]] [[p:7]]',
        citations: [
          { id: 'd:17', kind: 'DECISION', applicationNumber: 17, outcome: 'refer' },
          { id: 'p:7', kind: 'PARAGRAPH', paragraph: 7 },
        ],
        fixed: null,
        status: 'done',
      },
      {
        id: 2,
        question: 'מהי הריבית המקסימלית שהבנק רשאי לגבות?',
        steps: [],
        answer: 'המסמכים אינם עוסקים בשאלה הזו; אפשר לשאול על כלל, על סעיף או על מספר בקשה.',
        citations: [],
        fixed: 'not_covered',
        status: 'done',
      },
    ])
  })

  it('gives a conversation with no turn yet no exchange', () => {
    expect(exchangesOf({ ...conversation, turns: [] })).toEqual([])
  })
})
