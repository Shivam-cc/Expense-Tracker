import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { RANGE_PRESETS, firstOfMonth, getPresetRange, today } from '../utils/date'

export const SORT_OPTIONS = [
  { value: 'date:desc', label: 'Date (newest first)' },
  { value: 'date:asc', label: 'Date (oldest first)' },
  { value: 'amount:desc', label: 'Amount (high to low)' },
  { value: 'amount:asc', label: 'Amount (low to high)' },
  { value: 'description:asc', label: 'Description (A–Z)' },
  { value: 'description:desc', label: 'Description (Z–A)' },
]

const SORT_FIELDS = ['date', 'amount', 'description']
const PRESET_IDS = RANGE_PRESETS.map((p) => p.id)
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/
const SEARCH_DEBOUNCE_MS = 300
// Values equal to these are omitted from the URL to keep it short.
const DEFAULTS = { range: 'month', sortBy: 'date', direction: 'desc', page: 1 }

/**
 * Filter state for the dashboard, kept in the URL search params so views are shareable and survive
 * reloads. Any filter change resets pagination to the first page.
 */
export function useExpenseFilters() {
  const [params, setParams] = useSearchParams()

  const q = params.get('q') || ''
  const categoryId = params.get('categoryId') || ''
  const rangeParam = params.get('range')
  const preset = PRESET_IDS.includes(rangeParam) ? rangeParam : DEFAULTS.range
  const sortByParam = params.get('sortBy')
  const sortBy = SORT_FIELDS.includes(sortByParam) ? sortByParam : DEFAULTS.sortBy
  const direction = params.get('direction') === 'asc' ? 'asc' : 'desc'
  const page = Math.max(1, parseInt(params.get('page') || '1', 10) || 1) - 1
  const customStart = params.get('start')
  const customEnd = params.get('end')

  const { startDate, endDate } = useMemo(() => {
    if (preset !== 'custom') return getPresetRange(preset)
    const start = DATE_RE.test(customStart || '') ? customStart : firstOfMonth()
    const end = DATE_RE.test(customEnd || '') ? customEnd : today()
    return start <= end ? { startDate: start, endDate: end } : { startDate: end, endDate: start }
  }, [preset, customStart, customEnd])

  const [searchInput, setSearchInput] = useState(q)
  const searchTimer = useRef(null)

  useEffect(() => () => clearTimeout(searchTimer.current), [])

  const setFilters = useCallback((patch) => {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      for (const [key, value] of Object.entries(patch)) {
        if (value === '' || value == null || DEFAULTS[key] === value) next.delete(key)
        else next.set(key, String(value))
      }
      if (!('page' in patch)) next.delete('page')
      return next
    }, { replace: true })
  }, [setParams])

  const onSearchChange = useCallback((value) => {
    setSearchInput(value)
    clearTimeout(searchTimer.current)
    searchTimer.current = setTimeout(() => setFilters({ q: value.trim() }), SEARCH_DEBOUNCE_MS)
  }, [setFilters])

  const setPreset = useCallback((id) => {
    if (id === 'custom') setFilters({ range: 'custom', start: startDate, end: endDate })
    else setFilters({ range: id, start: '', end: '' })
  }, [setFilters, startDate, endDate])

  const setSort = useCallback((value) => {
    const [field, dir] = value.split(':')
    setFilters({ sortBy: field, direction: dir })
  }, [setFilters])

  const setPage = useCallback((zeroBased) => setFilters({ page: zeroBased + 1 }), [setFilters])

  const resetFilters = useCallback(() => {
    clearTimeout(searchTimer.current)
    setSearchInput('')
    setParams({}, { replace: true })
  }, [setParams])

  const hasListFilters = Boolean(q || categoryId)
  const isModified = hasListFilters || preset !== DEFAULTS.range
    || sortBy !== DEFAULTS.sortBy || direction !== DEFAULTS.direction

  return {
    q, categoryId, preset, startDate, endDate, sortBy, direction, page,
    sortValue: `${sortBy}:${direction}`,
    searchInput, onSearchChange,
    setFilters, setPreset, setSort, setPage, resetFilters,
    hasListFilters, isModified,
  }
}
