import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import type { ComponentProps } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ollamaProvider } from '../../test/fixtures/provider'
import { server } from '../../test/msw/server'
import { rtlSnapshot } from '../../test/rtlSnapshot'
import { resolved, rule, specRules, stylesheet, unported } from '../../test/css'
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

  it('lists each shortcut the product answers, in the order of the spec', async () => {
    // the spec, section 04: a shortcut joins the sheet with the phase that builds it; phase 2 built /, ↓ ↑ and ↵, and
    // phase 6 the palette
    renderShell()

    await userEvent.click(screen.getByRole('button', { name: 'Help' }))

    const sheet = screen.getByRole('dialog', { name: 'Help' }).querySelector('.shortcuts')!
    expect([...sheet.children].map((row) => row.textContent)).toStrictEqual([
      'Go to anything by id⌘K',
      'Filter the list/',
      'Select next / previous row↓↑',
      'Open the row in the margin↵',
      'Go to Rules / Cases / AssistantGR C A',
    ])
  })

  it('opens the palette with ⌘K, or Ctrl+K, even from a field', async () => {
    const onOpenPalette = vi.fn()
    renderShell({ onOpenPalette, children: <input aria-label="Question" /> })

    await userEvent.keyboard('{Meta>}k{/Meta}')
    expect(onOpenPalette).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('textbox', { name: 'Question' }))
    await userEvent.keyboard('{Control>}k{/Control}')
    expect(onOpenPalette).toHaveBeenCalledTimes(2)
    await userEvent.keyboard('k')
    expect(onOpenPalette).toHaveBeenCalledTimes(2)
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

  // the spec (v3.9), section 08, Rail: the keyboard's first stop skips to the workspace; it stayed clipped to a pixel
  // while it had the focus, so the focus went out of sight
  it('makes a link to the workspace the first stop of the keyboard, drawn when it takes the focus', async () => {
    const user = userEvent.setup()
    renderShell()

    await user.tab()

    const skip = screen.getByRole('link', { name: 'Skip to the workspace' })
    expect(skip).toHaveFocus()
    expect(skip).toHaveAttribute('href', '#workspace')
    expect(skip).toHaveClass('sr-only', 'skip')
    expect(rule(stylesheet('shared/layout/AppShell.css'), '.skip:focus-visible')).toMatchObject({
      position: 'fixed',
      width: 'auto',
      height: 'auto',
      'clip-path': 'none',
    })
  })

  // the address bar names the screen (#/rules), so the link's own #workspace took the reader to Policies from any
  // screen: it moves the focus into the workspace and leaves the address as it was
  it('skips to the workspace of the screen on hand, the address unchanged', async () => {
    window.location.hash = '#/rules'
    const user = userEvent.setup()
    renderShell()

    await user.click(screen.getByRole('link', { name: 'Skip to the workspace' }))

    expect(screen.getByRole('main')).toHaveFocus()
    expect(window.location.hash).toBe('#/rules')
    window.location.hash = ''
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

/**
 * The phone (the spec, section 10, "Cases at phone width"; section 08: "below 720px the rail becomes a top bar"): the
 * lockup, the version and the menu, with the six screens as a row of quiet buttons; the menu holds what the rail held
 * besides the screens. jsdom has no matchMedia, so each test says how wide the window is.
 */
describe('AppShell below 720px', () => {
  function windowOf(phone: boolean) {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: phone && query === '(max-width: 720px)',
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }))
  }

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it("draws the top bar in the rail's place: the lockup, the version and the menu", () => {
    windowOf(true)
    renderShell({ version: { status: 'PUBLISHED', versionNo: 1 } })

    expect(document.querySelector('.rail')).toBeNull()
    const top = document.querySelector<HTMLElement>('.phone__top')!
    expect(within(top).getByRole('img', { name: 'PolicyPilot' })).toBeInTheDocument()
    // the spec's bar writes the version alone, "v1", in its state's pill; the state's word is its title
    const version = within(top).getByText('v1')
    expect(version).toHaveClass('vstatus', 'vstatus--published')
    expect(version).toHaveAttribute('title', 'Published v1')
    expect(within(top).getByRole('button', { name: 'Menu' })).toHaveAttribute(
      'aria-expanded',
      'false',
    )
  })

  it('lists the six screens as a row of quiet buttons, the current one pressed', async () => {
    windowOf(true)
    const onNavigate = vi.fn()
    renderShell({ onNavigate })

    const row = screen.getByRole('navigation', { name: 'Workspace' })
    expect(row).toHaveClass('phone__screens')
    const buttons = within(row).getAllByRole('button')
    expect(buttons.map((button) => button.textContent)).toStrictEqual([
      'Policies',
      'Rules 7',
      'Cases',
      'Assistant',
      'Change',
      'Audit log',
    ])
    const rules = buttons[1]!
    expect(rules).toHaveClass('btn', 'btn--secondary')
    expect(rules).toHaveAttribute('aria-pressed', 'true')
    expect(buttons[0]).toHaveClass('btn', 'btn--quiet')
    expect(buttons[0]).toHaveAttribute('aria-pressed', 'false')
    await userEvent.click(buttons[2]!)
    expect(onNavigate).toHaveBeenCalledWith('cases')
  })

  it('opens the menu with what the rail held besides the screens: the workspace, the demo, Leave, Help and Theme', async () => {
    windowOf(true)
    const onLeave = vi.fn()
    renderShell({ onLeave })

    await userEvent.click(screen.getByRole('button', { name: 'Menu' }))

    const menu = screen.getByRole('dialog', { name: 'Menu' })
    expect(within(menu).getByText(POLICY.title)).toHaveAttribute('dir', 'rtl')
    expect(within(menu).getByRole('region', { name: 'Guided demo' })).toBeInTheDocument()
    expect(within(menu).getByText('Analyst')).toBeInTheDocument()
    expect(within(menu).getByRole('button', { name: 'Help' })).toBeInTheDocument()
    expect(within(menu).getByRole('button', { name: 'Theme' })).toBeInTheDocument()
    await userEvent.click(within(menu).getByRole('button', { name: 'Leave' }))
    expect(onLeave).toHaveBeenCalledOnce()
  })

  // Step 1 runs on Policies, the screen the workspace opens on: a step run from the menu closes it on its own screen too
  it('closes the menu when a step of the guided demo runs from it, even on the screen it was opened on', async () => {
    windowOf(true)
    const { rerender } = renderShell({ current: 'policies', demoRuns: 0 })

    await userEvent.click(screen.getByRole('button', { name: 'Menu' }))
    expect(screen.getByRole('dialog', { name: 'Menu' })).toBeInTheDocument()
    rerender(
      <QueryClientProvider client={new QueryClient()}>
        <AppShell
          current="policies"
          onNavigate={vi.fn()}
          onLeave={vi.fn()}
          policy={POLICY}
          demoRuns={1}
          aside={<section aria-label="Guided demo">Guided demo</section>}
        >
          <p>The workspace</p>
        </AppShell>
      </QueryClientProvider>,
    )

    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument()
  })

  it('keeps the rail from 721px up', () => {
    windowOf(false)
    renderShell()

    expect(document.querySelector('.rail')).not.toBeNull()
    expect(document.querySelector('.phone__top')).toBeNull()
  })
})

describe('AppShell.css, the phone', () => {
  // The spec's phone block, but for the frame it draws the phone in: the product's phone is the window itself; the
  // chips at 24px are section 10's touch targets, written into the block in v3.9
  it("carries the spec's phone rules, with the spec's declarations", () => {
    const phone = specRules('/* Phone (', '/* decide a case').filter(
      ([selector]) => selector !== '.phone',
    )

    expect(phone.map(([selector]) => selector)).toEqual([
      '.phone__top',
      '.phone__top svg',
      '.phone__screens',
      '.phone__screens .btn',
      '.phone__body',
      '.phone .sheet',
      '.phone .ws-header',
      '.phone .figures',
      '.phone .figure:nth-child(2)',
      '.phone .figure',
      '.phone a.chip',
      '.phone button.chip',
    ])
    expect(unported(stylesheet('shared/layout/AppShell.css'), phone)).toEqual([])
  })
})

describe('AppShell.css, the rail', () => {
  // The spec's shell block, but for .shell itself: the product's shell is the window (its height and min-height are
  // the product's own, under the ported block)
  it("carries the spec's shell and rail rules, with the spec's declarations", () => {
    const shell = specRules('/* Shell */', '/* Guided demo strip').filter(
      ([selector]) => selector !== '.shell',
    )

    expect(shell.map(([selector]) => selector)).toEqual([
      '.rail',
      '.rail__brand',
      '.rail__brand svg',
      '.rail__brand .brand-mark',
      '.rail__nav',
      '.rail__link',
      '.rail__link:hover',
      ".rail__link[aria-current='page']",
      ".rail__link[aria-current='page']::before",
      '.rail__count',
      '.rail__count--pending',
      '.rail__workspace',
      '.rail__workspace .name',
      '.rail__workspace .line',
      '.rail__foot',
      '.rail__me',
      '.rail__me .actor',
      '.rail__me .btn',
      '.rail__tools',
      '.rail__tools .btn',
    ])
    expect(unported(stylesheet('shared/layout/AppShell.css'), shell)).toEqual([])
  })

  // the spec, section 08 (v3.3, the owner's ask of 2026-09-29): the lockup at 36px, the screens and the workspace's name
  // in the working text, 14px, and the phone's lockup at 24px
  it('draws the lockup at 36px and the screens at 14px, and the phone lockup at 24px', () => {
    const css = stylesheet('shared/layout/AppShell.css')

    expect(rule(css, '.rail__brand svg').height).toBe('36px')
    expect(rule(css, '.rail__link')).toMatchObject({
      height: '34px',
      'font-size': 'var(--text-base)',
    })
    expect(resolved(rule(css, '.rail__link')['font-size']!, 'light')).toBe('0.875rem')
    expect(rule(css, '.rail__workspace .name')['font-size']).toBe('var(--text-base)')
    expect(rule(css, '.rail__me')['font-size']).toBe('var(--text-base)')
    expect(rule(css, '.phone__top svg').height).toBe('24px')
  })
})
