import { useEffect, useState, type ReactNode } from 'react'
import { useProvider } from '../../api/queries'
import { chooseTheme, type Theme } from '../../styles/theme'
import { contentAttributes, type ContentLanguage } from '../i18n/direction'
import { Actor, PERSON } from '../ui/Actor'
import { Button } from '../ui/Button'
import { Kbd } from '../ui/Kbd'
import { Logo } from '../ui/Logo'
import { Popover } from '../ui/Overlay'
import { VersionTag } from '../ui/StatusTag'
import { SCREENS, type ScreenId } from './screens'
import './AppShell.css'

interface AppShellProps {
  current: ScreenId
  onNavigate: (screen: ScreenId) => void
  /** Back to the gate, in this tab; the session and its sandbox stay until the cookie expires. */
  onLeave: () => void
  /** The policy the workspace's rule set was written from, named in its own language. */
  policy?: { title: string; language: ContentLanguage } | null
  /** Blocking findings of the workspace's draft that wait to be acknowledged: the one count the rail shows. */
  findingsToAcknowledge?: number
  /** The guided demo strip, under the workspace block (Brief FR-23). */
  aside?: ReactNode
  children: ReactNode
}

/** How the rail names each provider the profiles offer, and where its models run (Brief FR-21). */
const PROVIDERS: Record<string, { name: string; where: string }> = {
  openai: { name: 'OpenAI', where: 'cloud' },
  ollama: { name: 'Ollama', where: 'local' },
}

/** The screens the G shortcut reaches: G R, G C, G A. */
const GO_TO: Record<string, ScreenId> = { r: 'rules', c: 'cases', a: 'assistant' }

/** G, then a screen's letter, goes to that screen; never while the reader types into a field. */
function useGoTo(onNavigate: (screen: ScreenId) => void) {
  useEffect(() => {
    let armed = false
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      const typing =
        target !== null &&
        (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
      if (typing || event.metaKey || event.ctrlKey || event.altKey) {
        armed = false
        return
      }
      const key = event.key.toLowerCase()
      if (armed) {
        armed = false
        const screen = GO_TO[key]
        if (screen !== undefined) {
          event.preventDefault()
          onNavigate(screen)
        }
      } else {
        armed = key === 'g'
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onNavigate])
}

/**
 * The frame every screen sits in (the spec, section 08): a paper rail on the start edge with the six screens, a count
 * only beside Rules for the findings to acknowledge, the workspace named by its policy with the provider line, the guided
 * demo strip and, at the foot, the person the product records, Leave, and the legend, shortcuts and theme behind two
 * quiet buttons; the workspace on the end. The chrome is English and left to right; only content blocks turn around.
 */
export function AppShell({
  current,
  onNavigate,
  onLeave,
  policy,
  findingsToAcknowledge = 0,
  aside,
  children,
}: AppShellProps) {
  const provider = useProvider()
  const [helpAnchor, setHelpAnchor] = useState<HTMLElement | null>(null)
  const [theme, setTheme] = useState<Theme>(() =>
    document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light',
  )
  useGoTo(onNavigate)

  const known = provider.data ? PROVIDERS[provider.data.provider] : undefined
  const providerName = known?.name ?? provider.data?.provider
  const providerLine = provider.data
    ? `Provider ${providerName}${known ? ` · ${known.where}` : ''}`
    : null

  return (
    <div className="shell">
      <a className="sr-only" href="#workspace">
        Skip to the workspace
      </a>
      <nav className="rail" aria-label="Workspace">
        <div className="rail__brand">
          <Logo />
        </div>
        <ul className="rail__nav">
          {SCREENS.map((screen) => (
            <li key={screen.id}>
              <button
                type="button"
                className="rail__link"
                aria-current={screen.id === current ? 'page' : undefined}
                title={screen.hint}
                onClick={() => onNavigate(screen.id)}
              >
                <span>{screen.label}</span>
                {screen.id === 'rules' && findingsToAcknowledge > 0 ? (
                  <>
                    {' '}
                    <span
                      className="rail__count"
                      title={`${findingsToAcknowledge} finding${findingsToAcknowledge === 1 ? '' : 's'} to acknowledge`}
                    >
                      {findingsToAcknowledge}
                    </span>
                  </>
                ) : null}
              </button>
            </li>
          ))}
        </ul>
        <div className="rail__workspace">
          {policy ? (
            <span className="name" {...contentAttributes(policy.language)}>
              {policy.title}
            </span>
          ) : null}
          {providerLine ? <span className="line">{providerLine}</span> : null}
        </div>
        {aside}
        <div className="rail__foot">
          <div className="rail__me">
            <Actor kind="person">
              <strong>{PERSON}</strong>
            </Actor>
            <Button variant="quiet" size="sm" onClick={onLeave}>
              Leave
            </Button>
          </div>
          <div className="rail__tools">
            <Button
              variant="quiet"
              size="sm"
              icon="help"
              aria-expanded={helpAnchor !== null}
              onClick={(event) => setHelpAnchor(helpAnchor ? null : event.currentTarget)}
            >
              Help
            </Button>
            <Button
              variant="quiet"
              size="sm"
              icon={theme === 'dark' ? 'sun' : 'moon'}
              onClick={() => {
                const next: Theme = theme === 'dark' ? 'light' : 'dark'
                chooseTheme(next)
                setTheme(next)
              }}
            >
              Theme
            </Button>
          </div>
        </div>
      </nav>
      <main className="workspace" id="workspace">
        {children}
      </main>
      {helpAnchor ? (
        <Popover anchor={helpAnchor} label="Help" onClose={() => setHelpAnchor(null)}>
          <div className="legend">
            <Actor kind="model">The model proposes and explains</Actor>
            <Actor kind="engine">The rules engine decides</Actor>
            <Actor kind="person">A person approves</Actor>
            <Actor kind="system">The system records and resets</Actor>
            {provider.data ? (
              <span className="muted legend__provider">
                {providerLine} · authoring, review and changes on{' '}
                <span className="mono">{provider.data.chatModels.strong}</span>, answers and
                explanations on <span className="mono">{provider.data.chatModels.fast}</span>,
                embeddings <span className="mono">{provider.data.embeddingModel}</span> (
                {provider.data.embeddingDimension})
              </span>
            ) : null}
            <span className="legend__state">
              <VersionTag status="DRAFT" />
              <span className="muted">dashed: nothing decides with it yet</span>
            </span>
            <span className="legend__state">
              <VersionTag status="PUBLISHED" />
              <span className="muted">solid: the engine runs it</span>
            </span>
          </div>
          <div className="shortcuts">
            <div>
              <span>Filter the list</span>
              <span className="keys">
                <Kbd>/</Kbd>
              </span>
            </div>
            <div>
              <span>Select next / previous row</span>
              <span className="keys">
                <Kbd>↓</Kbd>
                <Kbd>↑</Kbd>
              </span>
            </div>
            <div>
              <span>Open the row in the margin</span>
              <span className="keys">
                <Kbd>↵</Kbd>
              </span>
            </div>
            <div>
              <span>Go to Rules / Cases / Assistant</span>
              <span className="keys">
                <Kbd>G</Kbd>
                <Kbd>R</Kbd> <Kbd>C</Kbd> <Kbd>A</Kbd>
              </span>
            </div>
          </div>
        </Popover>
      ) : null}
    </div>
  )
}
