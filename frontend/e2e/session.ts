import type { Page } from '@playwright/test'
import { sandboxCopy } from './change'
import { DRAFT, REVIEW, STAGES } from './generation'
import { ruleSet, seededRuleset } from './seeded'

/**
 * The presenter's session as the tests serve it (Brief FR-23): the copy of the seeded policy that step 1 pastes, created
 * and read back as a policy of the visitor's sandbox; its generation, streaming the draft with the reviewer's two
 * findings; and the sandbox's list of rule sets, which grows with the draft, then with its copy of the seeded rule set
 * once step 4 is approved. Registered after the steps' routes, so these answer first (Playwright tries the last route
 * that matches); each flag is set inside a route, before the browser has the answer that makes it refetch the list.
 */

/** The copy step 1 pastes: the seeded policy's own text, in a policy of the visitor's sandbox. */
const PASTED_ID = '0f4c1c9e-0000-4000-8000-0000000000a3'

/** The draft step 1 writes from the pasted copy, with the reviewer's two findings. */
const draft = {
  ...DRAFT,
  ruleSet,
  review: REVIEW,
  policyVersionId: '0f4c1c9e-0000-4000-8000-0000000000d3',
}

/** The draft as the sandbox's list of rule sets names it once it is written. */
const written = {
  id: DRAFT.rulesetId,
  name: DRAFT.name,
  domain: DRAFT.domain,
  protected: false,
  policyId: PASTED_ID,
  versions: [{ versionNo: 1, status: 'DRAFT' }],
}

/**
 * Serves the session on the seeded policy's paragraphs, the ones step 1 pastes; what the generation was asked with is
 * kept, so a test can read the hints it carried.
 */
export async function serveTheSession(
  page: Page,
  paragraphs: { index: number; text: string }[],
): Promise<{ generatedWith: () => unknown }> {
  const pasted = {
    id: PASTED_ID,
    title: seededRuleset.name,
    language: 'he',
    protected: false,
    createdAt: '2026-09-28T09:00:00Z',
    versions: [{ versionNo: 1, createdAt: '2026-09-28T09:00:00Z', paragraphs }],
  }
  let generated: unknown = null
  let approved = false
  await page.route('**/api/v1/policies', (route) =>
    route.request().method() === 'POST'
      ? route.fulfill({ status: 201, json: pasted })
      : route.fallback(),
  )
  await page.route(`**/api/v1/policies/${PASTED_ID}`, (route) => route.fulfill({ json: pasted }))
  await page.route(`**/api/v1/policies/${PASTED_ID}/rulesets`, (route) => {
    generated = route.request().postDataJSON()
    return route.fulfill({
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
      body: STAGES + `event:draft\ndata:${JSON.stringify(draft)}\n\n`,
    })
  })
  await page.route(`**/api/v1/rulesets/${DRAFT.rulesetId}/versions/*`, (route) =>
    route.fulfill({ json: draft }),
  )
  await page.route('**/api/v1/changes/*/approve', (route) => {
    approved = true
    return route.fallback()
  })
  await page.route('**/api/v1/rulesets', (route) =>
    route.fulfill({
      json: {
        rulesets: [
          seededRuleset,
          ...(generated === null ? [] : [written]),
          ...(approved ? [sandboxCopy] : []),
        ],
      },
    }),
  )
  return { generatedWith: () => generated }
}
