import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useProvider } from '../../api/queries'
import type { ProviderResponse } from '../../api/types'
import { chooseTheme, type Theme } from '../../styles/theme'
import { contentAttributes, type ContentLanguage } from '../i18n/direction'
import { Actor, PERSON } from '../ui/Actor'
import { Button } from '../ui/Button'
import { Icon } from '../ui/Icon'
import { Kbd } from '../ui/Kbd'
import { Logo } from '../ui/Logo'
import { Popover } from '../ui/Overlay'
import { VersionTag } from '../ui/StatusTag'
import { VERSION_LABELS, type VersionStatus } from '../ui/decisionLabels'
import { SCREENS, type ScreenId } from './screens'
import { usePhone } from './usePhone'
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
  /** The workspace's version, which the phone's top bar names beside the lockup (the spec, section 10). */
  version?: { status: VersionStatus; versionNo: number } | null
  /** How many steps of the guided demo have run: a step run from the phone's menu closes it, on any screen. */
  demoRuns?: number
  /** The guided demo strip, under the workspace block (Brief FR-23). */
  aside?: ReactNode
  /** Opens the palette: ⌘K, or Ctrl+K, from anywhere (the spec, section 08). */
  onOpenPalette?: () => void
  /** The palette while it is open, over the workspace. */
  palette?: ReactNode
  children: ReactNode
}

/** How the rail names each provider the profiles offer, and where its models run (Brief FR-21). */
const PROVIDERS: Record<string, { name: string; where: string }> = {
  openai: { name: 'OpenAI', where: 'cloud' },
  ollama: { name: 'Ollama', where: 'local' },
}

/** The screens the G shortcut reaches: G R, G C, G A. */
const GO_TO: Record<string, ScreenId> = { r: 'rules', c: 'cases', a: 'assistant' }

/** ⌘K, or Ctrl+K, opens the palette from anywhere, from a field too, as a command palette does (the spec, section 04). */
function usePaletteKey(onOpen: (() => void) | undefined) {
  useEffect(() => {
    if (onOpen === undefined) {
      return undefined
    }
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && !event.altKey && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        onOpen()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onOpen])
}

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
 * quiet buttons; the workspace on the end. Below 720px the rail becomes the phone's top bar (section 10): the lockup,
 * the version and a menu that holds what the rail held besides the screens, over a row of the six screens. The chrome
 * is English and left to right; only content blocks turn around.
 */
export function AppShell({
  current,
  onNavigate,
  onLeave,
  policy,
  findingsToAcknowledge = 0,
  version,
  demoRuns = 0,
  aside,
  onOpenPalette,
  palette,
  children,
}: AppShellProps) {
  const provider = useProvider()
  const phone = usePhone()
  const [helpAnchor, setHelpAnchor] = useState<HTMLElement | null>(null)
  // the menu belongs to the screen it was opened on and the steps run so far: a screen chosen, or a step run from it,
  // closes it, step 1 on Policies too
  const [menu, setMenu] = useState<{ anchor: HTMLElement; on: ScreenId; runs: number } | null>(null)
  const menuAnchor =
    menu !== null && menu.on === current && menu.runs === demoRuns ? menu.anchor : null
  const menuRef = useRef<HTMLButtonElement>(null)
  const [theme, setTheme] = useState<Theme>(() =>
    document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light',
  )
  useGoTo(onNavigate)
  usePaletteKey(onOpenPalette)

  const known = provider.data ? PROVIDERS[provider.data.provider] : undefined
  const providerName = known?.name ?? provider.data?.provider
  const providerLine = provider.data
    ? `Provider ${providerName}${known ? ` · ${known.where}` : ''}`
    : null
  const count = (screen: ScreenId) =>
    screen === 'rules' && findingsToAcknowledge > 0 ? (
      <>
        {' '}
        <span
          className="rail__count"
          title={`${findingsToAcknowledge} finding${findingsToAcknowledge === 1 ? '' : 's'} to acknowledge`}
        >
          {findingsToAcknowledge}
        </span>
      </>
    ) : null
  const workspaceLines = (
    <div className="rail__workspace">
      {policy ? (
        <span className="name" {...contentAttributes(policy.language)}>
          {policy.title}
        </span>
      ) : null}
      {providerLine ? <span className="line">{providerLine}</span> : null}
    </div>
  )
  const foot = (
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
          onClick={(event) => {
            if (phone) {
              // on a phone the legend takes the menu's place, beside the button that opened the menu
              setMenu(null)
              setHelpAnchor(helpAnchor ? null : menuRef.current)
            } else {
              setHelpAnchor(helpAnchor ? null : event.currentTarget)
            }
          }}
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
  )
  const help = helpAnchor ? (
    <Popover
      anchor={helpAnchor}
      label="Help"
      align={phone ? 'end' : 'start'}
      onClose={() => setHelpAnchor(null)}
    >
      <Legend provider={provider.data} providerLine={providerLine} />
    </Popover>
  ) : null

  if (phone) {
    return (
      <div className="phone">
        <SkipLink />
        <header className="phone__top">
          <Logo />
          <span className="phone__side">
            {version ? (
              <span
                className={`vstatus vstatus--${version.status.toLowerCase()}`}
                title={`${VERSION_LABELS[version.status]} v${String(version.versionNo)}`}
              >
                {`v${String(version.versionNo)}`}
              </span>
            ) : null}
            <button
              ref={menuRef}
              type="button"
              className="btn btn--quiet btn--sm"
              aria-label="Menu"
              aria-expanded={menuAnchor !== null}
              onClick={(event) =>
                setMenu(
                  menuAnchor ? null : { anchor: event.currentTarget, on: current, runs: demoRuns },
                )
              }
            >
              <Icon name="menu" />
            </button>
          </span>
        </header>
        <nav className="phone__screens" aria-label="Workspace">
          {SCREENS.map((screen) => (
            <Button
              key={screen.id}
              variant={screen.id === current ? 'secondary' : 'quiet'}
              aria-pressed={screen.id === current}
              title={screen.hint}
              onClick={() => onNavigate(screen.id)}
            >
              {screen.label}
              {count(screen.id)}
            </Button>
          ))}
        </nav>
        <main className="workspace phone__body" id="workspace" tabIndex={-1}>
          {children}
        </main>
        {menuAnchor ? (
          <Popover anchor={menuAnchor} label="Menu" align="end" onClose={() => setMenu(null)}>
            <div className="phone__menu">
              {workspaceLines}
              {aside}
              {foot}
            </div>
          </Popover>
        ) : null}
        {help}
        {palette}
      </div>
    )
  }

  return (
    <div className="shell">
      <SkipLink />
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
                {count(screen.id)}
              </button>
            </li>
          ))}
        </ul>
        {workspaceLines}
        {aside}
        {foot}
      </nav>
      <main className="workspace" id="workspace" tabIndex={-1}>
        {children}
      </main>
      {help}
      {palette}
    </div>
  )
}

/**
 * The keyboard's first stop (the spec, section 08, Rail, v3.9): it moves the focus into the workspace. The address bar
 * names the screen (#/rules), so the link's own fragment is not followed, which took the reader to Policies.
 */
function SkipLink() {
  return (
    <a
      className="sr-only skip"
      href="#workspace"
      onClick={(event) => {
        event.preventDefault()
        document.getElementById('workspace')?.focus()
      }}
    >
      Skip to the workspace
    </a>
  )
}

/**
 * What Help opens (the spec, section 04, "The legend and the shortcuts"): the four marks, the provider's models, the
 * two states of a version, and the shortcuts the product answers.
 */
function Legend({
  provider,
  providerLine,
}: {
  provider: ProviderResponse | undefined
  providerLine: string | null
}) {
  return (
    <>
      <div className="legend">
        <Actor kind="model">The model proposes and explains</Actor>
        <Actor kind="engine">The rules engine decides</Actor>
        <Actor kind="person">A person approves</Actor>
        <Actor kind="system">The system records and resets</Actor>
        {provider ? (
          <span className="muted legend__provider">
            {providerLine} · authoring, review and changes on{' '}
            <span className="mono">{provider.chatModels.strong}</span>, answers and explanations on{' '}
            <span className="mono">{provider.chatModels.fast}</span>, embeddings{' '}
            <span className="mono">{provider.embeddingModel}</span> ({provider.embeddingDimension})
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
          <span>Go to anything by id</span>
          <span className="keys">
            <Kbd>⌘</Kbd>
            <Kbd>K</Kbd>
          </span>
        </div>
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
          <span>Close the margin</span>
          <span className="keys">
            <Kbd>Esc</Kbd>
          </span>
        </div>
        <div>
          <span>Previous / next case</span>
          <span className="keys">
            <Kbd>[</Kbd> <Kbd>]</Kbd>
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
    </>
  )
}
