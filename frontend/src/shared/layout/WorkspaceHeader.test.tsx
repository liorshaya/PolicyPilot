import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { rule, specRules, stylesheet, unported } from '../../test/css'
import { Button } from '../ui/Button'
import { VersionTag } from '../ui/StatusTag'
import { PaletteContext } from '../ui/paletteContext'
import { WorkspaceHeader } from './WorkspaceHeader'

/**
 * The workspace header (the spec, section 08): the screen's title at 20/600 with the version status beside it, the
 * provenance line in one line, and on the end at most one secondary action, the reason when the primary is disabled,
 * then the primary at 36px.
 */
const css = stylesheet('shared/layout/WorkspaceHeader.css')

describe('WorkspaceHeader', () => {
  it('titles the screen at --text-lg, named by its title, with the version status beside the title', () => {
    render(<WorkspaceHeader title="Rules" version={<VersionTag status="DRAFT" versionNo={2} />} />)
    const title = screen.getByRole('heading', { level: 1, name: 'Rules' })

    expect(title).toHaveClass('ws-header__title')
    expect(within(title).getByText('Draft v2')).toHaveClass('vstatus--draft')
    expect(rule(css, '.ws-header__title')['font-size']).toBe('var(--text-lg)')
  })

  it('draws the provenance line as one .prov element, its segments children with no separator in the text', () => {
    const { container } = render(
      <WorkspaceHeader
        title="Rules"
        provenance={['consumer-lending', 'your copy of the seeded rule set', '20 rules']}
      />,
    )
    const lines = container.querySelectorAll('.prov')

    expect(lines).toHaveLength(1)
    expect([...lines[0]!.children].map((segment) => segment.textContent)).toStrictEqual([
      'consumer-lending',
      'your copy of the seeded rule set',
      '20 rules',
    ])
    expect(lines[0]!.textContent).not.toMatch(/·/)
  })

  it('shows at most one secondary action and one primary, the primary at 36px', () => {
    const { container } = render(
      <WorkspaceHeader
        title="Cases"
        secondary={<Button>Open the rules</Button>}
        primary={<Button variant="primary">Run 200 cases</Button>}
      />,
    )
    const side = container.querySelector('.ws-header__side')!

    expect(
      within(side as HTMLElement)
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toStrictEqual(['Open the rules', 'Run 200 cases'])
    expect(rule(css, '.ws-header__side .btn--primary').height).toBe('var(--control-h-lg)')
  })

  it('writes the reason for a disabled primary beside it, before it in the DOM, never as a tooltip', () => {
    render(
      <WorkspaceHeader
        title="Rules"
        reason="7 findings to acknowledge"
        primary={
          <Button variant="primary" disabled>
            Publish version 2
          </Button>
        }
      />,
    )
    const reason = screen.getByText('7 findings to acknowledge')
    const publish = screen.getByRole('button', { name: 'Publish version 2' })

    expect(reason).toHaveClass('reason')
    expect(reason.compareDocumentPosition(publish) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(publish).not.toHaveAttribute('title')
  })

  // the spec, section 10: the Rules and Cases headers open the palette from "Go to ⌘K", before their secondary
  it('offers Go to ⌘K first on its end when the palette can open, and opens it', async () => {
    const open = vi.fn()
    const { container } = render(
      <PaletteContext value={open}>
        <WorkspaceHeader title="Cases" goTo secondary={<Button>Decide a case</Button>} />
      </PaletteContext>,
    )

    const side = container.querySelector('.ws-header__side')!
    expect([...side.children].map((control) => control.textContent)).toStrictEqual([
      'Go to ⌘K',
      'Decide a case',
    ])
    expect(within(side.firstElementChild as HTMLElement).getByText('⌘K')).toHaveClass('kbd')
    // the specimen's style attribute, a rule of the header's own
    expect(rule(css, '.ws-header__side .btn .kbd')).toStrictEqual({ 'margin-inline-start': '4px' })
    await userEvent.click(screen.getByRole('button', { name: /^Go to/ }))
    expect(open).toHaveBeenCalledOnce()
  })

  it('offers no Go to where no palette can open', () => {
    render(<WorkspaceHeader title="Cases" goTo />)

    expect(screen.queryByRole('button', { name: /^Go to/ })).not.toBeInTheDocument()
  })

  // the spec, section 08 (v3.9): where the header has no room for them, the status and the seeded tag wrap under the
  // title, never under the actions (at 1024px "Seeded, read-only" ran under Go to, at 320px past the window), and the
  // text's one column holds to the header, so a long title row cannot widen it
  it("carries the spec's header, the title row wrapping its tags and the text held to its column", () => {
    // the block's grid of the body is SplitView's to draw
    const header = specRules('/* Workspace and header */', '/* Sheet and margin').filter(
      ([selector]) => !selector.startsWith('.ws-body'),
    )

    expect(header).toHaveLength(6)
    expect(unported(css, header)).toEqual([])
    expect(rule(css, '.ws-header__title')['flex-wrap']).toBe('wrap')
    expect(rule(css, '.ws-header__text')['grid-template-columns']).toBe('minmax(0, 1fr)')
  })
})
