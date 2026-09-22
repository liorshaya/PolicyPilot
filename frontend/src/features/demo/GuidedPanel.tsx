import { useState } from 'react'
import { Button } from '../../shared/ui/Button'
import { DEMO_STEPS, type DemoStep } from './steps'
import './GuidedPanel.css'

interface GuidedPanelProps {
  /** The step the workspace is on, so the panel shows where the demo has got to. */
  current: DemoStep['id'] | null
  onRun: (step: DemoStep) => void
}

/**
 * The guided demo panel (the brief FR-23; Document 2, Frontend Architecture): a collapsible panel that lists the
 * four scripted steps as one-click actions. It exists so the demo can be driven from an interviewer's machine,
 * and it adds no logic of its own — a step navigates to a screen and fills in what a presenter would type.
 *
 * <p>Collapsed by default: the workspace is the demo, and the panel is the remote control beside it.
 */
export function GuidedPanel({ current, onRun }: GuidedPanelProps) {
  const [open, setOpen] = useState(false)

  return (
    <section className={`guided${open ? ' guided--open' : ''}`} aria-label="Guided demo">
      <button
        type="button"
        className="guided__toggle"
        aria-expanded={open}
        aria-controls="guided-steps"
        onClick={() => setOpen((shown) => !shown)}
      >
        <span className="guided__title">Guided demo</span>
        <span className="guided__hint">{open ? 'Hide' : 'Four steps, one click each'}</span>
      </button>
      <ol className="guided__steps" id="guided-steps" hidden={!open}>
        {DEMO_STEPS.map((step) => (
          <li
            key={step.id}
            className={`guided__step${step.id === current ? ' guided__step--current' : ''}`}
            aria-current={step.id === current ? 'step' : undefined}
          >
            <span className="guided__number" aria-hidden="true">
              {step.id}
            </span>
            <span className="guided__body">
              <span className="guided__step-title">{step.title}</span>
              <span className="guided__what">{step.what}</span>
            </span>
            {step.soon === true ? (
              <span className="guided__soon">Day 14</span>
            ) : (
              <Button variant="secondary" onClick={() => onRun(step)}>
                Run
              </Button>
            )}
          </li>
        ))}
      </ol>
    </section>
  )
}
