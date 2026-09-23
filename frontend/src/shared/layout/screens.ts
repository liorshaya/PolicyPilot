/** The screens of the workspace; the one that arrives later is listed, and listed as not yet built. */
export const SCREENS = [
  { id: 'policies', label: 'Policies', hint: 'Policy documents and their paragraphs' },
  { id: 'rules', label: 'Rules', hint: 'The rule set, its versions and its sources' },
  { id: 'cases', label: 'Cases', hint: 'Applications, decisions and their traces' },
  { id: 'assistant', label: 'Assistant', hint: 'Questions answered with citations' },
  { id: 'change', label: 'Change', hint: 'Change requests, measured on the cases before approval' },
  { id: 'audit', label: 'Audit log', hint: 'Every publication and approval', soon: true },
] as const

export type ScreenId = (typeof SCREENS)[number]['id']
