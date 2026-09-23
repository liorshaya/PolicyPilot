import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { GuidedPanel } from './GuidedPanel'
import { DEMO_STEPS } from './steps'

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
})
