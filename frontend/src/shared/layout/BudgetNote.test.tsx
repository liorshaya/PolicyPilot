import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { budgetOpen, budgetSpent } from '../../test/fixtures/budget'
import { server } from '../../test/msw/server'
import { BudgetNote } from './BudgetNote'

// @requirement NFR-7

/**
 * The one note that is app-wide (the spec, section 08: "the spent budget (Document 5's degradation order), sits under
 * the workspace header of every screen that calls a model, and says what keeps working"), read from GET /system/budget
 * (Document 2). The time it resumes is the ledger's next midnight UTC, shown on the reader's clock (UTC in the tests).
 */
const BASE = 'http://localhost:8080/api/v1'

function renderNote() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <BudgetNote />
    </QueryClientProvider>,
  )
}

describe('BudgetNote', () => {
  it('says the budget is spent, until when, and what keeps working, in the system voice', async () => {
    server.use(http.get(`${BASE}/system/budget`, () => HttpResponse.json(budgetSpent)))
    renderNote()

    const note = await screen.findByRole('note', { name: 'Budget' })
    expect(note).toHaveClass('note', 'note--warning')
    expect(note.querySelector('.actor--system')).not.toBeNull()
    // the spec's sentence (section 08's notes), the time from resumesAt
    expect(note).toHaveTextContent(
      "Today's model budget is spent; answers, explanations and drafts come from the cache until 00:00. Decisions, the table and the audit log are unaffected.",
    )
  })

  it('says nothing while the budget is not spent', async () => {
    let asked = false
    server.use(
      http.get(`${BASE}/system/budget`, () => {
        asked = true
        return HttpResponse.json(budgetOpen)
      }),
    )
    const { container } = renderNote()

    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(asked).toBe(true)
    expect(container).toBeEmptyDOMElement()
  })
})
