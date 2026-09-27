import { useState, type FormEvent } from 'react'
import { exchangeAccessCode } from '../../api/auth'
import { Actor } from '../ui/Actor'
import { Button } from '../ui/Button'
import { Field } from '../ui/Field'
import { Logo } from '../ui/Logo'
import './AccessGate.css'
import { refusalMessage } from './refusalMessage'

/** The access code is eight lowercase characters (Document 2, Presentation scenarios); the field allows a little slack. */
const CODE_MAX_LENGTH = 32

interface AccessGateProps {
  /** Called once the API has set the session cookie. */
  onEntered: () => void
}

/**
 * The access gate: the single screen a visitor sees before the demo (Document 2, Frontend Architecture, key decision 6),
 * as the Register composes it (the spec, section 10): one sheet on paper with the two-tone lockup, the title, the one
 * place the sentence is written out, the field in mono, the primary at 36px, the refusal in place, the honesty line and
 * the three marks as a foot. The code is exchanged at POST /api/v1/auth/code for the session cookie (Document 5).
 */
export function AccessGate({ onEntered }: AccessGateProps) {
  const [code, setCode] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }
    setSubmitting(true)
    setMessage(null)
    const result = await exchangeAccessCode(code.trim())
    setSubmitting(false)
    if (result.kind === 'entered') {
      onEntered()
    } else {
      setMessage(refusalMessage(result))
    }
  }

  return (
    <main className="gate">
      <section className="gate__sheet" aria-labelledby="gate-title">
        <div className="gate__brand">
          <Logo />
        </div>
        <div>
          <h1 id="gate-title" className="gate__title">
            Enter the workspace
          </h1>
          <p className="gate__lead">
            The model proposes and explains, the rules engine decides, a person approves every
            policy change.
          </p>
        </div>
        <form className="gate__form" onSubmit={(event) => void handleSubmit(event)}>
          <Field label="Access code" htmlFor="access-code" error={message}>
            <input
              id="access-code"
              name="accessCode"
              className="input input--mono"
              type="password"
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
              maxLength={CODE_MAX_LENGTH}
              value={code}
              onChange={(event) => setCode(event.target.value)}
              aria-invalid={message ? true : undefined}
            />
          </Field>
          <Button
            type="submit"
            variant="primary"
            size="lg"
            busy={submitting}
            disabled={code.trim() === ''}
          >
            Enter
          </Button>
        </form>
        <p className="gate__foot">
          Enter the code you received with the invitation. Everything behind this gate is synthetic
          data.
        </p>
        <div className="gate__marks">
          <Actor kind="model">proposes</Actor>
          <Actor kind="engine">decides</Actor>
          <Actor kind="person">approves</Actor>
        </div>
      </section>
    </main>
  )
}
