const pad = (n) => String(n).padStart(2, '0')

// toISOString() converts to UTC, which yields the previous day for users east of UTC
// (e.g. IST before 05:30), so dates are always formatted from local calendar fields.
export function toLocalDateString(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function today() {
  return toLocalDateString(new Date())
}

export function firstOfMonth(date = new Date()) {
  return toLocalDateString(new Date(date.getFullYear(), date.getMonth(), 1))
}

/** Parses a `YYYY-MM-DD` string as a local date (new Date(str) would parse it as UTC). */
export function parseLocalDate(value) {
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function formatDisplayDate(value) {
  if (!value) return ''
  return parseLocalDate(value).toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

export const RANGE_PRESETS = [
  { id: 'month', label: 'This month' },
  { id: 'last-month', label: 'Last month' },
  { id: 'last-30', label: 'Last 30 days' },
  { id: 'year', label: 'This year' },
  { id: 'custom', label: 'Custom' },
]

/** Returns `{ startDate, endDate }` for a non-custom preset. */
export function getPresetRange(preset, now = new Date()) {
  const y = now.getFullYear()
  const m = now.getMonth()
  switch (preset) {
    case 'last-month':
      return {
        startDate: toLocalDateString(new Date(y, m - 1, 1)),
        endDate: toLocalDateString(new Date(y, m, 0)),
      }
    case 'last-30':
      return {
        startDate: toLocalDateString(new Date(y, m, now.getDate() - 29)),
        endDate: toLocalDateString(now),
      }
    case 'year':
      return { startDate: toLocalDateString(new Date(y, 0, 1)), endDate: toLocalDateString(now) }
    case 'month':
    default:
      return { startDate: firstOfMonth(now), endDate: toLocalDateString(now) }
  }
}

export function daysBetween(startDate, endDate) {
  const ms = parseLocalDate(endDate) - parseLocalDate(startDate)
  return Math.round(ms / 86400000) + 1
}
