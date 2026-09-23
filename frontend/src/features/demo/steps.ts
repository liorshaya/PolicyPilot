import type { ScreenId } from '../../shared/layout/screens'

/**
 * The four scripted steps of the demo (the brief, Demo Script; Document 2, Frontend Architecture: "a collapsible
 * panel that lists the four scripted steps as one-click actions which pre-fill the inputs and call the same API
 * the regular screens use; it adds no logic of its own"). Each step names the screen it belongs to and what it
 * fills in there; nothing here calls an API, so a step is the same click a person would make by hand.
 */
export interface DemoStep {
  readonly id: 1 | 2 | 3 | 4
  readonly title: string
  readonly what: string
  readonly screen: ScreenId
}

/** The three scripted chat questions of step 3, in the order the demo asks them (the brief, step 3). */
export const SCRIPTED_QUESTIONS = [
  'למה בקשה מספר 17 הופנתה לבדיקה?',
  'האם בקשה 17 הייתה מאושרת אם היה ערב?',
  'מהי תקופת ההחזר המקסימלית להלוואה?',
] as const

/**
 * The change request of step 4, as the labeled set words it (fixtures/eval/changes.json, CR-1; the brief, step 4:
 * "Raise the minimum monthly income to 9,000"). It is the key of the live site's cached proposal, word for word.
 */
export const SCRIPTED_CHANGE_REQUEST = 'העלה את ההכנסה החודשית המינימלית ל-9,000'

export const DEMO_STEPS: readonly DemoStep[] = [
  {
    id: 1,
    title: 'Author',
    what: 'Fills the form with the sample lending policy, ready to generate its rules',
    screen: 'policies',
  },
  {
    id: 2,
    title: 'Decide',
    what: 'Runs the 200 seeded cases on the published version',
    screen: 'cases',
  },
  {
    id: 3,
    title: 'Ask',
    what: 'Opens the assistant on the first of the three scripted questions',
    screen: 'assistant',
  },
  {
    id: 4,
    title: 'Change',
    what: 'Fills in the request to raise the minimum monthly income to 9,000',
    screen: 'change',
  },
] as const
