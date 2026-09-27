import type { ReactElement } from 'react'

/**
 * The glyphs of the Register (the spec's sprite, docs/design/register.html): drawn in the text's own colour and sized by
 * the rule of the control they sit in. A glyph is never alone: the word beside it carries the meaning.
 */
export type IconName =
  | 'check'
  | 'cross'
  | 'person'
  | 'warn'
  | 'flask'
  | 'plus'
  | 'external'
  | 'search'
  | 'help'
  | 'moon'
  | 'sun'

const GLYPHS: Record<IconName, { viewBox: string; body: ReactElement }> = {
  check: {
    viewBox: '0 0 12 12',
    body: (
      <>
        <path
          d="M2 6.2l2.6 2.6L10 3.4"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </>
    ),
  },
  cross: {
    viewBox: '0 0 12 12',
    body: (
      <>
        <path
          d="M3 3l6 6M9 3l-6 6"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
        />
      </>
    ),
  },
  person: {
    viewBox: '0 0 12 12',
    body: (
      <>
        <circle cx="6" cy="3.6" r="2.6" fill="currentColor" />
        <path d="M1 12c0-3 2.2-4.8 5-4.8s5 1.8 5 4.8z" fill="currentColor" />
      </>
    ),
  },
  warn: {
    viewBox: '0 0 14 14',
    body: (
      <>
        <path
          d="M7 1.6L13 12.4H1z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.3"
          strokeLinejoin="round"
        />
        <path
          d="M7 5.6v3.2M7 10.6v.2"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
        />
      </>
    ),
  },
  flask: {
    viewBox: '0 0 12 12',
    body: (
      <>
        <path
          d="M4.5 1.5h3M5 1.5v3.2L2.3 9.4A1 1 0 003.2 11h5.6a1 1 0 00.9-1.6L7 4.7V1.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.2"
          strokeLinejoin="round"
        />
      </>
    ),
  },
  plus: {
    viewBox: '0 0 12 12',
    body: (
      <>
        <path d="M6 2v8M2 6h8" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      </>
    ),
  },
  external: {
    viewBox: '0 0 12 12',
    body: (
      <>
        <path
          d="M5 2H2v8h8V7M7 2h3v3M10 2L5.5 6.5"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.3"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </>
    ),
  },
  search: {
    viewBox: '0 0 14 14',
    body: (
      <>
        <circle cx="6" cy="6" r="4.2" fill="none" stroke="currentColor" strokeWidth="1.4" />
        <path
          d="M9.2 9.2L12.5 12.5"
          stroke="currentColor"
          strokeWidth="1.4"
          strokeLinecap="round"
        />
      </>
    ),
  },
  help: {
    viewBox: '0 0 14 14',
    body: (
      <>
        <circle cx="7" cy="7" r="6" fill="none" stroke="currentColor" strokeWidth="1.3" />
        <path
          d="M5.2 5.4a1.9 1.9 0 113.2 1.4c-.7.5-1.2.9-1.2 1.7M7 10.4v.2"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.4"
          strokeLinecap="round"
        />
      </>
    ),
  },
  moon: {
    viewBox: '0 0 14 14',
    body: (
      <>
        <path
          d="M11.5 8.6A5 5 0 015.4 2.5a5 5 0 106.1 6.1z"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.3"
          strokeLinejoin="round"
        />
      </>
    ),
  },
  sun: {
    viewBox: '0 0 14 14',
    body: (
      <>
        <circle cx="7" cy="7" r="2.6" fill="none" stroke="currentColor" strokeWidth="1.3" />
        <path
          d="M7 1v1.6M7 11.4V13M1 7h1.6M11.4 7H13M2.8 2.8l1.1 1.1M10.1 10.1l1.1 1.1M2.8 11.2l1.1-1.1M10.1 3.9l1.1-1.1"
          stroke="currentColor"
          strokeWidth="1.3"
          strokeLinecap="round"
        />
      </>
    ),
  },
}

export function Icon({ name }: { name: IconName }) {
  const { viewBox, body } = GLYPHS[name]
  return (
    <svg viewBox={viewBox} aria-hidden="true" focusable="false" data-icon={name}>
      {body}
    </svg>
  )
}
