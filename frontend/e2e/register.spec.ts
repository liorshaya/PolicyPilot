import { fileURLToPath } from 'node:url'
import { expect, test, type Locator, type Page } from '@playwright/test'
import { changeRequest, serveTheChange } from './change'
import { ask, notCovered, question, serveTheChat } from './chat'
import { inspect } from './checklist'
import { serveTheGeneration } from './generation'
import { serveTheWholePolicy, serveTheWholeRun, wholePolicy } from './lending'
import { step } from './panel'
import { serveTheSeededRuleSet } from './seeded'
import { serveTheSession } from './session'

// @requirement FR-23
// @requirement NFR-5

/**
 * The Register as a browser draws it (Document 9, phase 6): the seven screens of the demo in both themes at 1376×900,
 * each reached the way the demo reaches it with every API call answered from the committed fixtures, then the demo's
 * four steps through the guided panel on a desktop and on a phone, each screen checked against the design's checklist
 * (the spec, section 12) as far as a browser can check it: nothing wider than its container but a table's scroll box,
 * the provenance line and the spec's two bleeds; no text under the type floor; no text under 4.5:1 against what it is
 * drawn on, nor a mark under 3:1; capitals only in the seal; no gradient; Plex and Frank Ruhl Libre the only faces;
 * and the first paint light. Asked with REGISTER_SHOTS=1, the spec writes each screen it checks into docs/demo/: the
 * seven as register/<screen>-<theme>.png, the steps as <device>-<n>-<step>.png; a plain run leaves the tree as it was.
 */

const SHOTS = process.env.REGISTER_SHOTS !== undefined
const DEMO = fileURLToPath(new URL('../../docs/demo/', import.meta.url))
const NOTE = 'אושר בוועדת האשראי'

type Theme = 'light' | 'dark'

/** Keeps a theme chosen in this browser before the page's first script, as the rail's Theme control does. */
async function choose(page: Page, theme: Theme): Promise<void> {
  await page.addInitScript((chosen) => {
    const view = globalThis as unknown as {
      localStorage: { setItem(key: string, value: string): void }
    }
    view.localStorage.setItem('pp-theme', chosen)
  }, theme)
}

async function enter(page: Page): Promise<void> {
  await page.goto('/')
  await page.getByLabel('Access code').fill('qwertyui')
  await page.getByRole('button', { name: 'Enter' }).click()
  await expect(page.getByRole('navigation', { name: 'Workspace' })).toBeVisible()
}

/** Opens a screen from the rail, or from the row of screens under a phone's top bar. */
async function open(page: Page, name: string): Promise<void> {
  await page
    .getByRole('navigation', { name: 'Workspace' })
    .getByRole('button', { name: new RegExp(`^${name}`) })
    .click()
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible()
}

/** The screen at rest: the faces loaded, every animation that ends ended, the pointer on the rail's empty corner. */
async function settle(page: Page): Promise<void> {
  await page.mouse.move(1, 1)
  await page.evaluate(async () => {
    const view = globalThis as unknown as {
      document: {
        fonts: { ready: Promise<unknown> }
        getAnimations(): {
          effect: { getComputedTiming(): { iterations?: number } } | null
          finished: Promise<unknown>
        }[]
      }
    }
    await view.document.fonts.ready
    await Promise.all(
      view.document
        .getAnimations()
        .filter((animation) => animation.effect?.getComputedTiming().iterations !== Infinity)
        .map((animation) => animation.finished.catch(() => undefined)),
    )
  })
}

/** Every check of the checklist on the screen as it stands, each reported with what broke it. */
async function keepsTheChecklist(page: Page): Promise<void> {
  const found = await inspect(page)
  expect.soft(found.overflow, 'nothing wider than its container').toEqual([])
  expect.soft(found.floor, 'no text under its floor').toEqual([])
  expect.soft(found.contrast, 'no text under 4.5:1, no mark under 3:1').toEqual([])
  expect.soft(found.capitals, 'capitals only in the seal').toEqual([])
  expect.soft(found.gradient, 'no gradient anywhere').toEqual([])
  expect.soft(found.fonts, 'Plex and Frank Ruhl Libre only').toEqual([])
  expect.soft(found.dont, "the rest of the don't column").toEqual([])
  expect.soft(found.primary, 'one primary action per screen').toEqual([])
}

async function generate(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Generate rules' }).click()
  await expect(page.getByRole('button', { name: 'Review the draft' })).toBeVisible()
}

async function runTheCases(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Run 200 cases' }).first().click()
  await openCase17(page)
}

async function openCase17(page: Page): Promise<void> {
  // 113 of the 200 are approved (cases-expected.json)
  await expect(page.getByRole('definition').filter({ hasText: '56.5%' })).toContainText('113')
  await page.getByRole('button', { name: '17', exact: true }).click()
  await expect(page.getByRole('complementary').getByRole('listitem').last()).toContainText(
    'Matched',
  )
}

async function propose(page: Page): Promise<void> {
  await page.getByLabel('What should change').fill(changeRequest.text.he)
  await page.getByRole('button', { name: 'Propose the change' }).click()
  await expect(page.getByRole('region', { name: 'Regression report' })).toBeVisible()
}

async function approveAndOpenTheLog(page: Page): Promise<void> {
  await page.getByLabel('Note for the audit log').fill(NOTE)
  await page.getByRole('button', { name: /^Approve and publish v\d+$/ }).click()
  await page.getByRole('button', { name: 'Open the audit log' }).click()
  const entry = page.getByRole('region', { name: 'Audit log' }).getByRole('listitem').first()
  await expect(entry).toContainText(NOTE)
  await entry.getByText('Show the request, the diff and the regression').click()
}

/** The seven screens, each in the state the demo leaves it in. */
const SCREENS: { name: string; reach: (page: Page) => Promise<void> }[] = [
  {
    name: 'gate',
    reach: async (page) => {
      await page.goto('/')
      await expect(page.getByLabel('Access code')).toBeVisible()
    },
  },
  {
    name: 'policies',
    reach: async (page) => {
      await serveTheGeneration(page)
      await enter(page)
      await generate(page)
    },
  },
  {
    name: 'rules',
    reach: async (page) => {
      await serveTheGeneration(page)
      await enter(page)
      await generate(page)
      await page.getByRole('button', { name: 'Review the draft' }).click()
      await page.getByRole('table').getByRole('button', { name: /R-110/ }).click()
      await expect(page.getByRole('complementary')).toContainText('Findings on this rule')
    },
  },
  {
    name: 'cases',
    reach: async (page) => {
      await enter(page)
      await open(page, 'Cases')
      await runTheCases(page)
    },
  },
  {
    name: 'assistant',
    reach: async (page) => {
      await enter(page)
      await open(page, 'Assistant')
      await ask(page, question('Q-01').question)
      await expect(
        page.getByRole('group', { name: 'Sources' }).getByRole('button', { name: 'Case 17' }),
      ).toBeVisible()
      await ask(page, question('Q-02').question)
      await expect(page.getByRole('list', { name: 'Tool calls' }).last()).toContainText(
        'what-if · case 17 · has_guarantor=true',
      )
    },
  },
  {
    name: 'change',
    reach: async (page) => {
      await enter(page)
      await open(page, 'Change')
      await propose(page)
    },
  },
  {
    name: 'audit',
    reach: async (page) => {
      await enter(page)
      await open(page, 'Change')
      await propose(page)
      await approveAndOpenTheLog(page)
    },
  },
]

/** What every screen reads: the seeded rule set, the whole policy, the run of the 200 cases, the chat and the change. */
async function serve(page: Page): Promise<void> {
  await serveTheSeededRuleSet(page)
  await serveTheWholePolicy(page)
  await serveTheWholeRun(page)
  await serveTheChat(page)
  await serveTheChange(page)
}

test.describe('the seven screens at 1376×900', () => {
  test.use({ viewport: { width: 1376, height: 900 }, deviceScaleFactor: SHOTS ? 2 : 1 })

  for (const theme of ['light', 'dark'] as const) {
    for (const screen of SCREENS) {
      test(`${screen.name}, ${theme}: keeps the checklist`, async ({ page }) => {
        await choose(page, theme)
        await serve(page)
        await screen.reach(page)
        await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
        await settle(page)
        if (SHOTS) {
          await page.screenshot({ path: `${DEMO}register/${screen.name}-${theme}.png` })
        }
        await keepsTheChecklist(page)
      })
    }
  }
})

/** The palette over the Cases screen, 17 typed after a run: section 08's own example, in both themes. */
test.describe('the palette at 1376×900', () => {
  test.use({ viewport: { width: 1376, height: 900 }, deviceScaleFactor: SHOTS ? 2 : 1 })

  for (const theme of ['light', 'dark'] as const) {
    test(`palette, ${theme}: keeps the checklist`, async ({ page }) => {
      await choose(page, theme)
      await serve(page)
      await enter(page)
      await open(page, 'Cases')
      await runTheCases(page)
      await page.keyboard.press('ControlOrMeta+K')
      await page.getByRole('combobox', { name: 'Go to' }).fill('17')
      await expect(
        page.getByRole('dialog', { name: 'Go to' }).getByRole('option').first(),
      ).toHaveAttribute('aria-selected', 'true')
      await settle(page)
      if (SHOTS) {
        await page.screenshot({ path: `${DEMO}register/palette-${theme}.png` })
      }
      await keepsTheChecklist(page)
    })
  }
})

/**
 * The presenter's four steps through the guided panel, as the demo runs them, each screen checked where the step
 * leaves it and, asked for, written as <device>-<n>-<step>.png. On a phone the panel is in the menu, which each step
 * run from it closes, and the page scrolls as one, so its picture is the window with what the step shows at its top.
 */
async function walk(page: Page, device: 'desktop' | 'phone'): Promise<void> {
  const phone = device === 'phone'
  const shoot = async (name: string, subject: Locator) => {
    if (phone) {
      // a phone's page scrolls as one: what the step shows stands at the top of the window
      await subject.evaluate((element: { scrollIntoView(options: { block: string }): void }) =>
        element.scrollIntoView({ block: 'start' }),
      )
    }
    await settle(page)
    if (SHOTS) {
      await page.screenshot({ path: `${DEMO}${device}-${name}.png` })
    }
    await keepsTheChecklist(page)
  }
  const run = async (title: string) => {
    if (phone) {
      await page.getByRole('button', { name: 'Menu' }).click()
    }
    await step(page, title).click()
  }
  await choose(page, 'light')
  await serve(page)
  await serveTheSession(page, wholePolicy)
  await enter(page)
  if (phone) {
    await page.getByRole('button', { name: 'Menu' }).click()
  }
  await page.getByRole('button', { name: /guided demo/i }).click()
  if (phone) {
    await page.getByRole('button', { name: 'Menu' }).click()
  }

  await run('Author')
  await page.getByRole('button', { name: 'Add policy' }).click()
  await generate(page)
  await shoot('1-author', page.getByText('A draft rule set was written from this policy:'))
  await page.getByRole('button', { name: 'Review the draft' }).click()
  const conflict = page.getByRole('table').getByRole('row').filter({ hasText: 'R-110' })
  await expect(conflict.getByText('Conflict')).toBeVisible()
  await shoot('1-review', conflict)

  await run('Decide')
  await expect(page.getByRole('heading', { level: 1, name: 'Cases' })).toBeVisible()
  await openCase17(page)
  await shoot('2-decide', page.getByRole('complementary', { name: 'Case 17' }))

  await run('Ask')
  await page.getByRole('button', { name: 'Ask' }).click()
  await expect(
    page.getByRole('group', { name: 'Sources' }).getByRole('button', { name: 'Case 17' }),
  ).toBeVisible()
  await ask(page, question('Q-02').question)
  await expect(page.getByRole('list', { name: 'Tool calls' }).last()).toContainText(
    'what-if · case 17 · has_guarantor=true',
  )
  await ask(page, question('Q-03').question)
  await expect(
    page
      .getByRole('group', { name: 'Sources' })
      .last()
      .getByRole('button', { name: 'Paragraph 2' }),
  ).toBeVisible()
  await ask(page, question('Q-04').question)
  await expect(page.getByText(notCovered('he'))).toBeVisible()
  await shoot('3-ask', page.getByRole('log'))

  await run('Change')
  await page.getByRole('button', { name: 'Propose the change' }).click()
  const report = page.getByRole('region', { name: 'Regression report' })
  await expect(report).toBeVisible()
  await shoot('4-change', report)
  await approveAndOpenTheLog(page)
  await shoot(
    '4-audit',
    page.getByRole('region', { name: 'Audit log' }).getByRole('listitem').first(),
  )
}

test.describe('the demo on a desktop', () => {
  test.use({ viewport: { width: 1376, height: 900 }, deviceScaleFactor: SHOTS ? 2 : 1 })

  test('keeps the checklist at every step, the guided panel open', async ({ page }) => {
    test.setTimeout(120_000)
    await walk(page, 'desktop')
  })
})

test.describe('the demo on a phone', () => {
  test.use({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
    deviceScaleFactor: SHOTS ? 3 : 1,
  })

  test('keeps the checklist at every step, the guided panel in the menu', async ({ page }) => {
    test.setTimeout(120_000)
    await walk(page, 'phone')
  })
})

/**
 * The first impression is light (the spec, section 12: "Light by default ... dark is a toggle remembered per browser,
 * never the first impression"): the page asks the browser for light before any stylesheet, and the theme is on the
 * root before the body exists, so no frame is drawn in another. Each change of the root's theme and the body's arrival
 * is recorded in order from the document's creation.
 */
async function recordTheFirstFrames(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const view = globalThis as unknown as {
      document: {
        documentElement: { getAttribute(name: string): string | null } | null
        body: unknown
      }
      MutationObserver: new (
        callback: (
          records: { type: string; addedNodes: ArrayLike<{ nodeName: string }> }[],
        ) => void,
      ) => { observe(target: unknown, options: Record<string, unknown>): void }
      firstFrames: string[]
    }
    view.firstFrames = []
    new view.MutationObserver((records) => {
      for (const record of records) {
        if (record.type === 'attributes') {
          view.firstFrames.push(
            `theme ${String(view.document.documentElement?.getAttribute('data-theme'))}`,
          )
        }
        for (const node of Array.from(record.addedNodes)) {
          if (node.nodeName === 'BODY') {
            view.firstFrames.push('body')
          }
        }
      }
    }).observe(view.document, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['data-theme'],
    })
  })
}

async function firstFrames(page: Page): Promise<string[]> {
  return page.evaluate(() => (globalThis as unknown as { firstFrames: string[] }).firstFrames)
}

test.describe('the first paint', () => {
  test.use({ colorScheme: 'dark' })

  test('is light where nothing was chosen, on a system that prefers dark', async ({ page }) => {
    await recordTheFirstFrames(page)
    await serveTheSeededRuleSet(page)
    await page.goto('/')
    await expect(page.getByLabel('Access code')).toBeVisible()

    await expect(page.locator('meta[name="color-scheme"]')).toHaveAttribute('content', 'light')
    expect((await firstFrames(page)).slice(0, 2)).toEqual(['theme light', 'body'])
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
    // the paper of the light theme, --paper: #f5f4ef
    await expect(page.locator('body')).toHaveCSS('background-color', 'rgb(245, 244, 239)')
  })

  test('is dark from the first frame in a browser that chose dark', async ({ page }) => {
    await choose(page, 'dark')
    await recordTheFirstFrames(page)
    await serveTheSeededRuleSet(page)
    await page.goto('/')
    await expect(page.getByLabel('Access code')).toBeVisible()

    expect((await firstFrames(page)).slice(0, 2)).toEqual(['theme dark', 'body'])
  })
})
