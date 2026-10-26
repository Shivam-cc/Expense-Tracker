import { daysBetween, parseLocalDate, toLocalDateString } from './date'

const MONTHLY_THRESHOLD_DAYS = 92

/**
 * Buckets expenses into daily points (short ranges) or monthly points (long ranges),
 * filling empty buckets with 0 so the chart axis is continuous.
 */
export function buildTrend(expenses, startDate, endDate) {
  const monthly = daysBetween(startDate, endDate) > MONTHLY_THRESHOLD_DAYS
  const totals = new Map()
  for (const e of expenses) {
    const key = monthly ? e.date.slice(0, 7) : e.date
    totals.set(key, (totals.get(key) || 0) + Number(e.amount))
  }

  const points = []
  const end = parseLocalDate(endDate)
  if (monthly) {
    const cursor = parseLocalDate(startDate)
    cursor.setDate(1)
    while (cursor <= end) {
      const key = toLocalDateString(cursor).slice(0, 7)
      points.push({
        key,
        label: cursor.toLocaleDateString(undefined, { month: 'short', year: '2-digit' }),
        total: totals.get(key) || 0,
      })
      cursor.setMonth(cursor.getMonth() + 1)
    }
  } else {
    const cursor = parseLocalDate(startDate)
    while (cursor <= end) {
      const key = toLocalDateString(cursor)
      points.push({
        key,
        label: cursor.toLocaleDateString(undefined, { day: 'numeric', month: 'short' }),
        total: totals.get(key) || 0,
      })
      cursor.setDate(cursor.getDate() + 1)
    }
  }
  return { points, monthly }
}
