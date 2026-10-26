import { Wallet, LayoutGrid, Tag } from 'lucide-react'
import Skeleton from '../ui/Skeleton'
import { formatMoney } from '../../utils/format'

function Card({ icon: Icon, iconBg, iconColor, label, children }) {
  return (
    <div className="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
      <div className="flex items-center gap-3">
        <div className={`w-10 h-10 ${iconBg} rounded-lg flex items-center justify-center`}>
          <Icon className={`w-5 h-5 ${iconColor}`} aria-hidden="true" />
        </div>
        <div className="min-w-0">
          <p className="text-xs text-gray-500 font-medium uppercase tracking-wide">{label}</p>
          {children}
        </div>
      </div>
    </div>
  )
}

export default function SummaryCards({ summary, summaryLoading, summaryFailed, onRetrySummary, expenseCount, expenseCountLoading, categoryCount, rangeLabel, currency }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
      <Card icon={Wallet} iconBg="bg-red-50" iconColor="text-red-500" label={`Spent · ${rangeLabel}`}>
        {summary ? (
          <p className="text-xl font-bold text-gray-900 truncate">{formatMoney(summary.total, currency)}</p>
        ) : summaryFailed ? (
          <button type="button" onClick={onRetrySummary} className="text-sm text-primary-600 hover:text-primary-700 underline">
            Couldn&apos;t load · Retry
          </button>
        ) : summaryLoading ? (
          <Skeleton className="h-6 w-28 mt-1" />
        ) : (
          <p className="text-xl font-bold text-gray-900">—</p>
        )}
      </Card>
      <Card icon={LayoutGrid} iconBg="bg-primary-50" iconColor="text-primary-500" label="Matching Expenses">
        {expenseCountLoading ? (
          <Skeleton className="h-6 w-12 mt-1" />
        ) : (
          <p className="text-xl font-bold text-gray-900">{expenseCount ?? '—'}</p>
        )}
      </Card>
      <Card icon={Tag} iconBg="bg-accent-50" iconColor="text-accent-600" label="Categories">
        <p className="text-xl font-bold text-gray-900">{categoryCount ?? '—'}</p>
      </Card>
    </div>
  )
}
