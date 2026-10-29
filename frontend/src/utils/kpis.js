import { daysBetween, parseLocalDate, today, toLocalDateString } from './date'

/** The window of equal length that ends the day before `startDate`. */
export function getPreviousRange(startDate, endDate) {
  const length = daysBetween(startDate, endDate)
  const start = parseLocalDate(startDate)
  const prevEnd = new Date(start.getFullYear(), start.getMonth(), start.getDate() - 1)
  const prevStart = new Date(prevEnd.getFullYear(), prevEnd.getMonth(), prevEnd.getDate() - (length - 1))
  return { startDate: toLocalDateString(prevStart), endDate: toLocalDateString(prevEnd) }
}

/** Days that have actually elapsed in the range (a range ending in the future only counts up to today). */
export function elapsedDays(startDate, endDate, now = today()) {
  const effectiveEnd = endDate > now ? now : endDate
  return Math.max(1, daysBetween(startDate, effectiveEnd < startDate ? startDate : effectiveEnd))
}

/** Percentage change from `previous` to `current`; null when there is no baseline to compare against. */
export function percentChange(current, previous) {
  if (!previous) return null
  return ((current - previous) / previous) * 100
}

/**
 * Derives the headline KPIs from the range summary, the previous-range summary and the range's expenses.
 * `expenses` may be capped (`truncated`), in which case count-based figures are lower bounds.
 */
export function computeKpis({ summary, previousSummary, expenses, truncated, startDate, endDate, now }) {
  if (!summary) return null
  const total = summary.total
  const days = elapsedDays(startDate, endDate, now)
  const count = expenses ? expenses.length : null
  const top = summary.byCategory.find((c) => c.total > 0) ?? null

  return {
    total,
    change: previousSummary ? percentChange(total, previousSummary.total) : null,
    previousTotal: previousSummary ? previousSummary.total : null,
    days,
    dailyAverage: total / days,
    count,
    countIsLowerBound: Boolean(truncated),
    averageExpense: count && !truncated ? total / count : null,
    topCategory: top ? { name: top.name, total: top.total, share: total > 0 ? (top.total / total) * 100 : 0 } : null,
  }
}
