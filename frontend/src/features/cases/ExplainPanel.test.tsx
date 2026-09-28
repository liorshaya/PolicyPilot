import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import applicant17 from '../../../../fixtures/eval/recordings/openai/explain/v1/189b157cd01762241e743d95ca4b11315e0256b084e4d9140f47bd1afe1f8467.json'
import officer2 from '../../../../fixtures/eval/recordings/openai/explain/v1/5929fbe7650bae548b299b811dbd0a661b7524688c1afd6e98ea90ce8aaa4d38.json'
import officer17 from '../../../../fixtures/eval/recordings/openai/explain/v1/6c6fb34fdaa66323a17641702d785f4996b7a5929699f501baa20bf243696525.json'
import { useExplain } from '../../api/queries'
import type { Audience, Explanation as ExplanationResponse } from '../../api/types'
import { specRules, stylesheet, unported } from '../../test/css'
import { server } from '../../test/msw/server'
import { ExplainActions, Explanation } from './ExplainPanel'

// @requirement FR-11

/**
 * "Explain" in the trace (Brief FR-11; Document 4, Prompt 3; the spec, section 09, "The explanation"). The answers are
 * the recorded ones of fixtures/eval/recordings/openai/explain/v1/: case 17 for an officer and for the applicant, and
 * case 2, an approval with a condition, for an officer; the API adds the decision, the audience and the prompt version.
 */

const BASE = 'http://localhost:8080/api/v1'
const DECISION = '0f4c1c9e-0000-4000-8000-000000000017'

/** A recording's answer as the API returns it. */
function answer(recording: { response: string }, audience: Audience): ExplanationResponse {
  const written = JSON.parse(recording.response) as Omit<
    ExplanationResponse,
    'decisionId' | 'audience' | 'promptVersion'
  >
  return { ...written, decisionId: DECISION, audience, promptVersion: 'v1' }
}

const forTheOfficer = answer(officer17, 'officer')
const forTheApplicant = answer(applicant17, 'applicant')
const approval = answer(officer2, 'officer')

/** The trace's head holds the two buttons and the explanation renders under it; one request serves both. */
function Trace({ onOpenRule }: { onOpenRule: (ruleId: string) => void }) {
  const explain = useExplain(DECISION)
  return (
    <>
      <div className="btn-group">
        <ExplainActions explain={explain} />
      </div>
      <Explanation explain={explain} language="he" onOpenRule={onOpenRule} />
    </>
  )
}

function renderTrace(onOpenRule: (ruleId: string) => void = () => undefined) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <Trace onOpenRule={onOpenRule} />
    </QueryClientProvider>,
  )
}

/** Answers every explain request with the recording for the audience it asks for. */
function answering(sent: unknown[], answers: Partial<Record<Audience, ExplanationResponse>>) {
  server.use(
    http.post(`${BASE}/decisions/:id/explain`, async ({ request, params }) => {
      const body = (await request.json()) as { audience: Audience }
      sent.push({ id: params.id, body })
      return HttpResponse.json(answers[body.audience])
    }),
  )
}

describe('ExplainPanel', () => {
  it('asks for nothing until the reader asks, and presses neither audience', () => {
    const sent: unknown[] = []
    answering(sent, { officer: forTheOfficer })
    renderTrace()

    expect(screen.getByRole('button', { name: 'Explain for an officer' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(screen.getByRole('button', { name: 'Explain for the applicant' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(sent).toEqual([])
  })

  it("marks the explanation as a model's, from this trace alone, with the audience pressed", async () => {
    const user = userEvent.setup()
    const sent: unknown[] = []
    answering(sent, { officer: forTheOfficer })
    renderTrace()

    await user.click(screen.getByRole('button', { name: 'Explain for an officer' }))

    const provenance = (await screen.findByText(/^For an officer · /)).closest('.explain__prov')!
    expect(provenance.querySelector('.actor--model')).not.toBeNull()
    // the spec's glossary, section 01: what a model wrote about a trace
    expect(provenance).toHaveTextContent(
      'For an officer · Written by a model from this trace alone; every rule and paragraph it cites was checked against the trace.',
    )
    expect(screen.getByRole('button', { name: 'Explain for an officer' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(screen.getByRole('button', { name: 'Explain for the applicant' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(sent).toEqual([{ id: DECISION, body: { audience: 'officer' } }])
  })

  it('reads the summary, then the named parts, each statement in Hebrew with its citations after it', async () => {
    const user = userEvent.setup()
    answering([], { officer: forTheOfficer })
    renderTrace()

    await user.click(screen.getByRole('button', { name: 'Explain for an officer' }))

    const summary = await screen.findByText(forTheOfficer.summary)
    expect(summary).toHaveClass('explain__text')
    expect(summary).toHaveAttribute('dir', 'rtl')
    expect(summary).toHaveAttribute('lang', 'he')
    // case 17 for an officer has factors and rules not applied, and no condition a person still checks
    expect(
      [...document.querySelectorAll('.explain__h')].map((heading) => heading.textContent),
    ).toStrictEqual(['Factors', 'Evaluated and not applied'])
    const r330 = screen.getByText(forTheOfficer.factors[2]!.statement, { exact: false })
    expect(r330).toHaveClass('explain__factor')
    expect(r330).toHaveAttribute('dir', 'rtl')
    // the chips come after the sentence and its full stop: R-330, then paragraph 7
    expect(r330.textContent).toBe(`${forTheOfficer.factors[2]!.statement} R-330 ¶\u00a07`)
    expect([...r330.querySelectorAll('.chip')].map((chip) => chip.className)).toStrictEqual([
      'chip chip--id',
      'chip chip--para',
    ])
    const r220 = screen.getByText(forTheOfficer.notApplied[0]!.statement, { exact: false })
    expect(r220.textContent).toBe(`${forTheOfficer.notApplied[0]!.statement} R-220`)
  })

  it('lists the flags of an approval as what a person still checks', async () => {
    // Document 4, Prompt 3: "For approve, list the flags as conditions that a person still checks"
    const user = userEvent.setup()
    answering([], { officer: approval })
    renderTrace()

    await user.click(screen.getByRole('button', { name: 'Explain for an officer' }))

    expect(await screen.findByText('A person still checks')).toHaveClass('explain__h')
    expect(
      [...document.querySelectorAll('.explain__h')].map((heading) => heading.textContent),
    ).toStrictEqual(['Factors', 'A person still checks', 'Evaluated and not applied'])
    const condition = screen.getByText(approval.conditions[0]!.statement, { exact: false })
    expect(within(condition).getByText('STABLE_INCOME_MANUAL_CHECK')).toHaveClass(
      'chip',
      'chip--field',
    )
  })

  it('opens the rule a citation names', async () => {
    const user = userEvent.setup()
    answering([], { officer: forTheOfficer })
    const opened: string[] = []
    renderTrace((ruleId) => opened.push(ruleId))

    await user.click(screen.getByRole('button', { name: 'Explain for an officer' }))
    await user.click(await screen.findByRole('button', { name: 'R-330' }))

    expect(opened).toEqual(['R-330'])
  })

  it('asks for the applicant when the reader chooses the applicant, and offers the text for the letter', async () => {
    const user = userEvent.setup()
    const sent: unknown[] = []
    answering(sent, { officer: forTheOfficer, applicant: forTheApplicant })
    renderTrace()

    await user.click(screen.getByRole('button', { name: 'Explain for the applicant' }))

    expect(await screen.findByText(/^For the applicant · /)).toBeInTheDocument()
    await waitFor(() => expect(sent).toEqual([{ id: DECISION, body: { audience: 'applicant' } }]))
    // the product owns the letter and sends nothing: the text is copied, the summary first, then each statement
    await user.click(screen.getByRole('button', { name: 'Copy for the letter' }))
    expect(await navigator.clipboard.readText()).toBe(
      [forTheApplicant.summary, ...forTheApplicant.factors.map((factor) => factor.statement)].join(
        '\n',
      ),
    )
  })

  it('offers no letter for an officer', async () => {
    const user = userEvent.setup()
    answering([], { officer: forTheOfficer })
    renderTrace()

    await user.click(screen.getByRole('button', { name: 'Explain for an officer' }))

    await screen.findByText(forTheOfficer.summary)
    expect(screen.queryByRole('button', { name: 'Copy for the letter' })).not.toBeInTheDocument()
  })

  it('cites a rule an analyst wrote by the person mark, since it has no paragraph', async () => {
    // Document 4, Prompt 3: a factor's paragraph is null for a rule an analyst wrote (R-310's provenance)
    const user = userEvent.setup()
    const statement = 'הוותק לא דווח, ולכן הבקשה הופנתה לבדיקה ידנית.'
    answering([], {
      officer: { ...forTheOfficer, factors: [{ ruleId: 'R-310', paragraph: null, statement }] },
    })
    renderTrace()

    await user.click(screen.getByRole('button', { name: 'Explain for an officer' }))

    const factor = await screen.findByText(statement, { exact: false })
    expect(factor.querySelector('.chip--para')).toBeNull()
    expect(factor.querySelector('.actor--person')).toHaveAttribute('title', 'Written by an analyst')
  })

  // Document 2, explain row: 503 when the provider fails; the trace stays the whole of the decision
  it('says so when no explanation was written, and asks the same reader again', async () => {
    const user = userEvent.setup()
    server.use(
      http.post(`${BASE}/decisions/:id/explain`, () =>
        HttpResponse.json(
          {
            code: 'PROVIDER_UNAVAILABLE',
            message: 'The model provider is unavailable.',
            details: [],
            traceId: 't',
          },
          { status: 503 },
        ),
      ),
    )
    renderTrace()

    await user.click(screen.getByRole('button', { name: 'Explain for an officer' }))

    expect(
      await screen.findByText(
        'No explanation was written; the trace below is the whole of the decision.',
      ),
    ).toBeInTheDocument()
    expect(screen.getByText('PROVIDER_UNAVAILABLE')).toBeInTheDocument()

    const sent: unknown[] = []
    answering(sent, { officer: forTheOfficer })
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(sent).toEqual([{ id: DECISION, body: { audience: 'officer' } }]))
  })
})

describe('ExplainPanel.css', () => {
  it("carries every rule of the spec's explanation, with the spec's declarations", () => {
    const explanation = specRules('/* Trace */', '/* The assistant thread').filter(([selector]) =>
      selector.startsWith('.explain'),
    )

    expect(explanation).toHaveLength(5)
    expect(unported(stylesheet('features/cases/ExplainPanel.css'), explanation)).toEqual([])
  })
})
