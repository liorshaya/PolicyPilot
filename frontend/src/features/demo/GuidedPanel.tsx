import { useState } from 'react'
import { Button } from '../../shared/ui/Button'
import { DEMO_STEPS, type DemoStep } from './steps'
import './GuidedPanel.css'

interface GuidedPanelProps {
  /** The step the workspace is on, so the strip shows where the demo has got to. */
  current: DemoStep['id'] | null
  onRun: (step: DemoStep) => void
  /**
   * Whether the steps are shown, when the app keeps it: on a phone the strip stands in the menu, which mounts it anew
   * each time it opens (the spec, section 10). Without it the strip keeps its own, collapsed at first.
   */
  open?: boolean
  onToggle?: (open: boolean) => void
}

/**
 * The guided demo strip of the rail (the brief FR-23; Document 2, Frontend Architecture; the Register spec, section 08):
 * collapsed to one line that says which step of four the demo is on, it opens to the four scripted steps as one-click
 * actions, the steps before the current one done and the current one marked. It sits in the rail and never covers the
 * sheet, and it adds no logic of its own: a step navigates to a screen and fills in what a presenter would type.
 */
export function GuidedPanel({ current, onRun, open: kept, onToggle }: GuidedPanelProps) {
  const [own, setOwn] = useState(false)
  const open = kept ?? own

  return (
    <section className="demo" aria-label="Guided demo">
      <button
        type="button"
        className="demo__toggle"
        aria-expanded={open}
        aria-controls="guided-steps"
        onClick={() => {
          setOwn(!open)
          onToggle?.(!open)
        }}
      >
        <span>Guided demo</span>{' '}
        <span className="mono">{`step ${current ?? 1} of ${DEMO_STEPS.length}`}</span>
      </button>
      <ol className="demo__steps" id="guided-steps" hidden={!open}>
        {DEMO_STEPS.map((step) => {
          const done = current !== null && step.id < current
          const now = step.id === current
          return (
            <li
              key={step.id}
              className={`demo__step${done ? ' demo__step--done' : ''}${now ? ' demo__step--current' : ''}`}
              aria-current={now ? 'step' : undefined}
            >
              <span className="demo__n" aria-hidden="true">
                {step.id}
              </span>
              <span className="demo__title">{step.title}</span>
              <Button variant={now ? 'secondary' : 'quiet'} size="sm" onClick={() => onRun(step)}>
                Run
              </Button>
              <span className="demo__what">{step.what}</span>
            </li>
          )
        })}
      </ol>
    </section>
  )
}
