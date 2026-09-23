/** The screens of the workspace, in the order of the demo: author, decide, ask, change, and the audit log. */
export const SCREENS = [
  { id: 'policies', label: 'Policies', hint: 'Policy documents and their paragraphs' },
  { id: 'rules', label: 'Rules', hint: 'The rule set, its versions and its sources' },
  { id: 'cases', label: 'Cases', hint: 'Applications, decisions and their traces' },
  { id: 'assistant', label: 'Assistant', hint: 'Questions answered with citations' },
  { id: 'change', label: 'Change', hint: 'Change requests, measured on the cases before approval' },
  { id: 'audit', label: 'Audit log', hint: 'Every publication and approval' },
] as const

export type ScreenId = (typeof SCREENS)[number]['id']
