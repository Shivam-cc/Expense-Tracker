import { Pencil, Trash2, ChevronLeft, ChevronRight, Wallet, SearchX, Plus } from 'lucide-react'
import { ExpenseRowSkeleton } from '../ui/Skeleton'
import ErrorState from '../ui/ErrorState'
import { formatMoney } from '../../utils/format'
import { formatDisplayDate } from '../../utils/date'

function EmptyState({ filtered, onAdd, onClear }) {
  if (filtered) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center px-4">
        <SearchX className="w-10 h-10 mb-3 text-gray-300" aria-hidden="true" />
        <p className="text-sm font-medium text-gray-700 mb-1">No expenses match your filters</p>
        <p className="text-sm text-gray-400 mb-4">Try a different search, category, or date range.</p>
        <button
          type="button" onClick={onClear}
          className="px-3.5 py-2 border border-gray-300 rounded-lg text-sm text-gray-700 hover:bg-gray-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
        >
          Clear filters
        </button>
      </div>
    )
  }
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center px-4">
      <div className="w-14 h-14 bg-primary-50 rounded-2xl flex items-center justify-center mb-4">
        <Wallet className="w-7 h-7 text-primary-500" aria-hidden="true" />
      </div>
      <p className="text-base font-semibold text-gray-800 mb-1">No expenses in this period</p>
      <p className="text-sm text-gray-500 mb-5 max-w-xs">Add an expense to start seeing totals and charts for your spending.</p>
      <button
        type="button" onClick={onAdd}
        className="inline-flex items-center gap-1.5 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2.5 rounded-lg transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 focus-visible:ring-offset-2"
      >
        <Plus className="w-4 h-4" aria-hidden="true" /> Add your first expense
      </button>
    </div>
  )
}

export default function ExpenseList({
  expenses, loading, error, onRetry, catMap, currency,
  page, totalPages, totalElements, onPage,
  filtered, onAdd, onClear, onEdit, onDelete,
}) {
  if (error && !expenses) return <ErrorState message={error.message || 'Could not load expenses.'} onRetry={onRetry} />
  if (!expenses) return <ExpenseRowSkeleton />
  if (expenses.length === 0) {
    return loading ? <ExpenseRowSkeleton /> : <EmptyState filtered={filtered} onAdd={onAdd} onClear={onClear} />
  }

  return (
    <>
      {error && <ErrorState compact message={error.message || 'Could not refresh expenses.'} onRetry={onRetry} />}
      <ul className={`divide-y divide-gray-50 transition-opacity ${loading ? 'opacity-50' : ''}`} aria-busy={loading}>
        {expenses.map((exp) => {
          const cat = catMap[exp.categoryId]
          return (
            <li key={exp.id} className="flex items-center justify-between px-5 py-3.5 hover:bg-gray-50 transition-colors">
              <div className="flex items-center gap-3 min-w-0">
                <div
                  className="w-8 h-8 rounded-lg flex items-center justify-center text-sm flex-shrink-0"
                  style={{ backgroundColor: cat?.color ? `${cat.color}22` : '#f3f4f6' }}
                  aria-hidden="true"
                >
                  {cat?.icon || '💸'}
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{exp.description}</p>
                  <div className="flex items-center gap-2 mt-0.5 min-w-0">
                    <span className="text-xs text-gray-400 flex-shrink-0">{formatDisplayDate(exp.date)}</span>
                    {(exp.categoryName || cat?.name) && (
                      <span className="text-xs px-1.5 py-0.5 rounded-full bg-gray-100 text-gray-600 truncate">
                        {exp.categoryName || cat?.name}
                      </span>
                    )}
                  </div>
                  {exp.notes && <p className="text-xs text-gray-400 truncate mt-0.5">{exp.notes}</p>}
                </div>
              </div>
              <div className="flex items-center gap-3 flex-shrink-0 ml-3">
                <span className="text-sm font-semibold text-gray-900">{formatMoney(exp.amount, currency)}</span>
                <button
                  type="button"
                  onClick={() => onEdit(exp)}
                  aria-label={`Edit expense ${exp.description}`}
                  className="p-1 rounded text-gray-400 hover:text-primary-600 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                >
                  <Pencil className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={() => onDelete(exp)}
                  aria-label={`Delete expense ${exp.description}`}
                  className="p-1 rounded text-gray-400 hover:text-red-500 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-red-500"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </li>
          )
        })}
      </ul>

      {totalPages > 1 && (
        <nav aria-label="Expenses pagination" className="flex items-center justify-between px-5 py-3 border-t border-gray-100">
          <span className="text-xs text-gray-400" aria-live="polite">
            Page {page + 1} of {totalPages} · {totalElements} total
          </span>
          <div className="flex gap-1">
            <button
              type="button"
              onClick={() => onPage(page - 1)} disabled={page === 0}
              aria-label="Previous page"
              className="p-1.5 rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={() => onPage(page + 1)} disabled={page >= totalPages - 1}
              aria-label="Next page"
              className="p-1.5 rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </nav>
      )}
    </>
  )
}
