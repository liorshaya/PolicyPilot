import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { rule, stylesheet } from '../../test/css'
import { Button } from '../ui/Button'
import { VersionTag } from '../ui/StatusTag'
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
})
