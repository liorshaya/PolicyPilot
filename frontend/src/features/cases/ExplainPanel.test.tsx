import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { Explanation } from '../../api/types'
import { server } from '../../test/msw/server'
import { ExplainPanel } from './ExplainPanel'

/**
 * "Explain" in the trace view (Brief FR-11; Document 4, Prompt 3; Work Plan day 10: "Explain on case 17 cites R-330 and
 * its paragraph"). The answers are shaped like the first live run's of case 17 and case 2
 * (fixtures/eval/recordings/openai/explain/v1/): R-330 with paragraph 7, and STABLE_INCOME_MANUAL_CHECK as a condition.
 */

const BASE = 'http://localhost:8080/api/v1'
const DECISION = '0f4c1c9e-0000-4000-8000-000000000017'

const caseSeventeen: Explanation = {
  decisionId: DECISION,
  audience: 'officer',
  language: 'he',
  promptVersion: 'v1',
  summary:
    'הבקשה הופנתה לבדיקת חתם לפי R-330, משום שנרשם אירוע אשראי אחד ב-24 החודשים האחרונים ואין ערב.',
  factors: [
    { ruleId: 'R-010', paragraph: 5, statement: 'ההחזר החודשי חושב לפי לוח שפיצר.' },
    { ruleId: 'R-330', paragraph: 7, statement: 'נמצא אירוע אשראי אחד ואין ערב.' },
  ],
  conditions: [],
  notApplied: [{ ruleId: 'R-220', statement: 'נרשם אירוע אחד, לעומת סף של 2.' }],
}

function renderPanel(onOpenRule: (ruleId: string) => void = () => undefined) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ExplainPanel decisionId={DECISION} language="he" onOpenRule={onOpenRule} />
    </QueryClientProvider>,
  )
}

function answering(explanation: Explanation, sent: unknown[]) {
  server.use(
    http.post(`${BASE}/decisions/:id/explain`, async ({ request, params }) => {
      sent.push({ id: params.id, body: await request.json() })
      return HttpResponse.json(explanation)
    }),
  )
}

describe('ExplainPanel', () => {
  it('asks for nothing until the reader asks', () => {
    const sent: unknown[] = []
    answering(caseSeventeen, sent)
    renderPanel()

    expect(screen.getByRole('button', { name: 'Explain for an officer' })).toBeInTheDocument()
    expect(sent).toEqual([])
  })

  it("explains case 17 to an officer by R-330 and its paragraph, marked as the model's reading", async () => {
    const user = userEvent.setup()
    const sent: unknown[] = []
    answering(caseSeventeen, sent)
    renderPanel()

    await user.click(screen.getByRole('button', { name: 'Explain for an officer' }))

    const factor = (await screen.findByText('נמצא אירוע אשראי אחד ואין ערב.')).closest('li')!
    expect(factor).toHaveTextContent('R-330')
    expect(factor).toHaveTextContent('¶ 7')
    expect(screen.getByText(/Written by a model from this trace alone/)).toBeVisible()
    expect(screen.getByText(caseSeventeen.summary)).toHaveAttribute('dir', 'rtl')
    expect(screen.getByText('Evaluated and not applied')).toBeInTheDocument()
    expect(sent).toEqual([{ id: DECISION, body: { audience: 'officer' } }])
  })

  it('asks for the applicant when the reader chooses the applicant', async () => {
    const user = userEvent.setup()
    const sent: unknown[] = []
    answering({ ...caseSeventeen, audience: 'applicant' }, sent)
    renderPanel()

    await user.click(screen.getByRole('button', { name: 'Explain for the applicant' }))

    await waitFor(() => expect(sent).toEqual([{ id: DECISION, body: { audience: 'applicant' } }]))
  })

  it('opens the rule a factor cites', async () => {
    const user = userEvent.setup()
    answering(caseSeventeen, [])
    const opened: string[] = []
    renderPanel((ruleId) => opened.push(ruleId))

    await user.click(screen.getByRole('button', { name: 'Explain for an officer' }))
    await user.click(await screen.findByRole('button', { name: 'R-330' }))

    expect(opened).toEqual(['R-330'])
  })

  // Document 4, Prompt 3: "For approve, list the flags as conditions that a person still checks"
  it('lists the flags of an approval as what a person still checks', async () => {
    const user = userEvent.setup()
    answering(
      {
        ...caseSeventeen,
        summary: 'התוצאה היא אישור לפי כלל R-900, בכפוף לבדיקה ידנית של יציבות ההכנסה.',
        factors: [{ ruleId: 'R-900', paragraph: 9, statement: 'כל התנאים התקיימו.' }],
        conditions: [
          { flagCode: 'STABLE_INCOME_MANUAL_CHECK', statement: 'יש לבדוק ידנית שההכנסה יציבה.' },
        ],
        notApplied: [],
      },
      [],
    )
    renderPanel()

    await user.click(screen.getByRole('button', { name: 'Explain for an officer' }))

    expect(await screen.findByText('A person still checks')).toBeInTheDocument()
    expect(screen.getByText('STABLE_INCOME_MANUAL_CHECK')).toBeInTheDocument()
  })

  // Document 2, explain row: 503 when the provider fails; the trace stays the whole of the decision
  it('says so when no explanation was written', async () => {
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
    renderPanel()

    await user.click(screen.getByRole('button', { name: 'Explain for an officer' }))

    expect(
      await screen.findByText(
        'No explanation was written; the trace below is the whole of the decision.',
      ),
    ).toBeInTheDocument()
  })
})
