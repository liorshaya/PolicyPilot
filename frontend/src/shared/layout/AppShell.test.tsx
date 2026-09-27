import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { ComponentProps } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ollamaProvider } from '../../test/fixtures/provider'
import { server } from '../../test/msw/server'
import { rtlSnapshot } from '../../test/rtlSnapshot'
import { AppShell } from './AppShell'

// @requirement FR-21

/**
 * The rail (the spec, section 08, and the owner's answers of 2026-09-28): the six screens in the demo's order, a count
 * only beside Rules for the findings to acknowledge, the workspace named by its policy, the provider line when the route
 * answers (Brief FR-21: the provider "visible in the UI"), the guided demo strip, and at the foot who the product records
 * a person as, Leave, Help and Theme. The principle sentence lives on the gate and in the legend, not in the rail.
 */
const PROVIDER_URL = 'http://localhost:8080/api/v1/system/provider'
const POLICY = { title: 'מדיניות אשראי צרכני - הלוואות אישיות', language: 'he' as const }

function renderShell({
  children = <p>The workspace</p>,
  ...props
}: Partial<ComponentProps<typeof AppShell>> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AppShell
        current="rules"
        onNavigate={vi.fn()}
        onLeave={vi.fn()}
        policy={POLICY}
        findingsToAcknowledge={7}
        aside={<section aria-label="Guided demo">Guided demo</section>}
        {...props}
      >
        {children}
      </AppShell>
    </QueryClientProvider>,
  )
}

const rail = () => screen.getByRole('navigation', { name: 'Workspace' })

describe('AppShell', () => {
  afterEach(() => {
    localStorage.removeItem('pp-theme')
    document.documentElement.removeAttribute('data-theme')
  })

  it('lists the six screens as buttons, in the order of the demo, the current one marked', () => {
    renderShell()
    const screens = within(within(rail()).getByRole('list')).getAllByRole('button')

    expect(screens.map((button) => button.textContent)).toStrictEqual([
      'Policies',
      'Rules 7',
      'Cases',
      'Assistant',
      'Change',
      'Audit log',
    ])
    expect(screens[1]).toHaveAttribute('aria-current', 'page')
    expect(screens[1]).toHaveClass('rail__link')
  })

  it('shows a count beside Rules alone, for the findings to acknowledge, and none when there are none', () => {
    const { unmount } = renderShell()

    const counts = rail().querySelectorAll('.rail__count')
    expect(counts).toHaveLength(1)
    expect(counts[0]).toHaveTextContent('7')
    expect(counts[0]).toHaveAttribute('title', '7 findings to acknowledge')
    expect(counts[0]!.closest('button')).toHaveTextContent(/^Rules/)
    unmount()

    renderShell({ findingsToAcknowledge: 0 })
    expect(rail().querySelectorAll('.rail__count')).toHaveLength(0)
  })

  it("names the workspace by its policy, in the policy's own language and direction", () => {
    renderShell()
    const name = screen.getByText(POLICY.title)

    expect(name).toHaveAttribute('lang', 'he')
    expect(name).toHaveAttribute('dir', 'rtl')
    expect(name.closest('.rail__workspace')).not.toBeNull()
  })

  it('shows the provider line when the route answers, local for Ollama, and nothing when it cannot be read', async () => {
    const { unmount } = renderShell()
    expect(await within(rail()).findByText('Provider OpenAI · cloud')).toHaveClass('line')
    unmount()

    server.use(http.get(PROVIDER_URL, () => HttpResponse.json(ollamaProvider)))
    const second = renderShell()
    expect(await within(rail()).findByText('Provider Ollama · local')).toBeInTheDocument()
    second.unmount()

    server.use(http.get(PROVIDER_URL, () => HttpResponse.json({}, { status: 500 })))
    renderShell()
    await screen.findByText(POLICY.title)
    expect(within(rail()).queryByText(/^Provider/)).not.toBeInTheDocument()
  })

  it('shows who the product records a person as, and Leave returns to the gate', async () => {
    const onLeave = vi.fn()
    renderShell({ onLeave })
    const me = rail().querySelector('.rail__me')!

    expect(me.querySelector('.actor--person')).toHaveTextContent('Analyst')
    await userEvent.click(within(me as HTMLElement).getByRole('button', { name: 'Leave' }))

    expect(onLeave).toHaveBeenCalledOnce()
  })

  it('opens the legend and the shortcuts sheet from Help, in a popover that Escape closes', async () => {
    renderShell()
    await within(rail()).findByText('Provider OpenAI · cloud')

    await userEvent.click(screen.getByRole('button', { name: 'Help' }))
    const help = screen.getByRole('dialog', { name: 'Help' })

    expect(within(help).getByText('The model proposes and explains')).toHaveClass(
      'actor',
      'actor--model',
    )
    expect(within(help).getByText('The rules engine decides')).toHaveClass('actor--engine')
    expect(within(help).getByText('A person approves')).toHaveClass('actor--person')
    expect(within(help).getByText('The system records and resets')).toHaveClass('actor--system')
    expect(help).toHaveTextContent(
      'Provider OpenAI · cloud · authoring, review and changes on gpt-5.6-terra, answers and explanations on gpt-5.6-luna, embeddings text-embedding-3-small (1536)',
    )
    expect(within(help).getByText('Go to Rules / Cases / Assistant')).toBeInTheDocument()

    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Help' })).not.toBeInTheDocument()
  })

  it('goes to Rules, Cases or the Assistant with G and the screen letter, but not while typing', async () => {
    const onNavigate = vi.fn()
    renderShell({ onNavigate, children: <input aria-label="Question" /> })

    await userEvent.keyboard('gc')
    expect(onNavigate).toHaveBeenLastCalledWith('cases')
    await userEvent.keyboard('ga')
    expect(onNavigate).toHaveBeenLastCalledWith('assistant')

    await userEvent.type(screen.getByLabelText('Question'), 'gr')
    expect(onNavigate).toHaveBeenCalledTimes(2)
  })

  it('toggles the theme and stores the choice in this browser', async () => {
    renderShell()

    await userEvent.click(screen.getByRole('button', { name: 'Theme' }))
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(localStorage.getItem('pp-theme')).toBe('dark')

    await userEvent.click(screen.getByRole('button', { name: 'Theme' }))
    expect(document.documentElement).toHaveAttribute('data-theme', 'light')
    expect(localStorage.getItem('pp-theme')).toBe('light')
  })

  it('keeps the principle sentence out of the rail, and carries the guided demo strip', () => {
    renderShell()

    expect(rail()).not.toHaveTextContent(/the rules engine decides/i)
    expect(within(rail()).getByRole('region', { name: 'Guided demo' })).toBeInTheDocument()
  })

  it('reads as the RTL snapshot of the rail with a Hebrew policy name', async () => {
    renderShell()
    await within(rail()).findByText('Provider OpenAI · cloud')

    expect(rtlSnapshot(rail())).toMatchSnapshot()
  })
})
