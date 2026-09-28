import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { keys } from '../../api/queries'
import { PROPOSAL_ID } from '../../test/fixtures/change'
import { batch, decision, SEEDED_RULESET_ID } from '../../test/msw/handlers'
import { GoToPalette } from './GoToPalette'

/**
 * The palette over what the workspace has read (the owner's answer to phase 6's second question): the rule set list,
 * the latest version, its policy and the whole audit log, read from the API with the screens' own queries, and this
 * session's run of the cases from what the run left. Every answer is the default fixtures' (src/test/msw/handlers.ts).
 */

function renderPalette(run?: typeof batch) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  if (run !== undefined) {
    client.setQueryData(keys.run(SEEDED_RULESET_ID, 1), run)
  }
  const onGoTo = vi.fn()
  const onClose = vi.fn()
  render(
    <QueryClientProvider client={client}>
      <GoToPalette rulesetId={null} onGoTo={onGoTo} onClose={onClose} />
    </QueryClientProvider>,
  )
  return { onGoTo, onClose, user: userEvent.setup() }
}

describe('GoToPalette', () => {
  it("reaches a case of this session's run and opens its trace", async () => {
    const { user, onGoTo, onClose } = renderPalette(batch)

    await user.type(screen.getByRole('combobox', { name: 'Go to' }), '17')
    await screen.findByRole('option', { name: /Case 17/ })
    await user.keyboard('{Enter}')

    expect(onGoTo).toHaveBeenCalledWith({ kind: 'case', decisionId: decision.id })
    expect(onClose).toHaveBeenCalled()
  })

  it("reaches the workspace's rule, its policy's paragraph, the log's change request and the version", async () => {
    const { user, onGoTo } = renderPalette()
    const input = screen.getByRole('combobox', { name: 'Go to' })

    await user.type(input, 'R-170')
    await user.click(await screen.findByRole('option', { name: /^R-170/ }))
    expect(onGoTo).toHaveBeenLastCalledWith({
      kind: 'rule',
      rulesetId: SEEDED_RULESET_ID,
      ruleId: 'R-170',
    })

    for (const [query, target] of [
      ['¶ 4', { kind: 'paragraph', index: 4 }],
      ['CR-1', { kind: 'change', changeRequestId: PROPOSAL_ID }],
      ['v1', { kind: 'version', rulesetId: SEEDED_RULESET_ID, versionNo: 1 }],
    ] as const) {
      await user.clear(input)
      await user.type(input, query)
      await waitFor(() => expect(screen.getAllByRole('option')).toHaveLength(1))
      await user.keyboard('{Enter}')
      expect(onGoTo, query).toHaveBeenLastCalledWith(expect.objectContaining(target))
    }
  })

  it('says a number reaches a case once the cases have run', async () => {
    const { user } = renderPalette()

    await user.type(screen.getByRole('combobox', { name: 'Go to' }), '17')

    expect(
      await screen.findByText('Run the cases to reach a case by its number'),
    ).toBeInTheDocument()
  })
})
