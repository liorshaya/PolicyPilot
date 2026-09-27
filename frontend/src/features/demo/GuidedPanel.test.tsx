import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { rule, stylesheet } from '../../test/css'
import { GuidedPanel } from './GuidedPanel'
import { DEMO_STEPS } from './steps'

const css = stylesheet('features/demo/GuidedPanel.css')

/**
 * The guided demo panel (the brief FR-23; Document 2: "the four scripted steps as one-click actions"). The
 * expected values are the brief's own demo script: four steps, named Author, Decide, Ask and Change, each of them
 * offered since step 4 arrived on day 14 of the work plan.
 */
describe('the guided demo panel', () => {
  it('lists the four scripted steps, and offers every one of them', async () => {
    render(<GuidedPanel current={null} onRun={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /guided demo/i }))

    const steps = screen.getAllByRole('listitem')

    expect(steps).toHaveLength(4)
    expect(steps.map((step) => step.textContent)).toEqual([
      expect.stringContaining('Author'),
      expect.stringContaining('Decide'),
      expect.stringContaining('Ask'),
      expect.stringContaining('Change'),
    ])
    expect(screen.getAllByRole('button', { name: 'Run' })).toHaveLength(4)
    expect(screen.queryByText('Day 14')).not.toBeInTheDocument()
  })

  it('is collapsed until it is opened, so the workspace is what the audience sees', () => {
    render(<GuidedPanel current={null} onRun={vi.fn()} />)

    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /guided demo/i })).toHaveAttribute(
      'aria-expanded',
      'false',
    )
  })

  it('hands back the step that was clicked, and nothing else', async () => {
    const onRun = vi.fn()
    render(<GuidedPanel current={null} onRun={onRun} />)
    await userEvent.click(screen.getByRole('button', { name: /guided demo/i }))

    await userEvent.click(screen.getAllByRole('button', { name: 'Run' })[2]!)

    expect(onRun).toHaveBeenCalledTimes(1)
    expect(onRun).toHaveBeenCalledWith(DEMO_STEPS[2])
  })

  it('marks the step the workspace is on, so a presenter can see where the demo has got to', async () => {
    render(<GuidedPanel current={2} onRun={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /guided demo/i }))

    const current = screen.getAllByRole('listitem').filter((step) => step.ariaCurrent === 'step')

    expect(current).toHaveLength(1)
    expect(current[0]!.textContent).toContain('Decide')
  })

  it('closes again, so the panel can be put away mid-demo', async () => {
    render(<GuidedPanel current={null} onRun={vi.fn()} />)
    const toggle = screen.getByRole('button', { name: /guided demo/i })

    await userEvent.click(toggle)
    await userEvent.click(toggle)

    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
  })

  // The Register spec, section 08: "collapsed to one line, it opens to the four scripted steps with the current one
  // marked; it never covers the sheet". Expected: the one line names the step the demo is on, 1 before any has run
  it('is one line when collapsed: Guided demo, and the step of four the demo is on', () => {
    const { rerender } = render(<GuidedPanel current={null} onRun={vi.fn()} />)
    const toggle = screen.getByRole('button', { name: /guided demo/i })

    expect(toggle).toHaveClass('demo__toggle')
    expect(toggle).toHaveTextContent(/^Guided demo step 1 of 4$/)

    rerender(<GuidedPanel current={3} onRun={vi.fn()} />)
    expect(screen.getByRole('button', { name: /guided demo/i })).toHaveTextContent(/step 3 of 4$/)
  })

  it('marks the steps before the current one as done, and the current one with its own button', async () => {
    render(<GuidedPanel current={2} onRun={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /guided demo/i }))
    const steps = screen.getAllByRole('listitem')

    expect(steps[0]).toHaveClass('demo__step', 'demo__step--done')
    expect(steps[1]).toHaveClass('demo__step--current')
    expect(steps[2]).not.toHaveClass('demo__step--done')
    expect(steps.map((step) => step.querySelector('button')?.className)).toStrictEqual([
      'btn btn--quiet btn--sm',
      'btn btn--secondary btn--sm',
      'btn btn--quiet btn--sm',
      'btn btn--quiet btn--sm',
    ])
  })

  it("sits in the rail's own flow, so it never covers the sheet", () => {
    expect(rule(css, '.demo').position).toBeUndefined()
    expect(rule(css, '.demo__steps').position).toBeUndefined()
  })
})
