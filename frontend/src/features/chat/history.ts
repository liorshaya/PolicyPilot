import type { ChatConversationResponse, ChatConversationTurn } from '../../api/types'
import type { ChatExchange, FixedAnswer } from './types'

/**
 * A conversation read back from the API (Document 2, GET /chat/sessions/{id}) as the screen holds it: each turn an
 * exchange that is done, its answer as it was shown, its citations as the citations event sent them, its tool calls as
 * the tool events reported them, numbered from 1, and the fixed sentence it is. Nothing is computed here: what the
 * thread showed the first time is what it shows again.
 */
export function exchangesOf(conversation: ChatConversationResponse): ChatExchange[] {
  return conversation.turns.map(exchangeOf)
}

function exchangeOf(turn: ChatConversationTurn): ChatExchange {
  return {
    id: turn.turn,
    question: turn.question,
    steps: turn.toolCalls.map((call, at) => ({ ...call, at: at + 1 })),
    answer: turn.answer,
    citations: turn.citations,
    fixed: (turn.fixed ?? null) as FixedAnswer,
    status: 'done',
  }
}
