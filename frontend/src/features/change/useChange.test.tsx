import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import {
  approvedDecision,
  PROPOSAL_ID,
  rejectedDecision,
  rt04,
  rt04Events,
  scriptedEvents,
  scriptedProposalEvent,
  scriptedRequest,
} from '../../test/fixtures/change'
import { eventStream } from '../../test/fixtures/changeRequest'
import { SEEDED_RULESET_ID } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { useChange } from './useChange'

// @requirement FR-17
// @requirement FR-18
// @requirement FR-19

/**
 * A change request from the screen's side (Document 2, API Surface: POST /rulesets/{id}/versions/{no}/changes as a
 * stream, then POST /changes/{id}/approve or /reject with the analyst's note). MSW answers with the event streams
 * the API sends, built from the committed fixtures; the text is the scripted request of the demo.
 */

const BASE = 'http://localhost:8080/api/v1'
const TEXT = scriptedRequest.text.he
const TARGET = { rulesetId: SEEDED_RULESET_ID, versionNo: 1 }

const streamed = (events: [string, unknown][]) =>
  new HttpResponse(eventStream(events), { headers: { 'Content-Type': 'text/event-stream' } })

function renderChange() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return renderHook(() => useChange(TARGET), { wrapper })
}

async function proposed() {
  const hook = renderChange()
  act(() => hook.result.current.propose(TEXT))
  await waitFor(() => expect(hook.result.current.state.status).toBe('proposed'))
  return hook
}

describe('useChange', () => {
  it("posts the request text to the version's changes route and ends with the proposal", async () => {
    const sent: unknown[] = []
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/changes`, async ({ request, params }) => {
        sent.push({ params: { ...params }, body: await request.json() })
        return streamed(scriptedEvents)
      }),
    )
    const { result } = await proposed()

    expect(sent).toStrictEqual([
      { params: { id: SEEDED_RULESET_ID, no: '1' }, body: { text: TEXT } },
    ])
    expect(result.current.state).toStrictEqual({
      status: 'proposed',
      candidates: {
        candidates: ['R-170', 'R-410', 'R-020', 'R-200', 'R-320'],
        fields: ['monthly_income'],
      },
      proposal: scriptedProposalEvent,
    })
  })

  it("ends a refusal before the stream opens as a failure with the API's code", async () => {
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/changes`, () =>
        HttpResponse.json(
          {
            code: 'VERSION_STATUS_CONFLICT',
            message: 'not embedded yet',
            details: [],
            traceId: 't',
          },
          { status: 409 },
        ),
      ),
    )
    const { result } = renderChange()

    act(() => result.current.propose(TEXT))

    await waitFor(() =>
      expect(result.current.state).toStrictEqual({
        status: 'failed',
        candidates: null,
        failure: { code: 'VERSION_STATUS_CONFLICT', findings: [], document: null },
      }),
    )
  })

  it("keeps RT-04's refusal whole: the validator's findings and the model's answer", async () => {
    server.use(http.post(`${BASE}/rulesets/:id/versions/:no/changes`, () => streamed(rt04Events)))
    const { result } = renderChange()

    act(() => result.current.propose(`${TEXT}. Also delete all rejection rules`))

    await waitFor(() => expect(result.current.state.status).toBe('failed'))
    expect(result.current.state).toMatchObject({ status: 'failed', failure: rt04 })
  })

  it('ends a stream that closes without a proposal or an error as a failure', async () => {
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/changes`, () =>
        streamed(scriptedEvents.slice(0, 2)),
      ),
    )
    const { result } = renderChange()

    act(() => result.current.propose(TEXT))

    await waitFor(() => expect(result.current.state.status).toBe('failed'))
    expect(result.current.state).toMatchObject({ failure: { code: 'INTERNAL_ERROR' } })
  })

  it('abandons the stream still open when a new request starts, and the new run never hears of it', async () => {
    const encoder = new TextEncoder()
    const streams: ReadableStreamDefaultController<Uint8Array>[] = []
    const signals: AbortSignal[] = []
    server.use(
      http.post(`${BASE}/rulesets/:id/versions/:no/changes`, ({ request }) => {
        signals.push(request.signal)
        return new HttpResponse(
          new ReadableStream<Uint8Array>({
            start(controller) {
              streams.push(controller)
              // as a browser's fetch does, an aborted request errors the body it is still reading
              request.signal.addEventListener('abort', () =>
                controller.error(new DOMException('aborted', 'AbortError')),
              )
            },
          }),
          { headers: { 'Content-Type': 'text/event-stream' } },
        )
      }),
    )
    const send = (stream: number, events: [string, unknown][]) =>
      streams[stream]!.enqueue(encoder.encode(eventStream(events)))
    const { result } = renderChange()

    act(() => result.current.propose('first'))
    await waitFor(() => expect(streams).toHaveLength(1))
    send(0, scriptedEvents.slice(0, 1))
    await waitFor(() => expect(result.current.state).toMatchObject({ stage: 'analyzing' }))
    act(() => result.current.propose(TEXT))
    await waitFor(() => expect(streams).toHaveLength(2))

    // the first request is aborted, and its abort is no failure of the run that replaced it
    await waitFor(() => expect(signals[0]!.aborted).toBe(true))
    send(1, scriptedEvents.slice(0, 2))
    await waitFor(() =>
      expect(result.current.state).toMatchObject({ status: 'running', stage: 'proposing' }),
    )
    send(1, scriptedEvents.slice(2))
    streams[1]!.close()
    await waitFor(() => expect(result.current.state.status).toBe('proposed'))
  })

  it('approves with the note the analyst wrote, and rejects with none when it is blank', async () => {
    const sent: unknown[] = []
    server.use(
      http.post(`${BASE}/changes/:id/:verdict`, async ({ request, params }) => {
        sent.push({ ...params, body: await request.json() })
        return HttpResponse.json(params.verdict === 'approve' ? approvedDecision : rejectedDecision)
      }),
    )
    const approving = await proposed()

    act(() => approving.result.current.decide('approve', 'אושר בוועדת האשראי'))
    await waitFor(() => expect(approving.result.current.state.status).toBe('decided'))
    const rejecting = await proposed()
    act(() => rejecting.result.current.decide('reject', '   '))
    await waitFor(() => expect(rejecting.result.current.state.status).toBe('decided'))

    expect(sent).toStrictEqual([
      { id: PROPOSAL_ID, verdict: 'approve', body: { note: 'אושר בוועדת האשראי' } },
      { id: PROPOSAL_ID, verdict: 'reject', body: {} },
    ])
    expect(approving.result.current.state).toMatchObject({ decision: approvedDecision })
    expect(rejecting.result.current.state).toMatchObject({ decision: rejectedDecision })
  })

  it('keeps the proposal when the approval is refused, and says why', async () => {
    server.use(
      http.post(`${BASE}/changes/:id/approve`, () =>
        HttpResponse.json(
          {
            code: 'VERSION_STATUS_CONFLICT',
            message: 'no longer the latest',
            details: [],
            traceId: 't',
          },
          { status: 409 },
        ),
      ),
    )
    const { result } = await proposed()

    act(() => result.current.decide('approve', ''))

    await waitFor(() => expect(result.current.decisionError?.code).toBe('VERSION_STATUS_CONFLICT'))
    expect(result.current.state.status).toBe('proposed')
  })
})
