import { useEffect, useState } from 'react'
import { AccessGate } from './shared/gate/AccessGate'
import { AppShell } from './shared/layout/AppShell'
import { SCREENS, type ScreenId } from './shared/layout/screens'
import { PoliciesScreen } from './features/policy/PoliciesScreen'
import { CasesScreen } from './features/cases/CasesScreen'
import { RulesScreen } from './features/rules/RulesScreen'
import { ChatScreen } from './features/chat/ChatScreen'
import { ChangeScreen } from './features/change/ChangeScreen'
import { AuditScreen } from './features/audit/AuditScreen'
import { GuidedPanel } from './features/demo/GuidedPanel'
import type { DemoStep } from './features/demo/steps'

/** The screen the address bar names, so a screen can be linked to and the back button works. */
function screenFromHash(): ScreenId {
  const candidate = window.location.hash.replace('#/', '')
  return SCREENS.some((screen) => screen.id === candidate) ? (candidate as ScreenId) : 'policies'
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

  useEffect(() => {
    const onHashChange = () => setScreen(screenFromHash())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
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
    navigate(step.screen)
  }

  return (
    <AppShell
      current={screen}
      onNavigate={navigate}
      aside={<GuidedPanel current={demo} onRun={runDemoStep} />}
    >
      {screen === 'policies' ? (
        <PoliciesScreen
          demoAsked={demo === 1}
          onDemoHandled={() => setDemo(null)}
          onOpenRules={(chosen) => {
            setRulesetId(chosen)
            setFocusRuleId(null)
            navigate('rules')
          }}
        />
      ) : null}
      {screen === 'rules' ? (
        <RulesScreen
          onOpenCases={() => navigate('cases')}
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
    </AppShell>
  )
}
