import { useEffect, useState } from 'react'
import { AccessGate } from './shared/gate/AccessGate'
import { AppShell } from './shared/layout/AppShell'
import { SCREENS, type ScreenId } from './shared/layout/screens'
import { PoliciesScreen } from './features/policy/PoliciesScreen'
import { CasesScreen } from './features/cases/CasesScreen'
import { RulesScreen } from './features/rules/RulesScreen'
import { EmptyState } from './shared/ui/States'
import { WorkspaceHeader } from './shared/layout/WorkspaceHeader'

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

  return (
    <AppShell current={screen} onNavigate={navigate}>
      {screen === 'policies' ? (
        <PoliciesScreen
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
          onOpenRule={(ruleId) => {
            setFocusRuleId(ruleId)
            navigate('rules')
          }}
        />
      ) : null}
      {screen !== 'policies' && screen !== 'rules' && screen !== 'cases' ? (
        <>
          <WorkspaceHeader title={SCREENS.find((item) => item.id === screen)?.label ?? ''} />
          <EmptyState
            title="Not built yet"
            description="This screen arrives with its day of the work plan; the screens that are built are in the sidebar."
          />
        </>
      ) : null}
    </AppShell>
  )
}
