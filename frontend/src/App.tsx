import { useEffect, useState } from 'react'
import { AccessGate } from './shared/gate/AccessGate'
import { AppShell } from './shared/layout/AppShell'
import { SCREENS, type ScreenId } from './shared/layout/screens'
import { PoliciesScreen } from './features/policy/PoliciesScreen'
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
      {screen === 'policies' ? <PoliciesScreen onOpenRules={() => navigate('rules')} /> : null}
      {screen === 'rules' ? <RulesScreen onOpenCases={() => navigate('cases')} /> : null}
      {screen !== 'policies' && screen !== 'rules' ? (
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
