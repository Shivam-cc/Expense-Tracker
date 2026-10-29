import { Wallet, CalendarDays, Receipt, PieChart, TrendingUp, TrendingDown, Minus } from 'lucide-react'
import Skeleton from '../ui/Skeleton'
import { formatMoney } from '../../utils/format'

function Card({ icon: Icon, iconBg, iconColor, label, children }) {
  return (
    <div className="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
      <div className="flex items-start gap-3">
        <div className={`w-10 h-10 shrink-0 ${iconBg} rounded-lg flex items-center justify-center`}>
          <Icon className={`w-5 h-5 ${iconColor}`} aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-xs text-gray-500 font-medium uppercase tracking-wide">{label}</p>
          {children}
        </div>
      </div>
    </div>
  )
}

function Value({ children }) {
  return <p className="text-xl font-bold text-gray-900 break-words">{children}</p>
}

function Hint({ children }) {
  return <p className="text-xs text-gray-500 mt-0.5">{children}</p>
}

function Placeholder({ loading, failed, onRetry }) {
  if (failed) {
    return (
      <button type="button" onClick={onRetry} className="text-sm text-primary-600 hover:text-primary-700 underline">
        Couldn&apos;t load · Retry
      </button>
    )
  }
  if (loading) {
    return (
      <>
        <Skeleton className="h-6 w-28 mt-1" />
        <Skeleton className="h-3 w-20 mt-2" />
      </>
    )
  }
  return <Value>—</Value>
}

// Spending going up is bad news, so an increase is red and a decrease is green.
function Delta({ change }) {
  if (change === null) return <Hint>No previous data</Hint>
  const rounded = Math.round(change * 10) / 10
  if (rounded === 0) {
    return (
      <p className="text-xs text-gray-500 mt-0.5 flex items-center gap-1">
        <Minus className="w-3.5 h-3.5 shrink-0" aria-hidden="true" /> Same as last period
      </p>
    )
  }
  const up = rounded > 0
  const Icon = up ? TrendingUp : TrendingDown
  return (
    <p className={`text-xs font-medium mt-0.5 flex flex-wrap items-center gap-x-1 ${up ? 'text-red-600' : 'text-green-600'}`}>
      <Icon className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
      <span>{Math.abs(rounded)}% {up ? 'higher' : 'lower'}</span>
      <span className="text-gray-500 font-normal">vs last period</span>
    </p>
  )
}

export default function SummaryCards({ kpis, loading, failed, onRetry, expensesLoading, currency }) {
  const placeholder = <Placeholder loading={loading} failed={failed} onRetry={onRetry} />
  const share = kpis?.topCategory ? Math.round(kpis.topCategory.share) : 0

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
      <Card icon={Wallet} iconBg="bg-red-50" iconColor="text-red-500" label="Total spent">
        {kpis ? (
          <>
            <Value>{formatMoney(kpis.total, currency)}</Value>
            {kpis.previousTotal !== null && <Delta change={kpis.change} />}
          </>
        ) : placeholder}
      </Card>

      <Card icon={CalendarDays} iconBg="bg-primary-50" iconColor="text-primary-500" label="Daily average">
        {kpis ? (
          <>
            <Value>{formatMoney(kpis.dailyAverage, currency)}</Value>
            <Hint>per day over {kpis.days} {kpis.days === 1 ? 'day' : 'days'}</Hint>
          </>
        ) : placeholder}
      </Card>

      <Card icon={Receipt} iconBg="bg-amber-50" iconColor="text-amber-600" label="Transactions">
        {kpis && kpis.count !== null ? (
          <>
            <Value>{kpis.count}{kpis.countIsLowerBound ? '+' : ''}</Value>
            <Hint>
              {kpis.averageExpense !== null ? `${formatMoney(kpis.averageExpense, currency)} average each` : 'in this period'}
            </Hint>
          </>
        ) : kpis && expensesLoading ? (
          <Placeholder loading />
        ) : (
          <Value>—</Value>
        )}
      </Card>

      <Card icon={PieChart} iconBg="bg-accent-50" iconColor="text-accent-600" label="Top category">
        {kpis ? (
          kpis.topCategory ? (
            <>
              <Value>{kpis.topCategory.name}</Value>
              <Hint>{formatMoney(kpis.topCategory.total, currency)} · {share}% of total</Hint>
            </>
          ) : (
            <>
              <Value>—</Value>
              <Hint>No spending in this period</Hint>
            </>
          )
        ) : placeholder}
      </Card>
    </div>
  )
}
