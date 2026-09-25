import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/msw/server'
import { ollamaProvider } from '../../test/fixtures/provider'
import { ProviderBadge } from './ProviderBadge'

// @requirement FR-21

/**
 * The model provider in the header of every screen (Brief FR-21: the provider "visible in the UI"; Document 2, API
 * Surface: GET /system/provider, "shown in the UI header"). The names are the ones the route answers, which the
 * fixtures take from Document 2's profiles table.
 */

const PROVIDER_URL = 'http://localhost:8080/api/v1/system/provider'

function renderBadge() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ProviderBadge />
    </QueryClientProvider>,
  )
}

/** Each term of the badge with the value beside it, in the order a reader meets them. */
function namesIn(badge: HTMLElement): [string, string][] {
  return within(badge)
    .getAllByRole('term')
    .map((term) => [term.textContent ?? '', term.nextElementSibling?.textContent ?? ''])
}

describe('ProviderBadge', () => {
  it('names the provider, both chat models and the embedding model with its dimension', async () => {
    renderBadge()

    const badge = await screen.findByRole('region', { name: 'Model provider' })
    expect(namesIn(badge)).toEqual([
      ['Provider', 'OpenAI'],
      ['Strong model', 'gpt-5.6-terra'],
      ['Fast model', 'gpt-5.6-luna'],
      ['Embeddings', 'text-embedding-3-small, 1536 dimensions'],
    ])
  })

  it('names the local models when the API runs on Ollama', async () => {
    server.use(http.get(PROVIDER_URL, () => HttpResponse.json(ollamaProvider)))
    renderBadge()

    const badge = await screen.findByRole('region', { name: 'Model provider' })
    expect(namesIn(badge)).toEqual([
      ['Provider', 'Ollama'],
      ['Strong model', 'qwen3:14b'],
      ['Fast model', 'qwen3:14b'],
      ['Embeddings', 'bge-m3, 1024 dimensions'],
    ])
  })

  it('says the provider is unknown when the route cannot be read, and names no model', async () => {
    server.use(http.get(PROVIDER_URL, () => HttpResponse.json({}, { status: 500 })))
    renderBadge()

    const badge = await screen.findByRole('region', { name: 'Model provider' })
    expect(await within(badge).findByRole('definition')).toHaveTextContent(/^Unknown$/)
    expect(namesIn(badge)).toEqual([['Provider', 'Unknown']])
  })
})
