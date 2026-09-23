/**
 * When something happened, in one fixed form whatever the reader's locale or time zone: the instant in UTC, to the
 * minute, as `2026-09-27 09:12 UTC`. Text that is not an instant is shown as it is.
 */
export function formatInstant(iso: string): string {
  const instant = new Date(iso)
  if (Number.isNaN(instant.getTime())) {
    return iso
  }
  const two = (value: number) => String(value).padStart(2, '0')
  const day = `${instant.getUTCFullYear()}-${two(instant.getUTCMonth() + 1)}-${two(instant.getUTCDate())}`
  return `${day} ${two(instant.getUTCHours())}:${two(instant.getUTCMinutes())} UTC`
}
