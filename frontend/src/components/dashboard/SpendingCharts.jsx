import { Bar, BarChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import Skeleton from '../ui/Skeleton'
import ErrorState from '../ui/ErrorState'
import { formatMoney } from '../../utils/format'

const PALETTE = ['#3b82f6', '#22c55e', '#a855f7', '#f97316', '#ec4899', '#14b8a6', '#eab308', '#6366f1']

function compact(value, currency) {
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency, notation: 'compact', maximumFractionDigits: 1 }).format(value)
  } catch {
    return String(value)
  }
}

function ChartCard({ title, subtitle, children }) {
  return (
    <section className="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
      <h3 className="text-sm font-semibold text-gray-700">{title}</h3>
      {subtitle && <p className="text-xs text-gray-400 mb-3">{subtitle}</p>}
      {!subtitle && <div className="mb-3" />}
      {children}
    </section>
  )
}

function CategoryBreakdown({ items, loading, failed, onRetry, currency }) {
  if (failed) return <ErrorState compact message="Could not load the category breakdown." onRetry={onRetry} />
  if (loading) return <Skeleton className="h-52 w-full" />
  if (items.length === 0) {
    return <p className="text-sm text-gray-400 py-12 text-center">No spending in this period.</p>
  }
  const data = items.map((c, i) => ({ ...c, fill: c.color || PALETTE[i % PALETTE.length] }))
  const total = data.reduce((sum, c) => sum + c.total, 0)
  return (
    <div className="flex flex-col sm:flex-row items-center gap-4">
      <div className="w-44 h-44 flex-shrink-0" role="img" aria-label="Donut chart of spending by category">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={data} dataKey="total" nameKey="name" innerRadius="60%" outerRadius="100%" paddingAngle={data.length > 1 ? 2 : 0} stroke="none">
              {data.map((c) => <Cell key={c.name} fill={c.fill} />)}
            </Pie>
            <Tooltip formatter={(v) => formatMoney(v, currency)} />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className="flex-1 w-full space-y-2 min-w-0">
        {data.slice(0, 6).map((c) => (
          <li key={c.name} className="flex items-center justify-between gap-3 text-sm">
            <span className="flex items-center gap-2 min-w-0">
              <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: c.fill }} aria-hidden="true" />
              <span className="truncate text-gray-700">{c.icon ? `${c.icon} ` : ''}{c.name}</span>
            </span>
            <span className="text-gray-900 font-medium flex-shrink-0">
              {formatMoney(c.total, currency)}
              <span className="text-gray-400 font-normal"> · {total > 0 ? Math.round((c.total / total) * 100) : 0}%</span>
            </span>
          </li>
        ))}
        {data.length > 6 && <li className="text-xs text-gray-400">+ {data.length - 6} more</li>}
      </ul>
    </div>
  )
}

function Trend({ trend, loading, error, onRetry, truncated, currency }) {
  if (error) return <ErrorState compact message="Could not load the spending trend." onRetry={onRetry} />
  if (loading || !trend) return <Skeleton className="h-52 w-full" />
  if (trend.points.every((p) => p.total === 0)) {
    return <p className="text-sm text-gray-400 py-12 text-center">No spending in this period.</p>
  }
  return (
    <>
      <div className="h-52" role="img" aria-label={`${trend.monthly ? 'Monthly' : 'Daily'} spending bar chart`}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={trend.points} margin={{ top: 4, right: 4, left: 0, bottom: 0 }}>
            <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fontSize: 11, fill: '#9ca3af' }} minTickGap={16} />
            <YAxis tickLine={false} axisLine={false} width={52} tick={{ fontSize: 11, fill: '#9ca3af' }} tickFormatter={(v) => compact(v, currency)} />
            <Tooltip formatter={(v) => formatMoney(v, currency)} cursor={{ fill: '#f3f4f6' }} />
            <Bar dataKey="total" name="Spent" fill="#3b82f6" radius={[4, 4, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>
      {truncated && (
        <p className="text-xs text-amber-600 mt-2">Showing the most recent expenses only; older entries in this range are not included.</p>
      )}
    </>
  )
}

export default function SpendingCharts({ categoryItems, summaryLoading, summaryFailed, onRetrySummary, trend, trendLoading, trendError, onRetryTrend, truncated, currency }) {
  return (
    <div className="grid lg:grid-cols-2 gap-4 mb-6">
      <ChartCard title="Spending by Category" subtitle="For the selected date range">
        <CategoryBreakdown items={categoryItems} loading={summaryLoading} failed={summaryFailed} onRetry={onRetrySummary} currency={currency} />
      </ChartCard>
      <ChartCard title={trend?.monthly ? 'Monthly Trend' : 'Daily Trend'} subtitle="For the selected date range">
        <Trend trend={trend} loading={trendLoading} error={trendError} onRetry={onRetryTrend} truncated={truncated} currency={currency} />
      </ChartCard>
    </div>
  )
}
