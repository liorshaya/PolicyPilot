import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { publishedVersion, rulesets } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { useRulesets } from '../../api/queries'
import { GenerationProgress } from './GenerationProgress'
import { STAGE_LABELS, useGeneration } from './useGeneration'

/**
 * The generation stream as the screen consumes it (Document 2, API Surface: parsing, authoring, validating, then
 * the draft). MSW answers with a real event stream, so the parser, the stage order and the two endings — a draft
 * and a refusal — are exercised the way the API sends them.
 */

const BASE = 'http://localhost:8080/api/v1'

function streamOf(events: [string, unknown][]): HttpResponse<string> {
  const body = events
    .map(([name, data]) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`)
    .join('')
  return new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream' } })
}

function Harness({ onStarted }: { onStarted?: (start: () => void) => void }) {
  const generation = useGeneration()
  return (
    <>
      <button
        type="button"
        onClick={() => {
          generation.start('policy-1')
          onStarted?.(() => generation.cancel())
        }}
      >
        Generate rules
      </button>
      <GenerationProgress generation={generation} onOpenRules={() => undefined} />
    </>
  )
}

/** Holds the rule set list in the cache, so a test can see it refetched after a draft. */
function Listing() {
  const rulesets = useRulesets()
  return <span>{rulesets.data?.length ?? 0} rule sets</span>
}

/** The hook invalidates the rule set list when a draft arrives, so it runs inside a client like the screens do. */
function renderHarness(ui: ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('useGeneration', () => {
  it('shows every stage and then the draft it was given', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          ['parsing', { paragraphs: 9 }],
          ['authoring', { paragraphs: 9 }],
          ['validating', { paragraphs: 9 }],
          ['draft', { ...publishedVersion, status: 'DRAFT', versionNo: 1 }],
        ]),
      ),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    expect(await screen.findByText(/A draft rule set was written/)).toBeInTheDocument()
    // the lending rule set has twenty rules, and the draft decides nothing until a person publishes it
    expect(screen.getByText('20 rules')).toBeInTheDocument()
    expect(
      screen.getByText(/Nothing decides cases until a person publishes it/),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Review the draft' })).toBeInTheDocument()
  })

  it('names the stage that is running while the stream is open', async () => {
    const user = userEvent.setup()
    let release: (() => void) | null = null
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () => {
        const stream = new ReadableStream({
          start(controller) {
            controller.enqueue(new TextEncoder().encode('event:parsing\ndata:{"paragraphs":9}\n\n'))
            release = () => {
              controller.enqueue(
                new TextEncoder().encode('event:authoring\ndata:{"paragraphs":9}\n\n'),
              )
              controller.close()
            }
          },
        })
        return new HttpResponse(stream, { headers: { 'Content-Type': 'text/event-stream' } })
      }),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    expect(await screen.findByText(STAGE_LABELS.parsing)).toBeInTheDocument()
    await waitFor(() => expect(release).not.toBeNull())
    act(() => release?.())
    await waitFor(() => expect(screen.queryByText(STAGE_LABELS.parsing)).not.toBeInTheDocument())
  })

  it('says what was refused and that nothing was stored', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          ['parsing', { paragraphs: 1 }],
          [
            'error',
            {
              code: 'RULESET_INVALID',
              findings: [
                {
                  code: 'PROVENANCE_QUOTE_MISMATCH',
                  severity: 'error',
                  path: '/rules/0/provenance/quote',
                  message: 'R-100: the quote does not occur in paragraph 1',
                  ruleIds: ['R-100'],
                  fieldNames: [],
                },
              ],
            },
          ],
        ]),
      ),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('RULESET_INVALID')
    expect(screen.getByText(/Nothing was stored/)).toBeInTheDocument()
    expect(screen.getByText('PROVENANCE_QUOTE_MISMATCH')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Review the draft' })).not.toBeInTheDocument()
  })

  it('reports a stream that never opened as the provider being unavailable', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () => new HttpResponse(null, { status: 503 })),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('PROVIDER_UNAVAILABLE')
  })

  it('shows nothing at all before a run', () => {
    renderHarness(<Harness />)

    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('makes the rule set list stale, so the new draft is there when a screen opens it', async () => {
    const user = userEvent.setup()
    let listed = 0
    server.use(
      http.get(`${BASE}/rulesets`, () => {
        listed += 1
        return HttpResponse.json(rulesets)
      }),
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([['draft', { ...publishedVersion, status: 'DRAFT', versionNo: 1 }]]),
      ),
    )
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <Listing />
        <Harness />
      </QueryClientProvider>,
    )
    await waitFor(() => expect(listed).toBe(1))

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    // a generation creates a rule set the cached list does not have; without this the screen opens the old one
    await waitFor(() => expect(listed).toBe(2))
  })

  it('shows what the validator noted about a draft that passed', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          [
            'draft',
            {
              ...publishedVersion,
              status: 'DRAFT',
              versionNo: 1,
              findings: [
                {
                  code: 'NO_TERMINAL_APPROVE',
                  severity: 'info',
                  path: '/rules',
                  message: 'no rule can produce approve',
                  ruleIds: [],
                  fieldNames: [],
                },
                {
                  code: 'REFER_PRECEDES_REJECT',
                  severity: 'warning',
                  path: '/rules/1/priority',
                  message: 'terminal refer R-002 at 300 precedes a terminal reject at 400',
                  ruleIds: ['R-002'],
                  fieldNames: [],
                },
              ],
            },
          ],
        ]),
      ),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    // a draft that validated can still be a poor one; the analyst approving it has to see what was noted
    expect(await screen.findByText('NO_TERMINAL_APPROVE')).toBeInTheDocument()
    expect(screen.getByText('no rule can produce approve')).toBeInTheDocument()
    expect(screen.getByText('REFER_PRECEDES_REJECT')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Review the draft' })).toBeInTheDocument()
  })
})
