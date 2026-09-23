import { describe, expect, it } from 'vitest'
import changes from '../../../../fixtures/eval/changes.json'
import questions from '../../../../fixtures/eval/questions.json'
import { DEMO_STEPS, SCRIPTED_CHANGE_REQUEST, SCRIPTED_QUESTIONS } from './steps'

// @requirement FR-23

/**
 * What the guided panel types (Brief FR-23). A scripted input is the key of the live site's response cache: the
 * rendered prompt, of which the text is a part, is what a warmed answer is found by. A word out of place sends the demo
 * to the model at the presenter's expense, so each input is the labeled set's own, word for word.
 */
describe('the scripted inputs of the demo', () => {
  it('asks the three questions of step 3 as the labeled set words them (Q-01 to Q-03)', () => {
    const labeled = (id: string) => questions.questions.find((one) => one.id === id)?.question

    expect(SCRIPTED_QUESTIONS).toStrictEqual([labeled('Q-01'), labeled('Q-02'), labeled('Q-03')])
  })

  it('proposes the change of step 4 as the labeled set words it (CR-1)', () => {
    expect(SCRIPTED_CHANGE_REQUEST).toBe(changes.changes.find((one) => one.id === 'CR-1')?.text)
  })

  it('opens step 4 on the change screen, where the request is filled in', () => {
    expect(DEMO_STEPS.map((step) => [step.id, step.screen])).toStrictEqual([
      [1, 'policies'],
      [2, 'cases'],
      [3, 'assistant'],
      [4, 'change'],
    ])
  })
})
