import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Kbd } from './Kbd'

/** A key of the shortcuts sheet (the spec, section 04) is a key cap. */
describe('Kbd', () => {
  it('writes a key as a key cap', () => {
    render(<Kbd>Esc</Kbd>)

    expect(screen.getByText('Esc').tagName).toBe('KBD')
    expect(screen.getByText('Esc')).toHaveClass('kbd')
  })
})
