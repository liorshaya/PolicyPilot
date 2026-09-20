import type { ReactNode } from 'react'
import './WorkspaceHeader.css'

interface WorkspaceHeaderProps {
  title: string
  /** What is open right now: the policy, the rule set, the case (the brief: the current context). */
  context?: ReactNode
  /** The version and its status, shown plainly. */
  version?: ReactNode
  /** The one action this screen is about, and at most one action beside it. */
  actions?: ReactNode
}

/** The top of the workspace: what this screen is, what is open in it, and what to do next. */
export function WorkspaceHeader({ title, context, version, actions }: WorkspaceHeaderProps) {
  return (
    <header className="workspace-header">
      <div className="workspace-header__text">
        <h1 className="workspace-header__title">{title}</h1>
        {context ? <p className="workspace-header__context">{context}</p> : null}
      </div>
      <div className="workspace-header__side">
        {version ? <div className="workspace-header__version">{version}</div> : null}
        {actions ? <div className="workspace-header__actions">{actions}</div> : null}
      </div>
    </header>
  )
}
