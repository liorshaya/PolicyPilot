import { expect, test } from '@playwright/test'
import { inspect, type Findings } from './checklist'
import { serveTheSeededRuleSet } from './seeded'

// @requirement NFR-5

/**
 * The checklist's own test (Document 9, phase 6): a check that finds nothing proves nothing until it is seen to find
 * what it looks for. Each fault the spec's section 12 forbids is planted on the access gate, a screen that keeps every
 * check (register.spec.ts), and the check named for it must report it; the gate is loaded again for the next one.
 */

interface Plant {
  /** The fault, in the words of the check that must find it. */
  fault: string
  /** Markup appended to the page, drawn by its own style attributes. */
  html: string
  check: keyof Findings
  /** What the report says of it. */
  says: string
}

const PLANTS: Plant[] = [
  {
    fault: 'an element wider than its container',
    html: '<div style="width: 100px"><div style="width: 300px">wide</div></div>',
    check: 'overflow',
    says: 'past div',
  },
  {
    fault: 'text under 11px',
    html: '<span style="font-size: 10px">tiny</span>',
    check: 'floor',
    says: '"tiny" 10px, under 12px',
  },
  {
    fault: 'Hebrew under 13px',
    html: '<span lang="he" dir="rtl" style="font-size: 12px">עברית קטנה</span>',
    check: 'floor',
    says: '12px, under 13px',
  },
  {
    fault: "the serif's running text under 16px",
    html: '<p style="font-family: \'Frank Ruhl Libre\'; font-size: 14px">a paragraph in the serif</p>',
    check: 'floor',
    says: '14px, under 16px',
  },
  {
    fault: 'text under 4.5:1',
    html: '<span style="color: #b8b8b8">faint</span>',
    check: 'contrast',
    says: '"faint"',
  },
  {
    fault: 'capitals outside the seal',
    html: '<span style="text-transform: uppercase">shouted</span>',
    check: 'capitals',
    says: '"shouted"',
  },
  {
    fault: 'a gradient',
    html: '<div style="width: 20px; height: 20px; background-image: linear-gradient(#fff, #000)"></div>',
    check: 'gradient',
    says: 'linear-gradient(',
  },
  {
    fault: 'a face that is not Plex or Frank Ruhl Libre',
    html: '<span style="font-family: Arial">arial</span>',
    check: 'fonts',
    says: 'asks for Arial first',
  },
  {
    fault: 'purple',
    html: '<span style="color: #6a2fb8">violet</span>',
    check: 'dont',
    says: 'no purple, indigo or violet',
  },
  {
    fault: 'glassmorphism',
    html: '<div style="width: 20px; height: 20px; backdrop-filter: blur(8px)"></div>',
    check: 'dont',
    says: 'no glassmorphism, no blurred blobs',
  },
  {
    fault: 'a rounded-xl corner',
    html: '<div style="width: 20px; height: 20px; border-radius: 12px"></div>',
    check: 'dont',
    says: 'no rounded-xl',
  },
  {
    fault: 'a drop shadow on a panel',
    html: '<div style="width: 20px; height: 20px; box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2)"></div>',
    check: 'dont',
    says: 'no drop shadows on panels',
  },
  {
    fault: 'a switch',
    html: '<div role="switch" aria-checked="true">on</div>',
    check: 'dont',
    says: 'no switches',
  },
  {
    fault: 'an avatar',
    html: '<img alt="" width="20" height="20" src="data:image/gif;base64,R0lGODlhAQABAAAAACw=">',
    check: 'dont',
    says: 'no avatars',
  },
  {
    fault: 'a sparkle',
    html: '<span>✨ new</span>',
    check: 'dont',
    says: 'no emoji, no sparkles',
  },
  {
    fault: 'an exclamation mark',
    html: '<span>Done!</span>',
    check: 'dont',
    says: 'no exclamation marks',
  },
  {
    fault: '"Submit"',
    html: '<button type="button">Submit</button>',
    check: 'dont',
    says: 'no "Get started", "Submit", "Learn more"',
  },
  {
    fault: 'an arrow welded to a link',
    html: '<a href="#">Open the rules →</a>',
    check: 'dont',
    says: 'no "→" welded to links',
  },
  {
    fault: 'lorem ipsum',
    html: '<p>Lorem ipsum dolor sit amet</p>',
    check: 'dont',
    says: 'no lorem ipsum or placeholder people',
  },
  {
    fault: 'a centred number',
    html: '<table><tbody><tr><td style="text-align: center; font-variant-numeric: tabular-nums">1,234</td></tr></tbody></table>',
    check: 'dont',
    says: 'no centred numbers',
  },
  {
    fault: 'proportional figures in a column',
    html: '<table><tbody><tr><td style="font-variant-numeric: proportional-nums">1,234</td></tr></tbody></table>',
    check: 'dont',
    says: 'no proportional figures in a column',
  },
]

test.describe('the checklist finds each fault planted for it', () => {
  test.beforeEach(async ({ page }) => {
    await serveTheSeededRuleSet(page)
  })

  for (const plant of PLANTS) {
    test(plant.fault, async ({ page }) => {
      await page.goto('/')
      await expect(page.getByLabel('Access code')).toBeVisible()
      expect((await inspect(page))[plant.check], 'the gate keeps the check').toEqual([])

      await page.evaluate((html) => {
        const view = globalThis as unknown as {
          document: {
            querySelector(selectors: string): {
              insertAdjacentHTML(where: string, html: string): void
            }
          }
        }
        view.document.querySelector('#root').insertAdjacentHTML('beforeend', html)
      }, plant.html)

      expect((await inspect(page))[plant.check].join('\n')).toContain(plant.says)
    })
  }

  test('something that moves on its own at rest', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByLabel('Access code')).toBeVisible()
    expect((await inspect(page)).dont).toEqual([])

    await page.evaluate(() => {
      const view = globalThis as unknown as {
        document: {
          querySelector(selectors: string): {
            animate(frames: Record<string, string>[], timing: Record<string, number>): unknown
          }
        }
      }
      view.document
        .querySelector('#root')
        .animate([{ opacity: '1' }, { opacity: '0.9' }], { duration: 1000, iterations: Infinity })
    })

    expect((await inspect(page)).dont.join('\n')).toContain('nothing animates on its own')
  })
})
