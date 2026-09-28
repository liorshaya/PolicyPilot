/**
 * Hands the reader a file the API served, as the browser's own download: an object URL for the one click of a link
 * made for it, released straight after (a decision's trace, the audit log).
 */
export function saveFile(blob: Blob, name: string): void {
  const href = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = href
  anchor.download = name
  anchor.click()
  URL.revokeObjectURL(href)
}
