import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { rule, stylesheet } from '../../test/css'
import { rtlSnapshot } from '../../test/rtlSnapshot'
import { AUTH_CODE_URL } from '../../api/auth'
import { server } from '../../test/msw/server'
import { AccessGate } from './AccessGate'

const gateCss = stylesheet('shared/gate/AccessGate.css')
const buttonCss = stylesheet('shared/ui/Button.css')

describe('AccessGate', () => {
  it('renders the logo, the code field and a disabled button', () => {
    render(<AccessGate onEntered={vi.fn()} />)

    expect(screen.getByRole('img', { name: 'PolicyPilot' })).toBeInTheDocument()
    expect(
      screen.getByRole('heading', { level: 1, name: 'Enter the workspace' }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Access code')).toHaveAttribute('type', 'password')
    expect(screen.getByRole('button', { name: 'Enter' })).toBeDisabled()
  })

  it('enables the button once a code is typed', async () => {
    const user = userEvent.setup()
    render(<AccessGate onEntered={vi.fn()} />)

    await user.type(screen.getByLabelText('Access code'), 'demo1234')

    expect(screen.getByRole('button', { name: 'Enter' })).toBeEnabled()
  })

  it('enters when the API accepts the code', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 204 })))
    const onEntered = vi.fn()
    const user = userEvent.setup()
    render(<AccessGate onEntered={onEntered} />)

    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(onEntered).toHaveBeenCalledOnce()
  })

  it('says the code is not valid and keeps the field when the API refuses it', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 401 })))
    const onEntered = vi.fn()
    const user = userEvent.setup()
    render(<AccessGate onEntered={onEntered} />)

    await user.type(screen.getByLabelText('Access code'), 'wrongone')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('That code is not valid.')
    expect(screen.getByLabelText('Access code')).toHaveValue('wrongone')
    expect(onEntered).not.toHaveBeenCalled()
  })

  it('tells a locked-out visitor when to try again', async () => {
    server.use(
      http.post(
        AUTH_CODE_URL,
        () => new HttpResponse(null, { status: 429, headers: { 'Retry-After': '840' } }),
      ),
    )
    const user = userEvent.setup()
    render(<AccessGate onEntered={vi.fn()} />)

    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Try again in 14 minutes.')
  })

  it('never repeats the typed code in its message', async () => {
    server.use(http.post(AUTH_CODE_URL, () => new HttpResponse(null, { status: 401 })))
    const user = userEvent.setup()
    render(<AccessGate onEntered={vi.fn()} />)

    await user.type(screen.getByLabelText('Access code'), 'secretxy')
    await user.click(screen.getByRole('button', { name: 'Enter' }))

    expect(await screen.findByRole('alert')).not.toHaveTextContent('secretxy')
  })

  // The Register spec, section 10, the access gate: "the two-tone lockup, the title, the sentence (this is the one
  // place it is written out), the field in mono with a password input and no autocomplete, the primary at 36px, the
  // refusal in place, the honesty line, and the three marks as a foot. No eyebrow, no illustration."
  it('draws the two-tone lockup, the title and the one sentence', () => {
    render(<AccessGate onEntered={vi.fn()} />)
    const logo = screen.getByRole('img', { name: 'PolicyPilot' })

    expect(logo.tagName).toBe('svg')
    expect(logo.querySelector('.brand-mark')).not.toBeNull()
    expect(rule(gateCss, '.gate__brand .brand-mark').color).toBe('var(--accent)')
    expect(
      screen.getByText(
        'The model proposes and explains, the rules engine decides, a person approves every policy change.',
      ),
    ).toHaveClass('gate__lead')
  })

  it('sets the field in mono, a password with no autocomplete, and Enter as the primary at 36px', () => {
    render(<AccessGate onEntered={vi.fn()} />)
    const field = screen.getByLabelText('Access code')

    expect(field).toHaveClass('input', 'input--mono')
    expect(field).toHaveAttribute('autocomplete', 'off')
    expect(screen.getByRole('button', { name: 'Enter' })).toHaveClass('btn--primary', 'btn--lg')
    expect(rule(buttonCss, '.btn--lg').height).toBe('var(--control-h-lg)')
  })

  it('says the demo could not be reached when the API does not answer, in place under the field', async () => {
    server.use(http.post(AUTH_CODE_URL, () => HttpResponse.error()))
    const user = userEvent.setup()
    render(<AccessGate onEntered={vi.fn()} />)

    await user.type(screen.getByLabelText('Access code'), 'qwertyui')
    await user.click(screen.getByRole('button', { name: 'Enter' }))
    const refusal = await screen.findByRole('alert')

    expect(refusal).toHaveTextContent('The demo could not be reached. Try again in a moment.')
    expect(refusal).toHaveClass('field__error')
    expect(refusal.querySelector('svg')).toHaveAttribute('data-icon', 'warn')
  })

  it('ends with the honesty line and the three marks as a foot, with no eyebrow and no illustration', () => {
    const { container } = render(<AccessGate onEntered={vi.fn()} />)

    expect(
      screen.getByText(
        'Enter the code you received with the invitation. Everything behind this gate is synthetic data.',
      ),
    ).toHaveClass('gate__foot')
    const marks = container.querySelector('.gate__marks')!
    expect([...marks.children].map((mark) => [mark.className, mark.textContent])).toStrictEqual([
      ['actor actor--model', 'proposes'],
      ['actor actor--engine', 'decides'],
      ['actor actor--person', 'approves'],
    ])
    expect(screen.queryByText(/protected demo/i)).not.toBeInTheDocument()
    expect(container.querySelectorAll('img, svg[role="img"]')).toHaveLength(1)
  })

  it('reads as the RTL snapshot of the gate', () => {
    const { container } = render(<AccessGate onEntered={vi.fn()} />)

    expect(rtlSnapshot(container)).toMatchSnapshot()
  })
})
