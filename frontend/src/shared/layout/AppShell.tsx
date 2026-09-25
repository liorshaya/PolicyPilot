import type { ReactNode } from 'react'
import { Logo } from '../ui/Logo'
import { ProviderBadge } from './ProviderBadge'
import { SCREENS, type ScreenId } from './screens'
import './AppShell.css'

interface AppShellProps {
  current: ScreenId
  onNavigate: (screen: ScreenId) => void
  /** Sits under the navigation: the guided demo panel, which drives the scripted steps (Brief FR-23). */
  aside?: ReactNode
  children: ReactNode
}

/**
 * The frame every screen sits in (the brief, Layout and navigation): a compact sidebar on the left, the workspace
 * on the right, with the model provider under the logo (Document 2: GET /system/provider, shown in the UI header).
 * The chrome is English and left-to-right; only content blocks turn around.
 */
export function AppShell({ current, onNavigate, aside, children }: AppShellProps) {
  return (
    <div className="shell">
      <a className="shell__skip" href="#workspace">
        Skip to the workspace
      </a>
      <nav className="shell__sidebar" aria-label="Workspace">
        <div className="shell__brand">
          <Logo tone="white" width={140} />
          <ProviderBadge />
        </div>
        <ul className="shell__nav">
          {SCREENS.map((screen) => (
            <li key={screen.id}>
              <button
                type="button"
                className={`shell__link${screen.id === current ? ' shell__link--current' : ''}`}
                aria-current={screen.id === current ? 'page' : undefined}
                title={screen.hint}
                onClick={() => onNavigate(screen.id)}
              >
                <span>{screen.label}</span>
              </button>
            </li>
          ))}
        </ul>
        {aside === undefined ? null : <div className="shell__aside">{aside}</div>}
        <p className="shell__principle">
          The model proposes and explains. The rules engine decides. A person approves.
        </p>
      </nav>
      <main className="shell__workspace" id="workspace">
        {children}
      </main>
    </div>
  )
}
