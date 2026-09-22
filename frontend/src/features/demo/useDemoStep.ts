import { useEffect, useRef } from 'react'

/**
 * Runs a scripted step once, when the guided panel asks for it (Document 2: the panel "adds no logic of its own").
 * A screen says what the step does there; this only makes sure it happens once and that the panel is told, so a
 * second click on the same step runs it again and a re-render does not.
 */
export function useDemoStep(asked: boolean, run: () => void, onHandled?: () => void): void {
  const handledRef = useRef(false)
  useEffect(() => {
    if (!asked) {
      handledRef.current = false
      return
    }
    if (handledRef.current) {
      return
    }
    handledRef.current = true
    run()
    onHandled?.()
  })
}
