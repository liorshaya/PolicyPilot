import { useEffect, useRef, type RefObject } from 'react'

/**
 * A target a deep link opened (the spec, section 02): brought into view once it becomes the one, and left where the
 * reader scrolls it afterwards.
 */
export function useInView<Target extends HTMLElement>(opened: boolean): RefObject<Target | null> {
  const ref = useRef<Target>(null)
  useEffect(() => {
    if (opened) {
      ref.current?.scrollIntoView({ block: 'nearest' })
    }
  }, [opened])
  return ref
}
