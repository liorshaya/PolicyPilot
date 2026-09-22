import { render } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { useDemoStep } from './useDemoStep'

/**
 * A scripted step is a click a presenter would make, so it happens once (Document 2: the panel "adds no logic of
 * its own"). Step 2 runs the 200 cases; firing on every render would run them again and again.
 */
describe('useDemoStep', () => {
  function Harness({ asked, run }: { asked: boolean; run: () => void }) {
    const [renders, setRenders] = useState(0)
    useDemoStep(asked, run)
    return (
      <button type="button" onClick={() => setRenders((count) => count + 1)}>
        rendered {renders}
      </button>
    )
  }

  it('runs the step once, however many times the screen renders', () => {
    const run = vi.fn()
    const { rerender } = render(<Harness asked run={run} />)

    rerender(<Harness asked run={run} />)
    rerender(<Harness asked run={run} />)

    expect(run).toHaveBeenCalledTimes(1)
  })

  it('does not run a step that was not asked for', () => {
    const run = vi.fn()

    render(<Harness asked={false} run={run} />)

    expect(run).not.toHaveBeenCalled()
  })

  it('runs it again when the same step is asked for a second time', () => {
    const run = vi.fn()
    const { rerender } = render(<Harness asked run={run} />)

    rerender(<Harness asked={false} run={run} />)
    rerender(<Harness asked run={run} />)

    expect(run).toHaveBeenCalledTimes(2)
  })

  it('tells the panel the step was handled, once', () => {
    const onHandled = vi.fn()
    function Told({ asked }: { asked: boolean }) {
      useDemoStep(asked, () => undefined, onHandled)
      return null
    }
    const { rerender } = render(<Told asked />)

    rerender(<Told asked />)

    expect(onHandled).toHaveBeenCalledTimes(1)
  })
})
