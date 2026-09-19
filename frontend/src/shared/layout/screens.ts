/** The screens of the workspace; the two that arrive later are listed, and listed as not yet built. */
export const SCREENS = [
  { id: 'policies', label: 'Policies', hint: 'Policy documents and their paragraphs' },
  { id: 'rules', label: 'Rules', hint: 'The rule set, its versions and its sources' },
  { id: 'cases', label: 'Cases', hint: 'Applications, decisions and their traces' },
  { id: 'assistant', label: 'Assistant', hint: 'Questions answered with citations', soon: true },
  { id: 'audit', label: 'Audit log', hint: 'Every publication and approval', soon: true },
] as const

export type ScreenId = (typeof SCREENS)[number]['id']
