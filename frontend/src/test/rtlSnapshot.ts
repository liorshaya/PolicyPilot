/**
 * An RTL snapshot (Document 6, Frontend Test Design, "Visual and RTL"; Work Plan day 14): every text of a rendered
 * region in document order, one line each, with the direction it is laid out in, its language, and whether a diff
 * struck it out or inserted it. That is the part of the rendered DOM direction handling decides, so a change in it
 * shows as a short diff a person can review, where a snapshot of the whole DOM would bury it in markup.
 *
 * The convention: a snapshot records only what the committed fixtures and the component put on the screen, never a
 * generated id, a date that depends on the clock or a timing; and an invisible character is written as `<U+2068>`, so
 * a snapshot file never carries one (Document 5, source integrity).
 */
export function rtlSnapshot(root: Element): string {
  const lines: string[] = []
  const walker = root.ownerDocument.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  for (let node = walker.nextNode(); node !== null; node = walker.nextNode()) {
    const text = (node.textContent ?? '').replace(/\s+/g, ' ').trim()
    const element = node.parentElement
    if (text === '' || element === null) {
      continue
    }
    // "page" is the chrome's own direction, inherited from the document: English, left to right
    const dir = element.closest('[dir]')?.getAttribute('dir') ?? 'page'
    const lang = element.closest('[lang]')?.getAttribute('lang') ?? '-'
    const edit = element.closest('del') ? 'deleted' : element.closest('ins') ? 'inserted' : ''
    const visible = text.replace(
      /[\p{Cc}\p{Cf}]/gu,
      (char) => `<U+${(char.codePointAt(0) ?? 0).toString(16).toUpperCase().padStart(4, '0')}>`,
    )
    lines.push(`${dir.padEnd(4)} ${lang.padEnd(2)} ${edit.padEnd(8)} ${visible}`.trimEnd())
  }
  return `\n${lines.join('\n')}\n`
}
