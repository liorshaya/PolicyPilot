import type { ReactNode } from 'react'
import './Kbd.css'

/** A key, as the shortcuts sheet and a sheet's footer write it (the spec, section 04). */
export function Kbd({ children }: { children: ReactNode }) {
  return <kbd className="kbd">{children}</kbd>
}
