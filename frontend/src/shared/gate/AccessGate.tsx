import { useState, type FormEvent } from 'react'
import './AccessGate.css'

/** The access code is eight lowercase characters (Document 2, Presentation scenarios); the field allows a little slack. */
const CODE_MAX_LENGTH = 32

/**
 * The access gate: the single screen a visitor sees before the demo (Document 2, Frontend Architecture, key
 * decision 6). On day 1 the gate is static; the exchange of the code for the session cookie
 * (POST /auth/code, Document 5) arrives on day 4.
 */
export function AccessGate() {
  const [code, setCode] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
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
        <form className="gate__form" onSubmit={handleSubmit}>
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
          />
          <button type="submit" className="gate__button" disabled={code.trim() === ''}>
            Enter
          </button>
        </form>
        <p className="gate__hint">
          Enter the code you received with the invitation. Everything behind this gate is synthetic
          data.
        </p>
      </section>
    </main>
  )
}
