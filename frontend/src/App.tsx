import { useEffect, useState, type ComponentProps } from 'react'
import { AccessGate } from './shared/gate/AccessGate'
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
import { applyTheme, storedTheme } from './styles/theme'

/** The screen the address bar names, so a screen can be linked to and the back button works. */
function screenFromHash(): ScreenId {
  const candidate = window.location.hash.replace('#/', '')
  return SCREENS.some((screen) => screen.id === candidate) ? (candidate as ScreenId) : 'policies'
}

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
 * cookie, so the app keeps no token of its own (Document 5).
 */
export function App() {
  const [entered, setEntered] = useState(false)
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

  useEffect(() => {
    const onHashChange = () => setScreen(screenFromHash())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  // the theme this browser chose, which index.html already applied before the first paint
  useEffect(() => {
    applyTheme(storedTheme())
  }, [])

  function navigate(next: ScreenId) {
    window.location.hash = `#/${next}`
    setScreen(next)
  }

  if (!entered) {
    return <AccessGate onEntered={() => setEntered(true)} />
  }

  function runDemoStep(step: DemoStep) {
    setDemo(step.id)
    setLastStep(step.id)
    setDemoRuns((runs) => runs + 1)
    navigate(step.screen)
  }

  return (
    <WorkspaceShell
      current={screen}
      onNavigate={navigate}
      onLeave={() => setEntered(false)}
      rulesetId={rulesetId}
      demoRuns={demoRuns}
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
          rulesetId={rulesetId}
          onChooseRuleset={(chosen) => {
            setRulesetId(chosen)
            setFocusRuleId(null)
          }}
        />
      ) : null}
    </WorkspaceShell>
  )
}
