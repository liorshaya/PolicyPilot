import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { AuditEntry } from '../../api/types'
import {
  copyRuleset,
  copyVersion,
  gapEntry,
  NOTE,
  proposedEntry,
  rejectedEntry,
  SANDBOX,
  seedPublishEntry,
  copyPublishEntry,
} from '../../test/fixtures/audit'
import { COPY_RULESET_ID, scriptedRequest } from '../../test/fixtures/change'
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
 * The audit log (Brief, demo step 4: "Version 2 is published, the audit log shows who changed what, when and why";
 * Document 2, GET /audit: a version's entries, newest first; Work Plan day 14: the audit log screen, newest first, and
 * an RTL snapshot). The entries are those the scripted change leaves (src/test/fixtures/audit.ts).
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

/** The seeded version's log, as a test chooses to fill it. */
function serveTheSeededLog(entries: AuditEntry[]) {
  server.use(
    http.get(`${BASE}/audit`, ({ request }) =>
      HttpResponse.json({
        entries:
          new URL(request.url).searchParams.get('versionId') === SEEDED_VERSION_ID ? entries : [],
      }),
    ),
  )
}

function renderScreen(rulesetId: string | null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AuditScreen rulesetId={rulesetId} />
    </QueryClientProvider>,
  )
}

async function entries(): Promise<HTMLElement[]> {
  return within(await screen.findByRole('list', { name: 'Entries' })).getAllByRole('article')
}

describe('AuditScreen', () => {
  it('lists the entries of the version newest first, in the order the API gives them', async () => {
    serveTheSeededLog([rejectedEntry, proposedEntry, seedPublishEntry])
    renderScreen(SEEDED_RULESET_ID)

    const shown = await entries()
    expect(shown.map((entry) => within(entry).getByRole('heading').textContent)).toStrictEqual([
      'Change rejected',
      'Change proposed',
      'Published',
    ])
    expect(within(shown[0]!).getByText('2026-09-27 09:20 UTC')).toBeVisible()
    expect(within(shown[2]!).getByText('2026-09-20 09:00 UTC')).toBeVisible()
  })

  it('shows an approval as who changed what, when and why: the request, the note, the stored diff and the flips', async () => {
    serveTheCopy()
    renderScreen(COPY_RULESET_ID)

    const [approval] = await entries()
    expect(within(approval!).getByRole('heading')).toHaveTextContent('Change approved')
    expect(within(approval!).getByText('2026-09-27 09:12 UTC')).toBeVisible()
    expect(within(approval!).getByText(SANDBOX)).toBeVisible()
    expect(within(approval!).getByText(scriptedRequest.text.he)).toHaveAttribute('dir', 'rtl')
    expect(within(approval!).getByText(NOTE)).toHaveAttribute('dir', 'rtl')
    // the diff and the report the approval stored, rendered as they were stored (Document 3, Structural diff)
    const diff = within(approval!).getByRole('region', {
      name: 'Changes from Version 1 to Version 2',
    })
    expect(within(diff).getByText('2 rules modified')).toBeVisible()
    expect(
      within(approval!).getByText('12 of the 200 decisions made on version 1 flip.'),
    ).toBeVisible()
  })

  it('reads every other kind of entry as what it records', async () => {
    serveTheSeededLog([copyPublishEntry, rejectedEntry, proposedEntry, gapEntry, seedPublishEntry])
    renderScreen(SEEDED_RULESET_ID)

    const [copied, rejected, proposed, gap, seeded] = await entries()
    expect(copied).toHaveTextContent('Copied from the seeded version as it was published')
    expect(within(rejected!).getByText('לא בתקופת הבחירות')).toHaveAttribute('dir', 'rtl')
    expect(proposed).toHaveTextContent('2 patches proposed on version 1')
    expect(within(gap!).getByText(/המדיניות אינה קובעת/)).toHaveAttribute('dir', 'rtl')
    // in the words the analyst chose it in the review's dialog
    expect(gap).toHaveTextContent('Resolved: A manual-check flag surfaces it')
    expect(seeded).toHaveTextContent('20 rules published, 1 warning left open')
    expect(within(seeded!).getByText('demo-analyst')).toBeVisible()
  })

  it('reads the log of another version when one is chosen', async () => {
    serveTheCopy()
    renderScreen(COPY_RULESET_ID)
    await screen.findByRole('heading', { name: 'Change approved' })

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Version' }), '1')

    expect(await screen.findByRole('heading', { name: 'Published' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Change approved' })).not.toBeInTheDocument()
  })

  it('says so when nothing has been recorded for the version', async () => {
    serveTheSeededLog([])
    renderScreen(SEEDED_RULESET_ID)

    expect(await screen.findByText('Nothing has been recorded for version 1 yet.')).toBeVisible()
  })

  it('compares any two versions side by side through the diff route', async () => {
    const asked: string[] = []
    serveTheCopy()
    server.use(
      http.get(`${BASE}/rulesets/:id/versions/:a/diff/:b`, ({ params }) => {
        asked.push(`${String(params.id)} ${String(params.a)}→${String(params.b)}`)
        return HttpResponse.json({
          fields: { added: [], removed: [], modified: [] },
          rules: { added: [], removed: [], modified: [] },
          defaults: null,
        })
      }),
    )
    renderScreen(COPY_RULESET_ID)

    const compare = within(
      (await screen.findByRole('heading', { name: 'Compare two versions' })).closest('section')!,
    )
    expect(compare.getByRole('combobox', { name: 'From' })).toHaveValue('1')
    expect(compare.getByRole('combobox', { name: 'To' })).toHaveValue('2')
    expect(
      await compare.findByText('Version 1 and Version 2 have the same rules, fields and defaults.'),
    ).toBeVisible()
    expect(asked).toStrictEqual([`${COPY_RULESET_ID} 1→2`])
  })

  it('offers nothing to compare while the rule set has one version', async () => {
    renderScreen(SEEDED_RULESET_ID)

    expect(
      await screen.findByText('This rule set has one version: there is nothing to compare yet.'),
    ).toBeVisible()
    expect(screen.queryByRole('combobox', { name: 'From' })).not.toBeInTheDocument()
  })

  it('RTL: the Hebrew of an entry turns right to left inside the English log (snapshot)', async () => {
    serveTheSeededLog([rejectedEntry, proposedEntry, seedPublishEntry])
    renderScreen(SEEDED_RULESET_ID)

    await entries()
    await waitFor(() => expect(screen.getByText('לא בתקופת הבחירות')).toHaveAttribute('lang', 'he'))
    expect(rtlSnapshot(screen.getByRole('list', { name: 'Entries' }))).toMatchSnapshot()
  })
})

describe('AuditScreen in both directions (NFR-5)', () => {
  it('LTR: an English note and request stay left to right in the log (snapshot)', async () => {
    const english = {
      ...rejectedEntry,
      details: { requestText: scriptedRequest.text.en, note: 'Not during the election period' },
    }
    serveTheSeededLog([english])
    renderScreen(SEEDED_RULESET_ID)

    const note = await screen.findByText('Not during the election period')
    expect(note).toHaveAttribute('dir', 'ltr')
    expect(screen.getByText(scriptedRequest.text.en)).toHaveAttribute('dir', 'ltr')
    expect(rtlSnapshot(screen.getByRole('list', { name: 'Entries' }))).toMatchSnapshot()
  })
})
