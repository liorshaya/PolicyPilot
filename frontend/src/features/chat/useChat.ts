import { useCallback, useEffect, useReducer, useRef, useState } from 'react'
import { api, ApiError } from '../../api/client'
import { openSse } from '../../api/sse'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { chatReducer } from './chatReducer'
import type { ChatCitation, ChatExchange } from './types'

/**
 * A chat session and its streams (Document 2: POST /chat/sessions opens one bound to a version; each question is an
 * event stream). The hook opens the session for the version it is given, asks one question at a time, and turns
 * each event into the conversation's state. A chat stream is not resumable, so a failed answer is asked again.
 */

export interface ChatTarget {
  rulesetId: string
  versionNo: number
}

export interface Chat {
  /** Whether the session is open and a question may be asked. */
  ready: boolean
  /** Why the session could not be opened, as the API's error code. */
  openFailure: string | null
  language: ContentLanguage
  exchanges: ChatExchange[]
  streaming: boolean
  ask: (question: string) => void
  retry: () => void
}

export function useChat(target: ChatTarget): Chat {
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [language, setLanguage] = useState<ContentLanguage>('en')
  const [openFailure, setOpenFailure] = useState<string | null>(null)
  const [exchanges, dispatch] = useReducer(chatReducer, [])
  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    let current = true
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
    return () => {
      current = false
      abortRef.current?.abort()
    }
  }, [target.rulesetId, target.versionNo])

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
            if (event.event === 'token') {
              dispatch({ type: 'token', text: data.text as string })
            } else if (event.event === 'citations') {
              dispatch({ type: 'citations', citations: data.citations as ChatCitation[] })
            } else if (event.event === 'done') {
              dispatch({ type: 'done' })
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
    [sessionId],
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
    openFailure,
    language,
    exchanges,
    streaming: exchanges[exchanges.length - 1]?.status === 'streaming',
    ask,
    retry,
  }
}
