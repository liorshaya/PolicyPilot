import { useState } from 'react'
import { AccessGate } from './shared/gate/AccessGate'

/**
 * The access gate until the code is exchanged, then the signed-in page; the feature screens replace that page from
 * day 6 (Work Plan). The session is the HttpOnly cookie, so the app keeps no token of its own.
 */
export function App() {
  const [entered, setEntered] = useState(false)

  if (!entered) {
    return <AccessGate onEntered={() => setEntered(true)} />
  }
  return (
    <main className="gate">
      <section className="gate__card" aria-labelledby="signed-in-title">
        <h1 id="signed-in-title" className="gate__title">
          PolicyPilot
        </h1>
        <p className="gate__lead">You are signed in. The policy workspace opens here.</p>
      </section>
    </main>
  )
}
