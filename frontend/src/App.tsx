import { useCallback, useEffect, useState, type ComponentProps } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { leave, onSessionEnded, sessionHolds } from './api/auth'
import { AccessGate, GatePaper } from './shared/gate/AccessGate'
import { AppShell } from './shared/layout/AppShell'
import { SCREENS, type ScreenId } from './shared/layout/screens'
import { useWorkspace } from './shared/layout/useWorkspace'
import { PoliciesScreen } from './features/policy/PoliciesScreen'
import { CasesScreen } from './features/cases/CasesScreen'
import { RulesScreen } from './features/rules/RulesScreen'
import { ChatScreen } from './features/chat/ChatScreen'
import { ChangeScreen } from './features/change/ChangeScreen'
import { AuditScreen } from './features/audit/AuditScreen'
import { GuidedPanel } from './features/demo/GuidedPanel'
import type { DemoStep } from './features/demo/steps'
import { GoToPalette } from './features/palette/GoToPalette'
import type { PaletteTarget } from './features/palette/paletteItems'
import { PaletteContext } from './shared/ui/paletteContext'
import { applyTheme, storedTheme } from './styles/theme'

/** The screen the address bar names, so a screen can be linked to and the back button works. */
function screenFromHash(): ScreenId {
  const candidate = window.location.hash.replace('#/', '')
  return SCREENS.some((screen) => screen.id === candidate) ? (candidate as ScreenId) : 'policies'
}

/** The screen each kind of the palette's targets opens on (the spec, section 08). */
const OPENS_ON: Record<PaletteTarget['kind'], ScreenId> = {
  case: 'cases',
  rule: 'rules',
  finding: 'rules',
  version: 'rules',
  paragraph: 'policies',
  change: 'audit',
}

/** What the gate says when it stands again because the session ended (the spec, section 11, Gate, v3.9). */
const SESSION_ENDED = 'Your session has ended. Enter the code again.'

/**
 * The rail, with what it says about the workspace: read only once the gate is behind, when the session can read it.
 */
function WorkspaceShell({
  rulesetId,
  ...shell
}: Omit<ComponentProps<typeof AppShell>, 'policy' | 'findingsToAcknowledge' | 'version'> & {
  rulesetId: string | null
}) {
  const workspace = useWorkspace(rulesetId)
  return (
    <AppShell
      {...shell}
      policy={workspace.policy}
      findingsToAcknowledge={workspace.findingsToAcknowledge}
      version={workspace.version}
    />
  )
}

/**
 * The application: the access gate until the code is exchanged, then the workspace. The session is the HttpOnly
 * cookie, so the app keeps no token of its own (Document 5); on load it asks the API whether that cookie still holds,
 * so a reload opens the workspace without the code, and Leave expires it (Document 2, /auth/session, 2026-10-01). A
 * session that ends while the workspace is open brings the gate back, saying so; Leave and the end of a session drop
 * all the workspace held, since it was its sandbox's and the next session is a new sandbox (the spec, section 11,
 * Gate, v3.9).
 */
export function App() {
  // null while the API is asked whether the session holds, then whether the workspace is open
  const [entered, setEntered] = useState<boolean | null>(null)
  // whether the gate stands again because the session ended while the workspace was open
  const [ended, setEnded] = useState(false)
  const queryClient = useQueryClient()

  useEffect(() => {
    let current = true
    void sessionHolds().then((holds) => {
      if (current) {
        setEntered(holds)
      }
    })
    return () => {
      current = false
    }
  }, [])

  // the theme this browser chose, which index.html already applied before the first paint
  useEffect(() => {
    applyTheme(storedTheme())
  }, [])

  const close = useCallback(
    (why: 'left' | 'ended') => {
      queryClient.clear()
      setEnded(why === 'ended')
      setEntered(false)
    },
    [queryClient],
  )

  useEffect(() => {
    if (entered !== true) {
      return undefined
    }
    return onSessionEnded(() => close('ended'))
  }, [entered, close])

  if (entered === null) {
    return <GatePaper />
  }
  if (!entered) {
    return (
      <AccessGate
        notice={ended ? SESSION_ENDED : undefined}
        onEntered={() => {
          setEnded(false)
          setEntered(true)
        }}
      />
    )
  }
  return (
    <Workspace
      onLeave={() => {
        void leave().then(() => close('left'))
      }}
    />
  )
}

/**
 * The workspace behind the gate: the six screens in the shell, the guided demo and the palette. All it holds is the
 * session's, so it is mounted anew with each session.
 */
function Workspace({ onLeave }: { onLeave: () => void }) {
  const [screen, setScreen] = useState<ScreenId>(screenFromHash)
  // the rule another screen asked to open, so a trace step leads to the rule and its source
  const [focusRuleId, setFocusRuleId] = useState<string | null>(null)
  // the rule set the workspace is on, so opening a policy's rules does not land on someone else's
  const [rulesetId, setRulesetId] = useState<string | null>(null)
  // the scripted step the guided panel asked for, which its screen carries out and then clears (Brief FR-23)
  const [demo, setDemo] = useState<DemoStep['id'] | null>(null)
  // the last step run, which the strip keeps showing after its screen has carried the step out
  const [lastStep, setLastStep] = useState<DemoStep['id'] | null>(null)
  // how many steps have run, so a step run from the phone's menu closes it (the spec, section 10)
  const [demoRuns, setDemoRuns] = useState(0)
  // whether the strip shows its steps, kept here since the phone's menu mounts the strip anew each time it opens
  const [demoOpen, setDemoOpen] = useState(false)
  // the palette (the spec, section 08): whether it is open, and where it last went, which the screen it opened reads
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [goneTo, setGoneTo] = useState<PaletteTarget | null>(null)
  // each place the palette opens is a visit of its own: the screen mounts afresh on it, even the one already open
  const [visit, setVisit] = useState(0)
  const openPalette = useCallback(() => setPaletteOpen(true), [])
  const closePalette = useCallback(() => setPaletteOpen(false), [])

  useEffect(() => {
    const onHashChange = () => setScreen(screenFromHash())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  function navigate(next: ScreenId) {
    window.location.hash = `#/${next}`
    setScreen(next)
  }

  function runDemoStep(step: DemoStep) {
    setGoneTo(null)
    setDemo(step.id)
    setLastStep(step.id)
    setDemoRuns((runs) => runs + 1)
    navigate(step.screen)
  }

  function goTo(target: PaletteTarget) {
    setGoneTo(target)
    setVisit((count) => count + 1)
    if (target.kind === 'rule' || target.kind === 'finding' || target.kind === 'version') {
      setRulesetId(target.rulesetId)
      setFocusRuleId(target.kind === 'rule' ? target.ruleId : null)
    }
    navigate(OPENS_ON[target.kind])
  }

  return (
    <PaletteContext value={openPalette}>
      <WorkspaceShell
        current={screen}
        onNavigate={(next) => {
          setGoneTo(null)
          navigate(next)
        }}
        onLeave={onLeave}
        rulesetId={rulesetId}
        demoRuns={demoRuns}
        onOpenPalette={openPalette}
        palette={
          paletteOpen ? (
            <GoToPalette rulesetId={rulesetId} onGoTo={goTo} onClose={closePalette} />
          ) : null
        }
        aside={
          <GuidedPanel
            current={lastStep}
            onRun={runDemoStep}
            open={demoOpen}
            onToggle={setDemoOpen}
          />
        }
      >
        {screen === 'policies' ? (
          <PoliciesScreen
            key={visit}
            focusParagraph={
              goneTo?.kind === 'paragraph'
                ? { policyId: goneTo.policyId, index: goneTo.index }
                : null
            }
            demoAsked={demo === 1}
            onDemoHandled={() => setDemo(null)}
            onOpenRules={(chosen, ruleId) => {
              setRulesetId(chosen)
              setFocusRuleId(ruleId ?? null)
              navigate('rules')
            }}
          />
        ) : null}
        {screen === 'rules' ? (
          <RulesScreen
            key={visit}
            focusFindingId={goneTo?.kind === 'finding' ? goneTo.findingId : null}
            focusVersionNo={goneTo?.kind === 'version' ? goneTo.versionNo : null}
            onOpenCases={() => navigate('cases')}
            onOpenPolicies={() => navigate('policies')}
            focusRuleId={focusRuleId}
            rulesetId={rulesetId}
            onChooseRuleset={(chosen) => {
              setRulesetId(chosen)
              setFocusRuleId(null)
            }}
          />
        ) : null}
        {screen === 'cases' ? (
          <CasesScreen
            key={visit}
            focusDecisionId={goneTo?.kind === 'case' ? goneTo.decisionId : null}
            rulesetId={rulesetId}
            demoAsked={demo === 2}
            onDemoHandled={() => setDemo(null)}
            onOpenRule={(ruleId) => {
              setFocusRuleId(ruleId)
              navigate('rules')
            }}
          />
        ) : null}
        {screen === 'assistant' ? (
          <ChatScreen
            onOpenCase={(decisionId) => goTo({ kind: 'case', decisionId })}
            rulesetId={rulesetId}
            demoAsked={demo === 3}
            onDemoHandled={() => setDemo(null)}
            onOpenRule={(ruleId) => {
              setFocusRuleId(ruleId)
              navigate('rules')
            }}
            onOpenCases={() => navigate('cases')}
          />
        ) : null}
        {screen === 'change' ? (
          <ChangeScreen
            rulesetId={rulesetId}
            demoAsked={demo === 4}
            onDemoHandled={() => setDemo(null)}
            onPublished={(published) => {
              // a protected base is approved into the sandbox's own copy: the workspace follows the new version
              setRulesetId(published.rulesetId)
              setFocusRuleId(null)
            }}
            onOpenRules={() => navigate('rules')}
            onOpenAudit={() => navigate('audit')}
          />
        ) : null}
        {screen === 'audit' ? (
          <AuditScreen
            key={visit}
            focusChangeRequestId={goneTo?.kind === 'change' ? goneTo.changeRequestId : null}
            rulesetId={rulesetId}
            onChooseRuleset={(chosen) => {
              setRulesetId(chosen)
              setFocusRuleId(null)
            }}
          />
        ) : null}
      </WorkspaceShell>
    </PaletteContext>
  )
}
