import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AuditEntry } from '../../api/types'
import { specRules, stylesheet, unported } from '../../test/css'
import {
  approvalEntry,
  copyPublishEntry,
  copyRuleset,
  copyVersion,
  gapEntry,
  NOTE,
  proposedEntry,
  rejectedEntry,
  resetEntry,
  seedPublishEntry,
} from '../../test/fixtures/audit'
import { COPY_RULESET_ID, scriptedProposalEvent, scriptedRequest } from '../../test/fixtures/change'
import {
  publishedVersion,
  rulesets,
  SEEDED_RULESET_ID,
  SEEDED_VERSION_ID,
} from '../../test/msw/handlers'
import { server } from '../../test/msw/server'
import { rtlSnapshot } from '../../test/rtlSnapshot'
import { AuditScreen } from './AuditScreen'

// @requirement FR-19
// @requirement FR-20
// @requirement NFR-5

/**
 * The audit log (Brief, demo step 4: "Version 2 is published, the audit log shows who changed what, when and why"),
 * drawn as the Register draws it (the spec, section 09, "Audit log": append-only, newest first, grouped by day, one row
 * per audit action the API records, each a provenance line with the actor's mark; an approval expands to the request,
 * the diff and the regression it carried; the compare control; no edit or delete affordance anywhere). The entries are
 * those the scripted change leaves (src/test/fixtures/audit.ts); `GET /audit` without a version lists them all
 * (Document 2, since 2026-09-28).
 */

const BASE = 'http://localhost:8080/api/v1'

/** The sandbox after the approval: the seeded rule set, and its own copy with versions 1 and 2. */
function serveTheCopy() {
  server.use(
    http.get(`${BASE}/rulesets`, () =>
      HttpResponse.json({ rulesets: [rulesets.rulesets[0]!, copyRuleset] }),
    ),
    http.get(`${BASE}/rulesets/:id/versions/:no`, ({ params }) =>
      HttpResponse.json(
        params.id === COPY_RULESET_ID
          ? copyVersion(Number(params.no) === 1 ? 1 : 2)
          : publishedVersion,
      ),
    ),
  )
}

/** The log as a test chooses to fill it: every entry without a version, and the seeded version's own with it. */
function serveTheLog(all: AuditEntry[], seeded: AuditEntry[] = []) {
  const asked: string[] = []
  server.use(
    http.get(`${BASE}/audit`, ({ request }) => {
      const versionId = new URL(request.url).searchParams.get('versionId')
      asked.push(versionId ?? 'all')
      return HttpResponse.json({
        entries: versionId === null ? all : versionId === SEEDED_VERSION_ID ? seeded : [],
      })
    }),
  )
  return asked
}

function renderScreen(rulesetId: string | null = SEEDED_RULESET_ID) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AuditScreen rulesetId={rulesetId} />
    </QueryClientProvider>,
  )
}

/** The rows of the log, in the order the screen shows them. */
async function rows(): Promise<HTMLElement[]> {
  const log = await screen.findByRole('region', { name: 'Audit log' })
  return await waitFor(() => {
    const found = within(log).getAllByRole('listitem')
    expect(found.length).toBeGreaterThan(0)
    return found
  })
}

/** A row's line: the verb, the chips and what stands between them, as a reader sees it. */
function lineOf(row: HTMLElement): string {
  return row.querySelector('.event__line')?.textContent ?? ''
}

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('AuditScreen, the log', () => {
  it('opens on every entry the sandbox can see, newest first, grouped by day', async () => {
    const asked = serveTheLog([
      rejectedEntry,
      approvalEntry,
      copyPublishEntry,
      proposedEntry,
      gapEntry,
      seedPublishEntry,
    ])
    renderScreen()

    const shown = await rows()
    expect(shown.map((row) => row.querySelector('.event__verb')?.textContent)).toStrictEqual([
      'Change rejected',
      'Change approved',
      'Published',
      'Change proposed',
      'Finding acknowledged',
      'Published',
    ])
    const log = screen.getByRole('region', { name: 'Audit log' })
    expect(
      within(log)
        .getAllByRole('heading', { level: 3 })
        .map((day) => day.textContent),
    ).toStrictEqual(['2026-09-27', '2026-09-21', '2026-09-20'])
    expect(within(log).getByText('6 entries')).toBeVisible()
    expect(screen.getByRole('combobox', { name: 'Version' })).toHaveDisplayValue('All versions')
    expect(asked).toStrictEqual(['all'])
  })

  it("narrows the log to one version, which it reads from that version's entries", async () => {
    const asked = serveTheLog(
      [rejectedEntry, proposedEntry, seedPublishEntry, copyPublishEntry],
      [proposedEntry, seedPublishEntry],
    )
    renderScreen()
    await rows()
    const user = userEvent.setup()

    await user.selectOptions(screen.getByRole('combobox', { name: 'Version' }), 'Published v1')

    await waitFor(async () =>
      expect(
        (await rows()).map((row) => row.querySelector('.event__verb')?.textContent),
      ).toStrictEqual(['Change proposed', 'Published']),
    )
    expect(asked).toStrictEqual(['all', SEEDED_VERSION_ID])
  })

  it('says "Nothing recorded yet" on the ruled lines when there is nothing to show', async () => {
    serveTheLog([])
    renderScreen()

    const empty = await screen.findByText('Nothing recorded yet')
    expect(empty).toHaveClass('empty__rule', 'empty__rule--text')
  })
})

describe('AuditScreen, a row', () => {
  it("writes an approval as a provenance line: the time, a person's mark, the verb, the chips and the seal", async () => {
    serveTheLog([approvalEntry])
    renderScreen()

    const [approval] = await rows()
    expect(approval).toHaveClass('event', 'event--version')
    expect(approval!.querySelector('.event__time')).toHaveTextContent('09:12')
    expect(approval!.querySelector('.event__mark .actor--person')).not.toBeNull()
    // the sandbox is the actor, named as the rail names the person (the owner's answer to phase 1's first question)
    expect(lineOf(approval!)).toBe('Change approvedCR-0001publishedv2byAnalystApproved09:12')
    expect(within(approval!).getByText('CR-0001')).toHaveClass('chip', 'chip--id')
    expect(approval!.querySelector('.event__line .vstatus')).toHaveClass('vstatus--published')
    const seal = approval!.querySelector('.seal')
    expect(seal).toHaveClass('seal--inline')
    expect(seal).not.toHaveClass('seal--rejected')
    // what changed, cell by cell, from the diff the entry stored
    expect(approval!.querySelector('.event__delta')).toHaveTextContent(
      'R-170 monthly_income < 8,000 → < 9,000 · R-410 monthly_income [8,000 .. 9,000] → [9,000 .. 10,000]',
    )
    const note = within(approval!).getByText(NOTE)
    expect(note).toHaveAttribute('dir', 'rtl')
    expect(note).toHaveAttribute('lang', 'he')
    expect(note.closest('.he-quote')).not.toBeNull()
  })

  it('expands an approval to the request, the diff and the regression it carried', async () => {
    serveTheLog([approvalEntry])
    renderScreen()
    const [approval] = await rows()
    const user = userEvent.setup()
    const request = approval!.querySelector<HTMLElement>('.event__more .he-quote p')!
    expect(request).toHaveTextContent(scriptedRequest.text.he)
    expect(request).not.toBeVisible()

    await user.click(within(approval!).getByText('Show the request, the diff and the regression'))

    expect(request).toBeVisible()
    expect(request).toHaveAttribute('dir', 'rtl')
    // the regression the approval stored: 12 flips, six approved cases and six in manual review declined now
    expect(
      within(approval!).getByText('12 flipped · 6 approved→declined · 6 manual review→declined'),
    ).toBeVisible()
    expect(within(approval!).getByRole('region', { name: 'Changes from v1 to v2' })).toBeVisible()
    expect(within(approval!).getByRole('region', { name: 'Regression report' })).toBeVisible()
  })

  it("writes a proposal with the model's mark, and a rejection with the red seal and its note", async () => {
    serveTheLog([rejectedEntry, proposedEntry])
    renderScreen()

    const [rejected, proposed] = await rows()
    expect(proposed!.querySelector('.event__mark .actor--model')).not.toBeNull()
    expect(lineOf(proposed!)).toBe('Change proposedCR-0001onv1')
    // the spec's row: the request, then "2 patches · considered R-170, R-410, R-020, R-200, R-320 · 2,140 tokens"
    const asked = proposed!.querySelector('.he-quote p')
    expect(asked).toHaveTextContent(scriptedRequest.text.he)
    expect(asked).toHaveAttribute('dir', 'rtl')
    expect(proposed!.querySelector('.event__delta')).toHaveTextContent(
      /^2 patches · considered R-170, R-410, R-020, R-200, R-320 · 2,140 tokens$/,
    )
    expect(rejected!.querySelector('.event__mark .actor--person')).not.toBeNull()
    expect(lineOf(rejected!)).toBe('Change rejectedCR-0002byAnalystRejected09:20')
    expect(rejected!.querySelector('.seal')).toHaveClass('seal--inline', 'seal--rejected')
    expect(within(rejected!).getByText('לא בתקופת הבחירות').closest('.he-quote')).not.toBeNull()
  })

  it('writes a proposal recorded before its entry held the request with what it holds', async () => {
    const older: AuditEntry = {
      ...proposedEntry,
      details: { rulesetId: '0f4c1c9e-0000-4000-8000-0000000000b1', versionNo: 1, patches: 2 },
    }
    serveTheLog([older])
    renderScreen()

    const [proposed] = await rows()
    expect(proposed!.querySelector('.event__delta')).toHaveTextContent(/^2 patches$/)
    expect(proposed!.querySelector('.he-quote')).toBeNull()
  })

  it('writes a publication as a version row, with the id of the version it published', async () => {
    serveTheLog([copyPublishEntry, seedPublishEntry])
    renderScreen()

    const [copied, seeded] = await rows()
    expect(seeded).toHaveClass('event--version')
    expect(lineOf(seeded!)).toBe('Publishedv10f4c…c1')
    expect(seeded!.querySelector('.event__id')).toHaveAttribute('title', SEEDED_VERSION_ID)
    expect(seeded!.querySelector('.event__delta')).toHaveTextContent(
      '20 rules · 1 warning left open',
    )
    expect(lineOf(copied!)).toContain('copied from the seeded version as it was published')
  })

  it("names an acknowledgement 'Finding acknowledged' with the finding's kind, and quotes its note", async () => {
    serveTheLog([gapEntry])
    renderScreen()

    const [acknowledged] = await rows()
    // the API writes every acknowledgement as GAP_ACKNOWLEDGED; the row names the finding's kind (the spec, section 09)
    expect(lineOf(acknowledged!)).toBe('Finding acknowledgedF-4Gap, onDraft v1')
    expect(within(acknowledged!).getByText('Draft v1')).toHaveClass('vstatus', 'vstatus--draft')
    expect(within(acknowledged!).getByText('A manual-check flag surfaces it')).toBeVisible()
    const note = within(acknowledged!).getByText('נוסף סימון לבדיקה ידנית')
    expect(note).toHaveAttribute('dir', 'rtl')
    expect(note.closest('.he-quote')).not.toBeNull()
  })

  it("draws a reset with the system's mark", async () => {
    serveTheLog([resetEntry])
    renderScreen()

    const [reset] = await rows()
    expect(reset!.querySelector('.event__mark .actor--system')).not.toBeNull()
    expect(lineOf(reset!)).toBe('Resetthe seeded data was reset')
  })
})

describe('AuditScreen, its controls', () => {
  it('is append-only: the mark says so, and nothing on the screen edits or deletes', async () => {
    serveTheLog([rejectedEntry, approvalEntry, proposedEntry, gapEntry, seedPublishEntry])
    serveTheCopy()
    renderScreen(COPY_RULESET_ID)
    await rows()

    expect(screen.getByText('append-only')).toHaveClass('append-only')
    const buttons = screen.getAllByRole('button').map((button) => button.textContent ?? '')
    expect(buttons.filter((name) => /edit|delete|remove|undo|revert/i.test(name))).toEqual([])
    expect(screen.queryAllByRole('textbox')).toEqual([])
  })

  it('exports the log as its filter stands, through GET /audit/export', async () => {
    const exported: string[] = []
    serveTheLog([proposedEntry, seedPublishEntry], [seedPublishEntry])
    server.use(
      http.get(`${BASE}/audit/export`, ({ request }) => {
        const address = new URL(request.url)
        exported.push(
          `${request.headers.get('accept') ?? ''} ${address.searchParams.get('versionId') ?? 'all'}`,
        )
        return new HttpResponse('at,action\n', { headers: { 'Content-Type': 'text/csv' } })
      }),
    )
    const saved = vi.fn(() => 'blob:the-log')
    // jsdom has no object URLs; the page's own URL is kept and given the two it lacks
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = saved
        static override revokeObjectURL = vi.fn()
      },
    )
    const clicked = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined)
    renderScreen()
    await rows()
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Export the log' }))
    await waitFor(() => expect(saved).toHaveBeenCalledOnce())
    await user.selectOptions(screen.getByRole('combobox', { name: 'Version' }), 'Published v1')
    await user.click(screen.getByRole('button', { name: 'Export the log' }))

    await waitFor(() => expect(saved).toHaveBeenCalledTimes(2))
    // Document 2: one row per entry, and without a version every entry the sandbox can see
    expect(exported).toStrictEqual(['text/csv all', `text/csv ${SEEDED_VERSION_ID}`])
    expect(clicked).toHaveBeenCalledTimes(2)
  })

  it('compares two versions: From and To, then "Compare two versions" shows their diff', async () => {
    const asked: string[] = []
    serveTheLog([])
    serveTheCopy()
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:a/diff/:b`, ({ params }) => {
        asked.push(`${String(params.id)} ${String(params.a)}→${String(params.b)}`)
        return HttpResponse.json(scriptedProposalEvent.diff)
      }),
    )
    renderScreen(COPY_RULESET_ID)
    const user = userEvent.setup()

    const from = await screen.findByRole('combobox', { name: 'Compare · from' })
    expect(from).toHaveValue('1')
    expect(screen.getByRole('combobox', { name: 'to' })).toHaveValue('2')
    await user.click(screen.getByRole('button', { name: 'Compare two versions' }))

    expect(await screen.findByRole('region', { name: 'Changes from v1 to v2' })).toBeVisible()
    expect(asked).toStrictEqual([`${COPY_RULESET_ID} 1→2`])
  })

  it('says "Choose two different versions." when From and To are the same', async () => {
    serveTheCopy()
    renderScreen(COPY_RULESET_ID)
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByRole('combobox', { name: 'to' }), '1')
    await user.click(screen.getByRole('button', { name: 'Compare two versions' }))

    expect(screen.getByText('Choose two different versions.')).toBeVisible()
  })

  it('offers nothing to compare while the rule set has one version', async () => {
    renderScreen(SEEDED_RULESET_ID)

    expect(
      await screen.findByText('This rule set has one version: there is nothing to compare yet.'),
    ).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Compare two versions' })).not.toBeInTheDocument()
  })
})

describe('AuditScreen in both directions (NFR-5)', () => {
  it('RTL: the Hebrew of a row turns right to left inside the English log (snapshot)', async () => {
    serveTheLog([rejectedEntry, approvalEntry, proposedEntry, gapEntry, seedPublishEntry])
    renderScreen()

    await rows()
    expect(rtlSnapshot(screen.getByRole('region', { name: 'Audit log' }))).toMatchSnapshot()
  })

  it('LTR: an English note stays left to right in the log (snapshot)', async () => {
    const english = {
      ...rejectedEntry,
      details: { requestText: scriptedRequest.text.en, note: 'Not during the election period' },
    }
    serveTheLog([english])
    renderScreen()

    const note = await screen.findByText('Not during the election period')
    expect(note).toHaveAttribute('dir', 'ltr')
    expect(rtlSnapshot(screen.getByRole('region', { name: 'Audit log' }))).toMatchSnapshot()
  })
})

describe('AuditScreen.css', () => {
  it("carries every rule of the spec's audit log, with the spec's declarations", () => {
    const audit = specRules('.timeline__day {', '/* Figures */')

    expect(audit).toHaveLength(19)
    expect(unported(stylesheet('features/audit/AuditScreen.css'), audit)).toEqual([])
  })
})
