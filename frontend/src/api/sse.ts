import { API_BASE_URL } from './config'
import { CLIENT_HEADER } from './auth'
import { ApiError } from './client'
import type { ErrorEnvelope } from './types'

/**
 * Server-sent events over {@code fetch} (Document 2, Frontend Architecture, key decision 2: not EventSource, which
 * cannot send a POST body, headers or credentials). Events are parsed as the stream arrives, resumed with
 * {@code Last-Event-ID} where the API allows it (generation and change streams), and the caller can abort.
 */

/** One event of a stream: its name, its JSON payload as text, and its id when the server gave one. */
export interface SseEvent {
  event: string
  data: string
  id?: string
}

export interface SseOptions {
  /** The request body; generation and chat both post one. */
  body?: unknown
  /** The id of the last event already seen, for a stream the API can resume. */
  lastEventId?: string
  /** Aborts the stream; the hook that owns it aborts on unmount. */
  signal?: AbortSignal
}

/** The event fields a server may send, as the specification names them. */
const FIELDS = ['event', 'data', 'id'] as const

/**
 * Opens the stream and yields its events in order. The caller reads it with {@code for await}, which ends when the
 * server closes the stream or the signal aborts.
 */
export async function* openSse(path: string, options: SseOptions = {}): AsyncGenerator<SseEvent> {
  const headers: Record<string, string> = { [CLIENT_HEADER]: 'web', Accept: 'text/event-stream' }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.lastEventId !== undefined) {
    headers['Last-Event-ID'] = options.lastEventId
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.body === undefined ? 'GET' : 'POST',
    credentials: 'include',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  })
  if (!response.ok) {
    // a refusal before the stream opened carries the error envelope, so the caller can tell a 409 from a 429
    throw new ApiError(response.status, await envelopeOf(response))
  }
  if (!response.body) {
    throw new Error(`stream failed with ${response.status}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        const event = parseEvent(block)
        if (event) {
          yield event
        }
        boundary = buffer.indexOf('\n\n')
      }
    }
    const last = parseEvent(buffer)
    if (last) {
      yield last
    }
  } finally {
    try {
      await reader.cancel()
    } catch {
      // the stream was already closed by the server or by an abort; there is nothing left to release
    }
  }
}

/** One block of lines into an event; a block with no data is a comment or a keep-alive and is dropped. */
export function parseEvent(block: string): SseEvent | null {
  let event = 'message'
  let id: string | undefined
  const data: string[] = []
  for (const line of block.split('\n')) {
    // a line is "field: value"; a line that starts with the colon is a comment, and one without it is ignored
    const separator = line.indexOf(':')
    if (separator <= 0) {
      continue
    }
    const field = line.slice(0, separator)
    const value = line
      .slice(separator + 1)
      .replace(/^ /, '')
      .trimEnd()
    if (!FIELDS.includes(field as (typeof FIELDS)[number])) {
      continue
    }
    if (field === 'event') {
      event = value
    } else if (field === 'data') {
      data.push(value)
    } else {
      id = value
    }
  }
  if (data.length === 0) {
    return null
  }
  return id === undefined ? { event, data: data.join('\n') } : { event, data: data.join('\n'), id }
}

/** The error envelope of a refused request, or null when the body was not one. */
async function envelopeOf(response: Response): Promise<ErrorEnvelope | null> {
  try {
    return (await response.json()) as ErrorEnvelope
  } catch {
    return null
  }
}
