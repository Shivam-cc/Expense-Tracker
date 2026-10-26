import { expensesApi } from './api'

const TREND_PAGE_SIZE = 100
const TREND_MAX_PAGES = 5

/**
 * The API has no aggregate-by-day endpoint, so the trend is built client-side from the expenses in the
 * range. At most TREND_MAX_PAGES pages are fetched; `truncated` tells the UI when that cap was hit.
 */
export async function fetchTrendExpenses(startDate, endDate) {
  const collected = []
  let truncated = false
  for (let page = 0; page < TREND_MAX_PAGES; page++) {
    const data = await expensesApi.list({
      page, size: TREND_PAGE_SIZE, sortBy: 'date', direction: 'desc', startDate, endDate,
    })
    collected.push(...(data.content || []))
    const totalPages = data.totalPages ?? Math.ceil((data.totalElements || 0) / TREND_PAGE_SIZE)
    if (page + 1 >= totalPages) break
    if (page + 1 >= TREND_MAX_PAGES) truncated = true
  }
  // Older backends ignore the date filters, so the range is enforced here as well (ISO dates compare as strings).
  const expenses = collected.filter((e) => e.date >= startDate && e.date <= endDate)
  return { expenses, truncated }
}
