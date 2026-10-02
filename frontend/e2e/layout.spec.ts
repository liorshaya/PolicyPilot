import { expect, test, type Page } from '@playwright/test'
import { ollamaProvider } from '../src/test/fixtures/provider'
import { changeRequest, serveTheChange } from './change'
import { streamOf } from './chat'
import { inspect } from './checklist'
import { serveTheGeneration } from './generation'
import { openThePanel } from './panel'
import { POLICY_ID, paragraphs, serveTheSeededRuleSet } from './seeded'

// @requirement FR-20
// @requirement FR-23

/**
 * The screens as the demo shows them, at the two widths it is shown at (Work Plan day 16, the pass over every screen in
 * both directions; days 17 and 18 rehearse from a laptop and from a phone). The pass found text nobody could read: the
 * guided panel's titles in the sidebar's white on the panel's own white and its descriptions a word a line, the change
 * screen squeezing its request out of sight once the proposal came, the side-by-side diff a letter a line on a phone,
 * the version pickers of the audit log cut off there, and the model names in the header broken at their hyphens. Each
 * test pins one of them where only a browser can see it, in the layout; the words themselves are the component tests'.
 * The local chat model chosen afterwards has a name wider than the sidebar, which wraps inside it instead of running
 * past the edge.
 */

const LAPTOP = { width: 1440, height: 900 }
const PHONE = { width: 390, height: 844 }

/** The colour of the workspace's text, --color-text (#142c43), as a browser reports it. */
const TEXT = 'rgb(20, 44, 67)'
// the Register's --ink-2 in the light theme, the step titles of the demo strip
const INK_2 = 'rgb(67, 83, 106)'

async function enter(page: Page): Promise<void> {
  await page.goto('/')
  await page.getByLabel('Access code').fill('qwertyui')
  await page.getByRole('button', { name: 'Enter' }).click()
  await expect(page.getByRole('navigation', { name: 'Workspace' })).toBeVisible()
}

async function proposeTheScriptedChange(page: Page): Promise<void> {
  await serveTheSeededRuleSet(page)
  await serveTheChange(page)
  await enter(page)
  await page
    .getByRole('navigation', { name: 'Workspace' })
    .getByRole('button', { name: /^Change/ })
    .click()
  await page.getByLabel('What should change').fill(changeRequest.text.he)
  await page.getByRole('button', { name: 'Propose the change' }).click()
  await expect(page.getByRole('region', { name: 'Regression report' })).toBeVisible()
}

test.describe('on a laptop', () => {
  test.use({ viewport: LAPTOP })

  // the Register's demo strip (the spec, section 08): its name in ink, a step's title in ink-2, a description a few words
  // a line inside the rail
  test('the guided demo strip reads as a list of steps: its name in ink, each description a few words a line', async ({
    page,
  }) => {
    await serveTheSeededRuleSet(page)
    await openThePanel(page)

    const panel = page.getByRole('region', { name: 'Guided demo' })
    await expect(panel.getByText('Guided demo', { exact: true })).toHaveCSS('color', TEXT)
    await expect(panel.getByText('Author', { exact: true })).toHaveCSS('color', INK_2)
    const what = panel.getByText(
      'Fills the form with the sample lending policy, ready to generate its rules',
    )
    expect((await what.boundingBox())!.width).toBeGreaterThan(140)
  })

  // the spec (v3.9), section 08, Rail: the keyboard's first stop skips to the workspace; it stayed clipped to a pixel
  // while it had the focus, and its #workspace took the reader to Policies from any other screen
  test('shows the link that skips to the workspace when the first Tab reaches it, and keeps the screen', async ({
    page,
  }) => {
    await serveTheSeededRuleSet(page)
    // a session that holds (Document 2, GET /auth/session), so the page opens on Rules and the keyboard at its top
    await page.route('**/api/v1/auth/session', (route) => route.fulfill({ status: 204 }))
    await page.goto('/#/rules')
    await expect(page.getByRole('heading', { level: 1, name: /^Rules/ })).toBeVisible()

    await page.keyboard.press('Tab')
    const skip = page.getByRole('link', { name: 'Skip to the workspace' })
    await expect(skip).toBeFocused()
    const box = (await skip.boundingBox())!
    expect(box.width).toBeGreaterThan(100)
    expect(box.height).toBeGreaterThan(16)
    await page.keyboard.press('Enter')

    await expect(page.getByRole('main')).toBeFocused()
    await expect(page.getByRole('heading', { level: 1, name: /^Rules/ })).toBeVisible()
    expect(new URL(page.url()).hash).toBe('#/rules')
  })

  test('the guided panel folds its steps away when hidden', async ({ page }) => {
    await serveTheSeededRuleSet(page)
    await openThePanel(page)
    const panel = page.getByRole('region', { name: 'Guided demo' })
    await expect(panel.getByRole('list')).toBeVisible()

    await panel.getByRole('button', { name: /guided demo/i }).click()

    await expect(panel.getByRole('list')).toBeHidden()
  })

  test('the change screen keeps its request whole once the proposal is shown, and scrolls as one page', async ({
    page,
  }) => {
    await proposeTheScriptedChange(page)

    const request = page.getByLabel('What should change')
    await request.scrollIntoViewIfNeeded()
    await expect(request).toBeInViewport({ ratio: 1 })
  })

  test('a changed value is marked as one box, which begins with its first words', async ({
    page,
  }) => {
    await proposeTheScriptedChange(page)
    await page.getByRole('button', { name: 'Side by side' }).click()

    // R-170's action as proposed: the outcome and the reason, marked as inserted
    const inserted = page
      .getByRole('row')
      .filter({ hasText: 'R-170' })
      .locator('ins')
      .filter({ hasText: 'Decline' })
    const mark = (await inserted.boundingBox())!
    const first = (await inserted.getByText('Decline', { exact: true }).boundingBox())!
    // a mark that broke with its lines began with an empty line above its first word
    expect(first.y - mark.y).toBeLessThan(6)
  })

  // Brief FR-21 and the Register (section 08): the rail names the provider, and its models stand in the legend behind
  // Help; a model's name is a machine token, whole on one line and inside the popover
  test('the legend names each model whole, on one line, inside its popover', async ({ page }) => {
    await serveTheSeededRuleSet(page)
    await enter(page)
    await page.getByRole('button', { name: 'Help' }).click()

    const legend = page.getByRole('dialog', { name: 'Help' })
    const edge = (await legend.boundingBox())!
    for (const name of ['gpt-5.6-terra', 'gpt-5.6-luna', 'text-embedding-3-small']) {
      const model = legend.getByText(name, { exact: true })
      expect(
        await model.evaluate(
          (value: { getClientRects: () => { length: number } }) => value.getClientRects().length,
        ),
        name,
      ).toBe(1)
      const box = (await model.boundingBox())!
      expect(box.x + box.width, name).toBeLessThanOrEqual(edge.x + edge.width)
    }
  })

  test('a long local model name stays whole inside the legend rather than running past its edge', async ({
    page,
  }) => {
    await serveTheSeededRuleSet(page)
    // the ollama column of Document 2's profiles table, served over the seeded openai names
    await page.route('**/api/v1/system/provider', (route) =>
      route.fulfill({ json: ollamaProvider }),
    )
    await enter(page)
    await page.getByRole('button', { name: 'Help' }).click()

    const legend = page.getByRole('dialog', { name: 'Help' })
    const edge = (await legend.boundingBox())!
    const names = legend.getByText(ollamaProvider.chatModels.strong, { exact: true })
    await expect(names).toHaveCount(2)
    for (const name of await names.all()) {
      expect(
        await name.evaluate(
          (value: { getClientRects: () => { length: number } }) => value.getClientRects().length,
        ),
      ).toBe(1)
      const box = (await name.boundingBox())!
      expect(box.x + box.width).toBeLessThanOrEqual(edge.x + edge.width)
    }
  })
})

test.describe('on a phone', () => {
  test.use({ viewport: PHONE })

  test('the side-by-side diff keeps each value readable, words on a line rather than a letter', async ({
    page,
  }) => {
    await proposeTheScriptedChange(page)
    await page.getByRole('button', { name: 'Side by side' }).click()

    // the label of R-170 as proposed (change-request-1.json), the widest value of the diff's first row
    const replacement = changeRequest.expected.patches.find(
      (patch) => 'rule' in patch && patch.ruleId === 'R-170',
    )
    const proposed =
      replacement && 'rule' in replacement ? replacement.rule.label : 'no R-170 in the fixture'
    const label = page
      .getByRole('row')
      .filter({ hasText: 'R-170' })
      .getByText(proposed, { exact: true })
    expect((await label.boundingBox())!.width).toBeGreaterThan(150)
  })

  test("the audit log's version pickers stay on the screen", async ({ page }) => {
    await proposeTheScriptedChange(page)
    await page.getByLabel('Note for the audit log').fill('אושר בוועדת האשראי')
    await page.getByRole('button', { name: 'Approve and publish' }).click()
    await page.getByRole('button', { name: 'Open the audit log' }).click()

    const to = page.getByRole('combobox', { name: 'to', exact: true })
    await to.scrollIntoViewIfNeeded()
    await expect(to).toBeInViewport({ ratio: 1 })
  })
})

// The pass over every width of 2026-10-02 (the spec, section 08, Sheet and margin, v3.9): from 721 to 1199px the margin
// is a drawer over the sheet, and on Policies and Rules it stood open with no Close, hiding the start of every Hebrew
// line, most of the rule table and the draft's Review the draft. It now starts closed and opens on what is asked for.
test.describe('between 721 and 1199px', () => {
  test.use({ viewport: { width: 1024, height: 768 } })

  test('opens Policies on the whole policy, the drawer on Documents, and Esc shuts it', async ({
    page,
  }) => {
    await serveTheSeededRuleSet(page)
    await enter(page)
    await expect(page.getByText(paragraphs[0].text)).toBeVisible()
    await expect(page.getByRole('complementary')).toHaveCount(0)

    const documents = page.getByRole('button', { name: 'Documents' })
    await documents.click()
    await expect(
      page.getByRole('complementary').getByRole('region', { name: 'Documents' }),
    ).toBeVisible()
    await page.keyboard.press('Escape')

    await expect(page.getByRole('complementary')).toHaveCount(0)
    await expect(documents).toBeFocused()
  })

  test("leaves the draft's Review the draft free to press once the stages have run", async ({
    page,
  }) => {
    await serveTheSeededRuleSet(page)
    await serveTheGeneration(page)
    await enter(page)

    await page.getByRole('button', { name: 'Generate rules' }).click()
    // a click waits until nothing covers the button: the open drawer did, and the click never landed
    await page.getByRole('button', { name: 'Review the draft' }).click()

    await expect(page.getByRole('heading', { level: 1, name: /^Rules/ })).toBeVisible()
  })

  test('opens Rules on the whole table, and a row opens the drawer on its rule until Esc', async ({
    page,
  }) => {
    await serveTheSeededRuleSet(page)
    await enter(page)
    await page
      .getByRole('navigation', { name: 'Workspace' })
      .getByRole('button', { name: /^Rules/ })
      .click()
    await expect(page.getByRole('table').getByRole('button', { name: /R-330/ })).toBeVisible()
    await expect(page.getByRole('complementary')).toHaveCount(0)

    await page.getByRole('table').getByRole('button', { name: /R-330/ }).click()
    await expect(page.getByRole('complementary')).toContainText(
      'Paragraph 7 is the source of R-330',
    )
    await page.keyboard.press('Escape')

    await expect(page.getByRole('complementary')).toHaveCount(0)
  })
})

// The spec (v3.9), section 03: prose a person or a model wrote breaks a word too long for its line. A pasted address
// of 200 characters ran a paragraph 1,100px past its sheet, pushing the Hebrew sentence before it out of sight, and a
// question, an answer and a note to the audit log did the same; on a phone the page scrolled sideways by 1,500px
for (const [device, viewport] of [
  ['laptop', LAPTOP],
  ['phone', PHONE],
] as const) {
  test(`breaks a word too long for its line on a ${device}: a paragraph, a question, an answer, a note`, async ({
    page,
  }) => {
    const address = `https://example.org/${'a'.repeat(200)}`
    await page.setViewportSize(viewport)
    await serveTheSeededRuleSet(page)
    await serveTheChange(page)
    await page.route(`**/api/v1/policies/${POLICY_ID}`, (route) =>
      route.fulfill({
        json: {
          id: POLICY_ID,
          title: 'מדיניות אשראי צרכני',
          language: 'he',
          protected: true,
          createdAt: '2026-09-20T09:00:00Z',
          versions: [
            {
              versionNo: 1,
              createdAt: '2026-09-20T09:00:00Z',
              paragraphs: [{ index: 1, text: `הלוואה אישית תינתן ליחיד, ראו ${address}` }],
            },
          ],
        },
      }),
    )
    await page.route('**/api/v1/chat/sessions', (route) =>
      route.request().method() === 'GET'
        ? route.fulfill({ json: { sessions: [] } })
        : route.fulfill({
            status: 201,
            json: { id: 'session-1', rulesetId: 'ruleset', versionNo: 1, language: 'he' },
          }),
    )
    await page.route('**/api/v1/chat/sessions/*/messages', (route) =>
      route.fulfill({
        headers: { 'Content-Type': 'text/event-stream' },
        body: streamOf({ text: `הכתובת ${address} אינה מסמך של המדיניות.`, citations: [] }),
      }),
    )
    await enter(page)
    const go = (screen: string) =>
      page
        .getByRole('navigation', { name: 'Workspace' })
        .getByRole('button', { name: new RegExp(`^${screen}`) })
        .click()

    await expect(page.getByText(/^הלוואה אישית תינתן ליחיד/)).toBeVisible()
    expect((await inspect(page)).overflow, 'Policies').toEqual([])

    await go('Assistant')
    await page.getByLabel('Question', { exact: true }).fill(`מה כתוב ב-${address}?`)
    await page.getByRole('button', { name: 'Ask' }).click()
    await expect(page.getByText(/אינה מסמך של המדיניות/)).toBeVisible()
    expect((await inspect(page)).overflow, 'Assistant').toEqual([])

    await go('Change')
    await page.getByLabel('What should change').fill(changeRequest.text.he)
    await page.getByRole('button', { name: 'Propose the change' }).click()
    await page.getByLabel('Note for the audit log').fill(`אושר, ראו ${address}`)
    await page.getByRole('button', { name: /^Approve and publish v\d+$/ }).click()
    await page.getByRole('button', { name: 'Open the audit log' }).click()
    await expect(page.getByText(/^אושר, ראו/)).toBeVisible()
    expect((await inspect(page)).overflow, 'Audit log').toEqual([])
  })
}
