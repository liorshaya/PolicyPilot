import type { ChatCitation, ChatExchange } from './types'

/**
 * The conversation as the screen holds it (Document 2, the chat stream: token events, then citations, usage and
 * done, or error in their place). Each question opens an exchange whose answer grows token by token; the last
 * exchange is the only one the stream ever writes to.
 */

export type ChatAction =
  | { type: 'asked'; question: string }
  | { type: 'token'; text: string }
  | { type: 'citations'; citations: ChatCitation[] }
  | { type: 'done' }
  | { type: 'failed'; code: string }
  | { type: 'retried' }

export function chatReducer(exchanges: ChatExchange[], action: ChatAction): ChatExchange[] {
  if (action.type === 'asked') {
    return [
      ...exchanges,
      {
        id: exchanges.length + 1,
        question: action.question,
        answer: '',
        citations: null,
        status: 'streaming',
      },
    ]
  }
  const last = exchanges[exchanges.length - 1]
  if (last === undefined) {
    return exchanges
  }
  const earlier = exchanges.slice(0, -1)
  switch (action.type) {
    case 'token':
      return [...earlier, { ...last, answer: last.answer + action.text }]
    case 'citations':
      return [...earlier, { ...last, citations: action.citations }]
    case 'done':
      return [...earlier, { ...last, status: 'done' }]
    case 'failed':
      return [...earlier, { ...last, status: 'failed', code: action.code }]
    case 'retried':
      // a chat stream is not resumable (Document 2): the failed exchange is asked again from the start
      return [
        ...earlier,
        { ...last, answer: '', citations: null, status: 'streaming', code: undefined },
      ]
  }
}
