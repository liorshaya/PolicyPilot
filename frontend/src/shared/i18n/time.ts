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

/** The date alone, "2026-09-24": the day the audit log groups an entry under. */
export function dateOf(iso: string): string {
  const { year, month, day } = partsOf(iso)
  return `${year}-${month}-${day}`
}

/** The date and the time, "2026-09-22 14:02", with the seconds when a record keeps them: "2026-09-23 10:14:07". */
export function dateTimeOf(iso: string, seconds = false): string {
  const { year, month, day, hour, minute, second } = partsOf(iso)
  return `${year}-${month}-${day} ${hour}:${minute}${seconds ? `:${second}` : ''}`
}

/**
 * How long something took, as the Register writes it (the spec, sections 08 and 09: "58 µs", "12 ms", "38 ms", "0.8 s",
 * "4.1 s"): microseconds under a millisecond, whole milliseconds under a tenth of a second (one decimal under ten), and
 * seconds with one decimal from there.
 */
export function durationText(micros: number): string {
  if (micros < 1_000) {
    return `${Math.round(micros)} µs`
  }
  if (micros < 100_000) {
    const ms = micros / 1_000
    return `${ms < 10 ? ms.toFixed(1).replace(/\.0$/, '') : String(Math.round(ms))} ms`
  }
  return `${(micros / 1_000_000).toFixed(1)} s`
}
