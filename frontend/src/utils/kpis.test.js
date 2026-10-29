import { describe, expect, it } from 'vitest'
import { computeKpis, elapsedDays, getPreviousRange, percentChange } from './kpis'

describe('getPreviousRange', () => {
  it('returns the equal-length window ending the day before the range', () => {
    expect(getPreviousRange('2026-10-01', '2026-10-29')).toEqual({ startDate: '2026-09-02', endDate: '2026-09-30' })
    expect(getPreviousRange('2026-03-01', '2026-03-31')).toEqual({ startDate: '2026-01-29', endDate: '2026-02-28' })
  })
})

describe('elapsedDays', () => {
  it('counts only days up to today for ranges ending in the future', () => {
    expect(elapsedDays('2026-10-01', '2026-10-31', '2026-10-10')).toBe(10)
    expect(elapsedDays('2026-09-01', '2026-09-30', '2026-10-10')).toBe(30)
  })

  it('never returns less than one day', () => {
    expect(elapsedDays('2026-11-01', '2026-11-30', '2026-10-10')).toBe(1)
  })
})

describe('percentChange', () => {
  it('compares against the previous value', () => {
    expect(percentChange(150, 100)).toBe(50)
    expect(percentChange(50, 100)).toBe(-50)
  })

  it('is null without a baseline', () => {
    expect(percentChange(100, 0)).toBeNull()
  })
})

describe('computeKpis', () => {
  const summary = {
    total: 1000,
    byCategory: [
      { name: 'Food', total: 600 },
      { name: 'Travel', total: 400 },
    ],
  }
  const base = { summary, startDate: '2026-10-01', endDate: '2026-10-10', now: '2026-10-10' }

  it('derives totals, averages and top category share', () => {
    const kpis = computeKpis({ ...base, previousSummary: { total: 800, byCategory: [] }, expenses: [{}, {}, {}, {}], truncated: false })
    expect(kpis.change).toBe(25)
    expect(kpis.dailyAverage).toBe(100)
    expect(kpis.count).toBe(4)
    expect(kpis.averageExpense).toBe(250)
    expect(kpis.topCategory).toEqual({ name: 'Food', total: 600, share: 60 })
  })

  it('treats truncated expense lists as lower bounds without an average', () => {
    const kpis = computeKpis({ ...base, previousSummary: null, expenses: [{}, {}], truncated: true })
    expect(kpis.countIsLowerBound).toBe(true)
    expect(kpis.averageExpense).toBeNull()
    expect(kpis.previousTotal).toBeNull()
  })

  it('has no top category when nothing was spent', () => {
    const kpis = computeKpis({ ...base, summary: { total: 0, byCategory: [] }, previousSummary: null, expenses: [] })
    expect(kpis.topCategory).toBeNull()
    expect(kpis.averageExpense).toBeNull()
  })

  it('returns null until the summary has loaded', () => {
    expect(computeKpis({ ...base, summary: null })).toBeNull()
  })
})
