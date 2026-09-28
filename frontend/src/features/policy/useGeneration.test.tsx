import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactElement } from 'react'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { publishedVersion, rulesets } from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { seededReview } from '../../test/fixtures/review'
import { useRulesets } from '../../api/queries'
import { GenerationProgress, GenerationResult, ReviewSummary } from './GenerationProgress'
import { STAGE_LABELS, useGeneration } from './useGeneration'

/**
 * The generation stream as the screen consumes it (Document 2, API Surface: parsing, authoring, validating, reviewing,
 * then the draft or an error). MSW answers with a real event stream, so the parser, the stage order and the two
 * endings, a draft and a refusal, are exercised the way the API sends them.
 */

const BASE = 'http://localhost:8080/api/v1'

function streamOf(events: [string, unknown][]): HttpResponse<string> {
  const body = events
    .map(([name, data]) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`)
    .join('')
  return new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream' } })
}

/** A stream the test writes event by event; `send` is ready once the request has arrived. */
function heldStream(): {
  response: () => HttpResponse<ReadableStream>
  send: (name: string, data: unknown) => void
  close: () => void
  opened: () => boolean
} {
  let controller: ReadableStreamDefaultController | null = null
  return {
    response: () =>
      new HttpResponse(
        new ReadableStream({
          start(opened) {
            controller = opened
          },
        }),
        { headers: { 'Content-Type': 'text/event-stream' } },
      ),
    send: (name, data) =>
      controller?.enqueue(
        new TextEncoder().encode(`event:${name}\ndata:${JSON.stringify(data)}\n\n`),
      ),
    close: () => controller?.close(),
    opened: () => controller !== null,
  }
}

function Harness() {
  const generation = useGeneration()
  return (
    <>
      <button type="button" onClick={() => generation.start('policy-1')}>
        Generate rules
      </button>
      <GenerationResult generation={generation} onReview={() => undefined} />
      <GenerationProgress generation={generation} />
      {generation.draft ? (
        <ReviewSummary review={generation.draft.review} language="he" onOpen={() => undefined} />
      ) : null}
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

/** The stage list's steps, in order. */
function steps(): HTMLElement[] {
  return [
    ...screen
      .getByRole('region', { name: 'Generation' })
      .querySelectorAll<HTMLElement>('.progress__step'),
  ]
}

const draftOf = (overrides: object) => ({
  ...publishedVersion,
  status: 'DRAFT',
  versionNo: 1,
  ...overrides,
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('useGeneration', () => {
  // Document 2, API Surface: progress events parsing, authoring, validating, reviewing; the reviewer reads the draft
  // last, and that stage is the one marked while it runs
  it('marks the reviewing stage while the reviewer reads the draft', async () => {
    const user = userEvent.setup()
    const stream = heldStream()
    server.use(http.post(`${BASE}/policies/:id/rulesets`, () => stream.response()))
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))
    await waitFor(() => expect(stream.opened()).toBe(true))
    act(() => {
      for (const stage of ['parsing', 'authoring', 'validating', 'reviewing']) {
        stream.send(stage, { paragraphs: 9 })
      }
    })

    await waitFor(() =>
      expect(
        within(screen.getByRole('region', { name: 'Generation' }))
          .getByText(STAGE_LABELS.reviewing)
          .closest('.progress__step'),
      ).toHaveClass('progress__step progress__step--now'),
    )
    expect(steps().map((step) => step.className)).toEqual([
      'progress__step progress__step--done',
      'progress__step progress__step--done',
      'progress__step progress__step--done',
      'progress__step progress__step--now',
    ])
    expect(steps()[3]).toHaveAttribute('aria-current', 'step')
  })

  // The owner's answer of 2026-09-28 to phase 5's first question: each finished stage with its count, from the stream
  // and the draft, and the run's time in the browser from its first event to the draft; no tokens
  it('counts each finished stage and times the run from its first event to the draft', async () => {
    const user = userEvent.setup()
    let now = 52_000
    vi.spyOn(performance, 'now').mockImplementation(() => now)
    const stream = heldStream()
    server.use(http.post(`${BASE}/policies/:id/rulesets`, () => stream.response()))
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))
    await waitFor(() => expect(stream.opened()).toBe(true))
    act(() => {
      stream.send('parsing', { paragraphs: 9 })
      stream.send('authoring', { paragraphs: 9 })
    })
    await waitFor(() => expect(steps()[1]).toHaveClass('progress__step--now'))
    // the first stage's count is the stream's, while the rest are still to come
    expect(steps()[0]!.querySelector('.progress__meta')).toHaveTextContent(/^9 ¶$/)
    expect(steps()[1]!.querySelector('.progress__meta')).toBeNull()
    act(() => {
      now = 56_100
      stream.send('validating', { paragraphs: 9 })
      stream.send('reviewing', { paragraphs: 9 })
      stream.send('draft', draftOf({ review: seededReview }))
      stream.close()
    })

    await waitFor(() =>
      expect(screen.getByRole('region', { name: 'Generation' })).toHaveTextContent(
        'Generation done · 4.1 s',
      ),
    )
    // Expected: the lending rule set's 20 rules, the validator's findings on the draft (none), the review's five
    expect(steps().map((step) => step.querySelector('.progress__meta')?.textContent)).toEqual([
      '9 ¶',
      '20 rules',
      '0 problems',
      '5 findings',
    ])
  })

  // Document 1, demo step 1: "Two rows carry warnings: one ambiguity and one conflict"
  it('shows what the reviewer found once the draft arrives', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          ['parsing', { paragraphs: 9 }],
          ['reviewing', { paragraphs: 9 }],
          [
            'draft',
            draftOf({ review: { ...seededReview, findings: seededReview.findings.slice(0, 2) } }),
          ],
        ]),
      ),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    const summary = await screen.findByRole('region', {
      name: 'The reviewer found 2 things to check',
    })
    expect(within(summary).getByText('1 blocks publishing')).toBeInTheDocument()
    expect(within(summary).getByText('Ambiguity')).toBeInTheDocument()
    expect(within(summary).getByText('Conflict')).toBeInTheDocument()
    expect(within(summary).getByText(seededReview.findings[0]!.message)).toHaveAttribute(
      'dir',
      'rtl',
    )
  })

  // Document 2, Flow 1: a failed review keeps the draft, and publishing waits for the review to run again
  it('says a review that failed must be run again before publishing', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          [
            'draft',
            draftOf({
              review: { status: 'FAILED', promptVersion: 'v1', findings: [], coverage: {} },
            }),
          ],
        ]),
      ),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    expect(
      await screen.findByText(
        'The review could not run; run it again from the rule set before publishing.',
      ),
    ).toBeInTheDocument()
  })

  it('announces the draft it was given, which decides nothing until a person publishes it', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          ['parsing', { paragraphs: 9 }],
          ['authoring', { paragraphs: 9 }],
          ['validating', { paragraphs: 9 }],
          ['draft', draftOf({})],
        ]),
      ),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    await screen.findByRole('button', { name: 'Review the draft' })
    // the lending rule set has twenty rules, and the draft decides nothing until a person publishes it
    expect(document.querySelector('.note--proposal')!.textContent).toBe(
      'A draft rule set was written from this policy: 20 rules, version 1. Nothing decides cases until a person publishes it.',
    )
  })

  it('names the stage that is running while the stream is open', async () => {
    const user = userEvent.setup()
    const stream = heldStream()
    server.use(http.post(`${BASE}/policies/:id/rulesets`, () => stream.response()))
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))
    await waitFor(() => expect(stream.opened()).toBe(true))
    act(() => stream.send('parsing', { paragraphs: 9 }))

    await waitFor(() => expect(steps()[0]).toHaveClass('progress__step--now'))
    act(() => stream.send('authoring', { paragraphs: 9 }))
    await waitFor(() => expect(steps()[1]).toHaveClass('progress__step--now'))
    expect(steps()[0]).toHaveClass('progress__step--done')
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
              document: null,
            },
          ],
        ]),
      ),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    const refusal = await screen.findByRole('alert')
    expect(refusal).toHaveTextContent('RULESET_INVALID')
    expect(refusal).toHaveTextContent('Nothing was stored.')
    expect(within(refusal).getByText('/rules/0/provenance/quote')).toBeInTheDocument()
    expect(
      within(refusal).getByText(
        'PROVENANCE_QUOTE_MISMATCH · R-100: the quote does not occur in paragraph 1',
      ),
    ).toBeInTheDocument()
    // the model's answer is shown only when the stream carried one
    expect(
      within(refusal).queryByRole('button', { name: 'What the model proposed' }),
    ).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Review the draft' })).not.toBeInTheDocument()
  })

  it('reports a stream that never opened, with no code of its own, as the provider being unavailable', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () => new HttpResponse(null, { status: 503 })),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('PROVIDER_UNAVAILABLE')
  })

  // Document 2: before any stream opens, a refusal carries the error envelope, 429 RATE_LIMITED for the rate limits
  it('reports a refusal before the stream opened by the code the API sent', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        HttpResponse.json(
          { code: 'RATE_LIMITED', message: 'Too many requests.', details: [], traceId: 't' },
          { status: 429 },
        ),
      ),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    const refusal = await screen.findByRole('alert')
    expect(refusal).toHaveTextContent('RATE_LIMITED')
    expect(refusal).not.toHaveTextContent('PROVIDER_UNAVAILABLE')
  })

  it('shows nothing at all before a run', () => {
    renderHarness(<Harness />)

    expect(screen.queryByRole('region', { name: 'Generation' })).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('makes the rule set list stale, so the new draft is there when a screen opens it', async () => {
    const user = userEvent.setup()
    let listed = 0
    server.use(
      http.get(`${BASE}/rulesets`, () => {
        listed += 1
        return HttpResponse.json(rulesets)
      }),
      http.post(`${BASE}/policies/:id/rulesets`, () => streamOf([['draft', draftOf({})]])),
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

  // A draft that validated can still be a poor one: the checking stage counts what the validator noted
  it('counts what the validator noted about a draft that passed', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/policies/:id/rulesets`, () =>
        streamOf([
          ['parsing', { paragraphs: 9 }],
          ['authoring', { paragraphs: 9 }],
          ['validating', { paragraphs: 9 }],
          [
            'draft',
            draftOf({
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
            }),
          ],
        ]),
      ),
    )
    renderHarness(<Harness />)

    await user.click(screen.getByRole('button', { name: 'Generate rules' }))

    await screen.findByRole('button', { name: 'Review the draft' })
    expect(steps()[2]!.querySelector('.progress__meta')).toHaveTextContent(/^2 problems$/)
  })
})
