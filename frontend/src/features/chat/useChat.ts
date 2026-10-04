import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useReducer, useRef, useState } from 'react'
import { api, ApiError } from '../../api/client'
import { keys } from '../../api/queries'
import { openSse } from '../../api/sse'
import type { ChatConversationResponse } from '../../api/types'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { chatReducer } from './chatReducer'
import { exchangesOf } from './history'
import type { ChatCitation, ChatExchange, ChatToolCall, FixedAnswer } from './types'

/**
 * A chat session and its streams (Document 2: POST /chat/sessions opens one bound to a version; each question is an
 * event stream of tool calls, tokens, citations and the end). The hook opens the session for the version it is given,
 * asks one question at a time, and turns each event into the conversation's state. A chat stream is not resumable, so
 * a failed answer is asked again. Given a conversation read back from the API (GET /chat/sessions/{id}), the hook
 * opens no session of its own: the thread starts as the conversation was shown and asks on in its session.
 */

export interface ChatTarget {
  rulesetId: string
  versionNo: number
}

export interface Chat {
  /** Whether the session is open and a question may be asked. */
  ready: boolean
  /** The session's id once it is open, which the list of conversations marks as the open one. */
  sessionId: string | null
  /** Why the session could not be opened, as the API's error code. */
  openFailure: string | null
  /** The language the session answers in, its version's own; null until the session is open. */
  language: ContentLanguage | null
  exchanges: ChatExchange[]
  streaming: boolean
  ask: (question: string) => void
  retry: () => void
}

export function useChat(target: ChatTarget, resumed: ChatConversationResponse | null = null): Chat {
  const client = useQueryClient()
  const [sessionId, setSessionId] = useState<string | null>(() => resumed?.id ?? null)
  // a session that is still to open has not said its language: the screen reads it from the version meanwhile
  const [language, setLanguage] = useState<ContentLanguage | null>(() =>
    resumed === null ? null : resumed.language === 'he' ? 'he' : 'en',
  )
  const [openFailure, setOpenFailure] = useState<string | null>(null)
  const [exchanges, dispatch] = useReducer(chatReducer, resumed, (conversation) =>
    conversation === null ? [] : exchangesOf(conversation),
  )
  const abortRef = useRef<AbortController | null>(null)
  const resumedId = resumed?.id ?? null

  useEffect(() => {
    let current = true
    if (resumedId === null) {
      api
        .openChat(target.rulesetId, target.versionNo)
        .then((session) => {
          if (current) {
            setSessionId(session.id ?? null)
            setLanguage(session.language === 'he' ? 'he' : 'en')
          }
        })
        .catch((error: unknown) => {
          if (current) {
            setOpenFailure(error instanceof ApiError ? error.code : 'PROVIDER_UNAVAILABLE')
          }
        })
    }
    return () => {
      current = false
      abortRef.current?.abort()
    }
  }, [target.rulesetId, target.versionNo, resumedId])

  const stream = useCallback(
    (question: string) => {
      if (sessionId === null) {
        return
      }
      const controller = new AbortController()
      abortRef.current = controller
      void (async () => {
        try {
          for await (const event of openSse(`/api/v1/chat/sessions/${sessionId}/messages`, {
            body: { question },
            signal: controller.signal,
          })) {
            const data = JSON.parse(event.data) as Record<string, unknown>
            if (event.event === 'tool') {
              dispatch({ type: 'tool', call: data as unknown as ChatToolCall })
            } else if (event.event === 'token') {
              dispatch({ type: 'token', text: data.text as string })
            } else if (event.event === 'citations') {
              dispatch({ type: 'citations', citations: data.citations as ChatCitation[] })
            } else if (event.event === 'done') {
              dispatch({ type: 'done', fixed: (data.fixed ?? null) as FixedAnswer })
              // the conversation holds one turn more, or is a conversation at last: the list reads it again
              void client.invalidateQueries({ queryKey: keys.chatSessions })
            } else if (event.event === 'error') {
              dispatch({ type: 'failed', code: data.code as string })
            }
          }
        } catch (error: unknown) {
          // an aborted stream is the screen's own doing; anything else is the API, the provider or the network
          if (!controller.signal.aborted) {
            dispatch({
              type: 'failed',
              code: error instanceof ApiError ? error.code : 'PROVIDER_UNAVAILABLE',
            })
          }
        }
      })()
    },
    [sessionId, client],
  )

  const ask = useCallback(
    (question: string) => {
      dispatch({ type: 'asked', question })
      stream(question)
    },
    [stream],
  )

  const retry = useCallback(() => {
    const last = exchanges[exchanges.length - 1]
    if (last?.status === 'failed') {
      dispatch({ type: 'retried' })
      stream(last.question)
    }
  }, [exchanges, stream])

  return {
    ready: sessionId !== null,
    sessionId,
    openFailure,
    language,
    exchanges,
    streaming: exchanges[exchanges.length - 1]?.status === 'streaming',
    ask,
    retry,
  }
}
