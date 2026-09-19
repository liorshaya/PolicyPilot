import { useState, type FormEvent } from 'react'
import { exchangeAccessCode } from '../../api/auth'
import './AccessGate.css'
import { refusalMessage } from './refusalMessage'

/** The access code is eight lowercase characters (Document 2, Presentation scenarios); the field allows a little slack. */
const CODE_MAX_LENGTH = 32

interface AccessGateProps {
  /** Called once the API has set the session cookie. */
  onEntered: () => void
}

/**
 * The access gate: the single screen a visitor sees before the demo (Document 2, Frontend Architecture, key
 * decision 6). The code is exchanged at POST /api/v1/auth/code for the session cookie (Document 5).
 */
export function AccessGate({ onEntered }: AccessGateProps) {
  const [code, setCode] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
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
      <section className="gate__card" aria-labelledby="gate-title">
        <p className="gate__eyebrow">Protected demo</p>
        <h1 id="gate-title" className="gate__title">
          PolicyPilot
        </h1>
        <p className="gate__lead">
          The model proposes and explains, the rules engine decides, a person approves every policy
          change.
        </p>
        <form className="gate__form" onSubmit={(event) => void handleSubmit(event)}>
          <label className="gate__label" htmlFor="access-code">
            Access code
          </label>
          <input
            id="access-code"
            name="accessCode"
            className="gate__input"
            type="password"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={CODE_MAX_LENGTH}
            value={code}
            onChange={(event) => setCode(event.target.value)}
            aria-describedby={message ? 'gate-message' : undefined}
          />
          <button
            type="submit"
            className="gate__button"
            disabled={code.trim() === '' || submitting}
          >
            {submitting ? 'Checking…' : 'Enter'}
          </button>
          {message && (
            <p id="gate-message" className="gate__message" role="alert">
              {message}
            </p>
          )}
        </form>
        <p className="gate__hint">
          Enter the code you received with the invitation. Everything behind this gate is synthetic
          data.
        </p>
      </section>
    </main>
  )
}
