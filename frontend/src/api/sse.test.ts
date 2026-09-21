import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './client'
import { openSse, parseEvent, type SseEvent } from './sse'

/**
 * The streaming helper (Document 6, Frontend Test Design: "useSse: token accumulation, event ordering,
 * Last-Event-ID resume, abort on unmount, error states"; 100% coverage required). The stream is a scripted
 * ReadableStream, so the test never needs a server.
 */

function streamOf(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  return new ReadableStream({
    start(controller) {
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(chunk))
      }
      controller.close()
    },
  })
}

function respondWith(stream: ReadableStream<Uint8Array> | null, status = 200, envelope?: unknown) {
  const fetchMock = vi.fn(async () =>
    Promise.resolve({
      ok: status < 400,
      status,
      body: stream,
      json: async () =>
        envelope === undefined
          ? Promise.reject(new SyntaxError('no JSON'))
          : Promise.resolve(envelope),
    } as unknown as Response),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

async function collect(path: string, options?: Parameters<typeof openSse>[1]): Promise<SseEvent[]> {
  const events: SseEvent[] = []
  for await (const event of openSse(path, options)) {
    events.push(event)
  }
  return events
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('openSse', () => {
  it('yields the events of the stream in order', async () => {
    respondWith(streamOf('event: token\ndata: one\n\n', 'event: token\ndata: two\n\n'))

    const events = await collect('/api/v1/chat/sessions/1/messages')

    expect(events).toEqual([
      { event: 'token', data: 'one' },
      { event: 'token', data: 'two' },
    ])
  })

  it('joins an event split across chunks', async () => {
    respondWith(streamOf('event: cit', 'ations\ndata: {"ids":', '[1]}\n\n'))

    const events = await collect('/api/v1/chat/sessions/1/messages')

    expect(events).toEqual([{ event: 'citations', data: '{"ids":[1]}' }])
  })

  it('yields a last event that arrives without a blank line', async () => {
    respondWith(streamOf('event: done\ndata: {}\n'))

    const events = await collect('/api/v1/chat/sessions/1/messages')

    expect(events).toEqual([{ event: 'done', data: '{}' }])
  })

  it('posts the body and sends the client header and the credentials', async () => {
    const fetchMock = respondWith(streamOf('event: parsing\ndata: {}\n\n'))

    await collect('/api/v1/policies/7/rulesets', { body: { policyId: '7' } })

    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(init.method).toBe('POST')
    expect(init.credentials).toBe('include')
    expect(init.body).toBe('{"policyId":"7"}')
    expect(init.headers).toMatchObject({
      'X-PolicyPilot-Client': 'web',
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    })
  })

  it('opens a stream without a body as a GET and sends no content type', async () => {
    const fetchMock = respondWith(streamOf('event: token\ndata: hi\n\n'))

    await collect('/api/v1/stream')

    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(init.method).toBe('GET')
    expect(init.body).toBeUndefined()
    expect(init.headers).not.toHaveProperty('Content-Type')
  })

  it('resumes with Last-Event-ID when the caller knows where it stopped', async () => {
    const fetchMock = respondWith(streamOf('id: 4\nevent: token\ndata: four\n\n'))

    const events = await collect('/api/v1/policies/7/rulesets', { lastEventId: '3' })

    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(init.headers).toMatchObject({ 'Last-Event-ID': '3' })
    expect(events).toEqual([{ event: 'token', data: 'four', id: '4' }])
  })

  it('passes the abort signal to the request', async () => {
    const fetchMock = respondWith(streamOf('event: token\ndata: hi\n\n'))
    const controller = new AbortController()

    await collect('/api/v1/stream', { signal: controller.signal })

    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(init.signal).toBe(controller.signal)
  })

  it('cancels the reader when the caller stops reading', async () => {
    respondWith(streamOf('event: token\ndata: one\n\n', 'event: token\ndata: two\n\n'))

    const stream = openSse('/api/v1/stream')
    const first = await stream.next()
    await stream.return(undefined)

    expect(first.value).toEqual({ event: 'token', data: 'one' })
  })

  it('skips a keep-alive block between events', async () => {
    respondWith(
      streamOf('event: token\ndata: one\n\n', ': keep-alive\n\n', 'event: done\ndata: {}\n\n'),
    )

    const events = await collect('/api/v1/stream')

    expect(events).toEqual([
      { event: 'token', data: 'one' },
      { event: 'done', data: '{}' },
    ])
  })

  it('ends quietly when releasing the reader fails', async () => {
    const reader = {
      read: vi
        .fn()
        .mockResolvedValueOnce({
          done: false,
          value: new TextEncoder().encode('event: token\ndata: one\n\n'),
        })
        .mockResolvedValue({ done: true, value: undefined }),
      cancel: vi.fn().mockRejectedValue(new Error('already closed')),
    }
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        Promise.resolve({
          ok: true,
          status: 200,
          body: { getReader: () => reader },
        } as unknown as Response),
      ),
    )

    const events = await collect('/api/v1/stream')

    expect(events).toEqual([{ event: 'token', data: 'one' }])
    expect(reader.cancel).toHaveBeenCalledOnce()
  })

  it('fails with the error envelope when the API refuses the stream', async () => {
    respondWith(null, 409, { code: 'VERSION_STATUS_CONFLICT', message: 'not ready', details: [] })

    const refusal = await collect('/api/v1/stream').catch((error: unknown) => error)

    expect(refusal).toBeInstanceOf(ApiError)
    expect((refusal as ApiError).status).toBe(409)
    expect((refusal as ApiError).code).toBe('VERSION_STATUS_CONFLICT')
  })

  it('fails with the status alone when the refusal carries no envelope', async () => {
    respondWith(null, 429)

    const refusal = await collect('/api/v1/stream').catch((error: unknown) => error)

    expect((refusal as ApiError).code).toBe('HTTP_429')
  })

  it('fails when the response carries no body', async () => {
    respondWith(null, 200)

    await expect(collect('/api/v1/stream')).rejects.toThrow('stream failed with 200')
  })
})

describe('parseEvent', () => {
  it('defaults the event name to message', () => {
    expect(parseEvent('data: hello')).toEqual({ event: 'message', data: 'hello' })
  })

  it('joins the data lines of one event', () => {
    expect(parseEvent('event: usage\ndata: {\ndata: }')).toEqual({ event: 'usage', data: '{\n}' })
  })

  it('drops a block with no data, such as a keep-alive comment', () => {
    expect(parseEvent(': keep-alive')).toBeNull()
    expect(parseEvent('retry: 3000')).toBeNull()
    expect(parseEvent('')).toBeNull()
  })
})
