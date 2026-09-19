import './Logo.css'

interface LogoProps {
  /** The white logo is for the dark sidebar; navy is for light surfaces. */
  tone?: 'navy' | 'white'
  /** The rendered width in pixels; the file keeps its own proportions. */
  width?: number
}

/**
 * The PolicyPilot logo as the brand kit ships it (brand/kit/01-Logos): the file is used as it is, with its own
 * proportions and clear space around it.
 */
export function Logo({ tone = 'navy', width = 148 }: LogoProps) {
  return (
    <img
      className="logo"
      src={tone === 'white' ? '/logo-white.svg' : '/logo.svg'}
      alt="PolicyPilot"
      width={width}
      height={Math.round((width * 560) / 2048)}
    />
  )
}
