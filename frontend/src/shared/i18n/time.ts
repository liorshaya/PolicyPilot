/**
 * When something happened, as the Register writes it (the spec, sections 04 and 09): "10:14:07", "2026-09-22 14:02".
 * The API sends an instant; the reader sees it on the 24-hour clock in their own time zone, the date first, year to
 * day, whatever the browser's locale.
 */

const PARTS = new Intl.DateTimeFormat('en-GB', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

/** The date and time parts of an instant, in the reader's zone. */
function partsOf(
  iso: string,
): Record<'year' | 'month' | 'day' | 'hour' | 'minute' | 'second', string> {
  const found = { year: '', month: '', day: '', hour: '', minute: '', second: '' }
  for (const part of PARTS.formatToParts(new Date(iso))) {
    if (part.type in found) {
      found[part.type as keyof typeof found] = part.value
    }
  }
  return found
}

/** The time of day, "14:02", with the seconds when a record keeps them: "10:14:07". */
export function timeOf(iso: string, seconds = false): string {
  const { hour, minute, second } = partsOf(iso)
  return `${hour}:${minute}${seconds ? `:${second}` : ''}`
}

/** The date and the time, "2026-09-22 14:02", with the seconds when a record keeps them: "2026-09-23 10:14:07". */
export function dateTimeOf(iso: string, seconds = false): string {
  const { year, month, day, hour, minute, second } = partsOf(iso)
  return `${year}-${month}-${day} ${hour}:${minute}${seconds ? `:${second}` : ''}`
}

/**
 * How long something took, as the Register writes it (the spec, section 09: "58 µs", "38 ms", "4.1 s"): the smallest
 * unit that keeps it under a thousand, one decimal above the microsecond when it is under ten of its unit.
 */
export function durationText(micros: number): string {
  if (micros < 1_000) {
    return `${Math.round(micros)} µs`
  }
  const [value, unit] = micros < 1_000_000 ? [micros / 1_000, 'ms'] : [micros / 1_000_000, 's']
  return `${value < 10 ? value.toFixed(1).replace(/\.0$/, '') : String(Math.round(value))} ${unit}`
}
