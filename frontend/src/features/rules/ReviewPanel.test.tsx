import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Finding, Review, ReviewFinding } from '../../api/types'
import { rule, specRules, stylesheet, unported } from '../../test/css'
import { rtlSnapshot } from '../../test/rtlSnapshot'
import { PublishBox, ReviewPanel } from './ReviewPanel'

// @requirement FR-5

/**
 * The review of a draft (the Register spec, section 09, "The review: lifecycle, findings, acknowledgement, publish box";
 * Brief FR-5; Document 2, Flow 1). The findings are SF-1 to SF-5 of fixtures/eval/policies/consumer-lending/
 * seeded.findings.json in the reviewer's Hebrew, the ambiguity acknowledged; the words are the spec's and the product's
 * own sentences.
 */

const css = stylesheet('features/rules/ReviewPanel.css')

function finding(overrides: Partial<ReviewFinding>): ReviewFinding {
  return {
    id: 'F-1',
    kind: 'ambiguity',
    severity: 'warning',
    ruleIds: ['R-420'],
    paragraphIndexes: [4],
    message: 'המונח "הכנסה יציבה" אינו מוגדר במדיניות.',
    suggestion: 'להוסיף סימון לבדיקה ידנית',
    confidence: 0.8,
    blocking: false,
    ...overrides,
  }
}

const findings: ReviewFinding[] = [
  finding({
    acknowledgement: { note: 'נבדק ידנית לפי תלושים', at: '2026-09-24T14:02:00Z' },
  }),
  finding({
    id: 'F-2',
    kind: 'conflict',
    severity: 'error',
    ruleIds: ['R-110', 'R-115'],
    paragraphIndexes: [1, 8],
    message: 'סעיף 1 מגביל את גיל כל המבקשים ל-70, בעוד סעיף 8 מתיר לגמלאים עד גיל 75.',
    suggestion: 'יש להבהיר אם סעיף 8 הוא חריג לסעיף 1.',
    blocking: true,
  }),
  finding({
    id: 'F-3',
    kind: 'unsupported',
    severity: 'error',
    ruleIds: ['R-170'],
    paragraphIndexes: [4],
    message: 'סף ההכנסה בטיוטה הוא 8,500 בעוד הטקסט קובע 8,000.',
    blocking: true,
  }),
  finding({
    id: 'F-4',
    kind: 'gap',
    ruleIds: ['R-160'],
    paragraphIndexes: [3],
    message: 'סעיף הוותק לעצמאים אינו מכוסה.',
    blocking: true,
  }),
  finding({
    id: 'F-5',
    kind: 'duplicate',
    ruleIds: ['R-100'],
    paragraphIndexes: [1],
    message: 'R-100 ו-R-101 בודקים אותו תנאי.',
  }),
]

const review: Review = {
  status: 'DONE',
  promptVersion: 'v1',
  findings,
  coverage: { '1': [], '9': [] },
}

function renderPanel(props: Partial<Parameters<typeof ReviewPanel>[0]> = {}) {
  return render(
    <ReviewPanel
      review={review}
      language="he"
      draft
      running={false}
      onRunReview={() => undefined}
      acknowledging={null}
      onAcknowledge={() => undefined}
      onSelectRule={() => undefined}
      onShowParagraph={() => undefined}
      {...props}
    />,
  )
}

/** A finding's item, found by its code. */
function findingOf(code: string): HTMLElement {
  return screen.getByText(code, { selector: '.finding__code' }).closest<HTMLElement>('li')!
}

describe('ReviewPanel, the head and the lifecycle', () => {
  it('counts the findings, those that block publishing and those acknowledged in its head', () => {
    renderPanel()

    // the spec: "Review · 10 findings · 7 block publishing · 1 acknowledged", here the fixture's five
    const head = screen.getByRole('heading', { name: 'Review' }).closest<HTMLElement>('.sec')!
    expect(head).toHaveTextContent('Review · 5 findings · 3 block publishing · 1 acknowledged')
    expect(within(head).getByText('reviewer · model').closest('.actor')).toHaveClass('actor--model')
    expect(within(head).getByRole('button', { name: 'Run the review again' })).toBeInTheDocument()
  })

  it("states the review's lifecycle with the system mark, in the product's sentences", () => {
    const { rerender } = renderPanel()
    const status = () => document.querySelector<HTMLElement>('.review__status')!

    expect(status()).toHaveTextContent('Reviewed against ¶ 1–9. The draft has not changed since.')
    expect(status().querySelector('.actor--system')).not.toBeNull()

    for (const [next, sentence] of [
      [undefined, 'Not reviewed yet.'],
      [{ ...review, status: 'FAILED' } as Review, 'The review could not run.'],
      [
        { ...review, status: 'STALE' } as Review,
        'The draft was edited after its review; run the review again.',
      ],
    ] as const) {
      rerender(
        <ReviewPanel
          review={next}
          language="he"
          draft
          running={false}
          onRunReview={() => undefined}
          acknowledging={null}
          onAcknowledge={() => undefined}
          onSelectRule={() => undefined}
          onShowParagraph={() => undefined}
        />,
      )
      expect(status()).toHaveTextContent(sentence)
    }
    // a draft never reviewed is reviewed; any other is reviewed again
    expect(screen.getByRole('button', { name: 'Run the review again' })).toBeInTheDocument()
  })

  it('offers to review a draft that has none yet, and nothing on a published version', () => {
    const { rerender } = renderPanel({ review: undefined })

    expect(screen.getByRole('button', { name: 'Review the draft' })).toBeInTheDocument()
    rerender(
      <ReviewPanel
        review={review}
        language="he"
        draft={false}
        running={false}
        onRunReview={() => undefined}
        acknowledging={null}
        onAcknowledge={() => undefined}
        onSelectRule={() => undefined}
        onShowParagraph={() => undefined}
      />,
    )
    expect(screen.queryByRole('button', { name: /review/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Acknowledge' })).not.toBeInTheDocument()
  })
})

describe('ReviewPanel, a finding', () => {
  it('reads as its code, its kind, "Blocks publishing" when it blocks, and the claim in Hebrew', () => {
    renderPanel()

    const conflict = findingOf('F-2')
    expect(within(conflict).getByText('Conflict')).toHaveClass('finding__kind')
    // blocking is an ink outline, not a red pill (the spec, section 06)
    expect(within(conflict).getByText('Blocks publishing')).toHaveClass('tag', 'tag--ink')
    expect(within(findingOf('F-5')).queryByText('Blocks publishing')).not.toBeInTheDocument()
    const claim = within(conflict).getByText(findings[1]!.message)
    expect(claim).toHaveClass('finding__claim')
    expect(claim).toHaveAttribute('lang', 'he')
    expect(claim).toHaveAttribute('dir', 'rtl')
    expect(conflict.querySelector('.finding__gutter .sev--error')).not.toBeNull()
    expect(findingOf('F-5').querySelector('.finding__gutter .sev--warning')).not.toBeNull()
  })

  it('shows its evidence as chips on their own line, and "What to do" above its Hebrew line', async () => {
    const user = userEvent.setup()
    const onSelectRule = vi.fn()
    const onShowParagraph = vi.fn()
    renderPanel({ onSelectRule, onShowParagraph })

    const conflict = findingOf('F-2')
    const evidence = conflict.querySelector<HTMLElement>('.finding__evidence')!
    expect(within(evidence).getByText('Evidence')).toHaveClass('label')
    await user.click(within(evidence).getByRole('button', { name: 'R-115' }))
    await user.click(within(evidence).getByRole('button', { name: 'Paragraph 8' }))
    expect(onSelectRule).toHaveBeenCalledExactlyOnceWith('R-115')
    expect(onShowParagraph).toHaveBeenCalledExactlyOnceWith(8)
    const todo = conflict.querySelector<HTMLElement>('.finding__todo')!
    expect(todo.firstElementChild).toHaveTextContent('What to do')
    expect(todo).toHaveTextContent('יש להבהיר אם סעיף 8 הוא חריג לסעיף 1.')
    expect(todo).toHaveAttribute('dir', 'rtl')
  })

  it('opens the paper box in place: a note for an error, the three resolutions for a gap, nothing for a warning', async () => {
    const user = userEvent.setup()
    renderPanel()

    await user.click(within(findingOf('F-2')).getByRole('button', { name: 'Acknowledge' }))
    const note = findingOf('F-2').querySelector<HTMLElement>('.finding__ack')!
    expect(
      within(note).getByLabelText('Why the draft stands as it is (required)'),
    ).toBeInTheDocument()
    expect(within(note).getByRole('button', { name: 'Record the acknowledgement' })).toBeDisabled()

    await user.click(within(findingOf('F-4')).getByRole('button', { name: 'Acknowledge' }))
    const gap = findingOf('F-4').querySelector<HTMLElement>('.finding__ack')!
    expect(within(gap).getByRole('group', { name: 'How is the gap resolved?' })).toBeInTheDocument()
    expect(within(gap).getAllByRole('radio')).toHaveLength(3)
    expect(within(gap).getByLabelText('Note (optional)')).toBeInTheDocument()

    await user.click(within(findingOf('F-5')).getByRole('button', { name: 'Acknowledge' }))
    const warning = findingOf('F-5').querySelector<HTMLElement>('.finding__ack')!
    expect(within(warning).queryByRole('textbox')).not.toBeInTheDocument()
    expect(within(warning).queryByRole('radio')).not.toBeInTheDocument()
    expect(
      within(warning).getByRole('button', { name: 'Record the acknowledgement' }),
    ).toBeEnabled()
    expect(rule(css, '.finding__ack').background).toBe('var(--paper)')
  })

  it('records a note for an error and a resolution for a gap', async () => {
    const user = userEvent.setup()
    const onAcknowledge = vi.fn()
    renderPanel({ onAcknowledge })

    await user.click(within(findingOf('F-2')).getByRole('button', { name: 'Acknowledge' }))
    await user.type(
      within(findingOf('F-2')).getByLabelText('Why the draft stands as it is (required)'),
      'R-110 מצומצם ללא גמלאים',
    )
    await user.click(
      within(findingOf('F-2')).getByRole('button', { name: 'Record the acknowledgement' }),
    )
    await user.click(within(findingOf('F-4')).getByRole('button', { name: 'Acknowledge' }))
    await user.click(within(findingOf('F-4')).getByLabelText('A manual-check flag surfaces it'))
    await user.click(
      within(findingOf('F-4')).getByRole('button', { name: 'Record the acknowledgement' }),
    )

    expect(onAcknowledge.mock.calls).toStrictEqual([
      ['F-2', undefined, 'R-110 מצומצם ללא גמלאים'],
      ['F-4', 'flag_added', undefined],
    ])
  })

  it('shows an acknowledged finding as the inline seal with the note hung beneath it', () => {
    renderPanel()

    const done = findingOf('F-1')
    expect(done).toHaveClass('finding--done')
    const seal = done.querySelector<HTMLElement>('.seal')!
    expect(seal).toHaveClass('seal--inline')
    expect(seal).toHaveTextContent('Acknowledged14:02 · Analyst')
    const quote = done.querySelector<HTMLElement>('.he-quote p')!
    expect(quote).toHaveTextContent('נבדק ידנית לפי תלושים')
    expect(quote).toHaveAttribute('dir', 'rtl')
    expect(within(done).queryByRole('button', { name: 'Acknowledge' })).not.toBeInTheDocument()
  })
})

/** The validator's findings on a draft (Document 3, Static Validation). */
function validated(overrides: Partial<Finding>): Finding {
  return {
    code: 'DSL-201',
    severity: 'error',
    path: '/rules/2',
    message: 'unknown field',
    ruleIds: ['R-100'],
    fieldNames: [],
    ...overrides,
  }
}

describe('PublishBox', () => {
  function renderBox(props: Partial<Parameters<typeof PublishBox>[0]> = {}) {
    return render(
      <PublishBox
        version={{ status: 'DRAFT', versionNo: 2 }}
        review={review}
        findings={[]}
        publishing={false}
        onPublish={() => undefined}
        running={false}
        onRunReview={() => undefined}
        {...props}
      />,
    )
  }

  it("lists the four gates with their facts, the product's reason, and a button that never hides", () => {
    renderBox()

    const rows = [...document.querySelectorAll('.publish-box__row')].map((row) => row.textContent)
    expect(rows).toStrictEqual([
      'Publishing version 2',
      'Only a draft is publishedDraft v2',
      'Schema and semantics valid0 problems',
      'Reviewed, and not edited since¶ 1–9',
      'Blocking findings acknowledged0 of 3',
      'Publishing waits: 3 findings must be acknowledged: F-2, F-3, F-4.Publish version 2',
    ])
    expect(document.querySelectorAll('.publish-box__check--ok')).toHaveLength(3)
    expect(document.querySelectorAll('.publish-box__check--fail')).toHaveLength(1)
    expect(
      [...document.querySelectorAll('.publish-box__id')].map((id) => id.textContent),
    ).toStrictEqual(['F-2', 'F-3', 'F-4'])
    expect(screen.getByRole('button', { name: 'Publish version 2' })).toBeDisabled()
  })

  // the spec, sections 08 and 12: the header carries the screen's one primary, the same Publish, so the box's is a
  // secondary beside its gates and its reason (the owner's answer to phase 6's eighth question)
  it('publishes with a secondary, the header holding the one primary', () => {
    renderBox()

    const publish = screen.getByRole('button', { name: 'Publish version 2' })
    expect(publish).toHaveClass('btn--secondary')
    expect(publish).not.toHaveClass('btn--primary')
  })

  it('counts the problems of the validator, and publishes a draft nothing holds back', async () => {
    const user = userEvent.setup()
    const onPublish = vi.fn()
    const acknowledged = findings.map((one) =>
      one.blocking
        ? { ...one, blocking: false, acknowledgement: { at: '2026-09-24T14:05:00Z' } }
        : one,
    )
    const { rerender } = renderBox({
      findings: [validated({}), validated({ code: 'DSL-311', severity: 'warning' })],
    })

    expect(
      screen.getByText('Schema and semantics valid').closest('.publish-box__row'),
    ).toHaveTextContent('1 problem')
    rerender(
      <PublishBox
        version={{ status: 'DRAFT', versionNo: 2 }}
        review={{ ...review, findings: acknowledged }}
        findings={[]}
        publishing={false}
        onPublish={onPublish}
        running={false}
        onRunReview={() => undefined}
      />,
    )
    expect(
      screen.getByText('Blocking findings acknowledged').closest('.publish-box__row'),
    ).toHaveTextContent('3 of 3')
    await user.click(screen.getByRole('button', { name: 'Publish version 2' }))
    expect(onPublish).toHaveBeenCalledOnce()
  })

  it('waits on a stale review, and offers to run it again in the place of publishing', async () => {
    const user = userEvent.setup()
    const onRunReview = vi.fn()
    renderBox({ review: { ...review, status: 'STALE' }, onRunReview })

    expect(document.querySelector('.publish-box__check--wait')).not.toBeNull()
    expect(document.querySelector('.publish-box__row--action')).toHaveTextContent(
      'Publishing waits: The draft was edited after its review; run the review again.',
    )
    await user.click(screen.getByRole('button', { name: 'Run the review again' }))
    expect(onRunReview).toHaveBeenCalledOnce()
  })

  it('shows a published version as the seal, and what decides cases from now on', () => {
    renderBox({
      version: { status: 'PUBLISHED', versionNo: 2, publishedAt: '2026-09-24T16:20:00Z' },
    })

    expect(document.querySelector('.publish-box__row--head')).toHaveTextContent('Published')
    expect(document.querySelector('.seal')).toHaveTextContent(
      'Publishedv2 · 2026-09-24 16:20by Analyst',
    )
    expect(
      document.querySelector<HTMLElement>('.seal')!.closest('.publish-box__row'),
    ).toHaveTextContent('Version 2 decides cases from now on. Version 1 stays readable.')
  })
})

describe('ReviewPanel.css', () => {
  it('lets the head, the reason and the ids wrap in the margin, never an id across two lines', () => {
    // the margin is 340px (the spec, section 08); the spec draws the review and its box wider
    expect(rule(css, '.review > .sec')['flex-wrap']).toBe('wrap')
    expect(rule(css, '.publish-box__row--action')['flex-wrap']).toBe('wrap')
    expect(rule(css, '.publish-box__id')['white-space']).toBe('nowrap')
  })

  it("carries every rule of the spec's review, findings and publish box, with the spec's declarations", () => {
    const review = specRules('/* Review and findings */', '/* Trace */')

    expect(review).toHaveLength(34)
    expect(unported(css, review)).toEqual([])
  })
})

describe('ReviewPanel in both directions (NFR-5)', () => {
  it("RTL: the reviewer's Hebrew reads right to left beside Latin codes, kinds and chips (snapshot)", () => {
    const { container } = renderPanel()

    expect(rtlSnapshot(container)).toMatchSnapshot()
  })
})
