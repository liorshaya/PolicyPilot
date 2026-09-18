import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App'

describe('App', () => {
  it('shows the access gate on first load', () => {
    render(<App />)

    expect(screen.getByRole('heading', { level: 1, name: 'PolicyPilot' })).toBeInTheDocument()
    expect(screen.getByLabelText('Access code')).toBeInTheDocument()
  })
})
