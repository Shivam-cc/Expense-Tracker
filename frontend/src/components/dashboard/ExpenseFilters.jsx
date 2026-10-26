import { Search, X } from 'lucide-react'
import { SORT_OPTIONS } from '../../hooks/useExpenseFilters'

const selectCls =
  'px-3 py-2 border border-gray-300 bg-white rounded-lg text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500'

export default function ExpenseFilters({ searchInput, onSearchChange, categoryId, onCategory, sortValue, onSort, categories, isModified, onReset }) {
  return (
    <div className="px-5 py-3 border-b border-gray-100 flex flex-col md:flex-row gap-3 md:items-center">
      <div className="relative flex-1">
        <label htmlFor="expense-search" className="sr-only">Search expenses</label>
        <Search className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" aria-hidden="true" />
        <input
          id="expense-search"
          type="search"
          value={searchInput}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Search description or notes…"
          className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
        />
      </div>
      <div className="flex flex-wrap gap-3">
        <div>
          <label htmlFor="filter-category" className="sr-only">Filter by category</label>
          <select id="filter-category" value={categoryId} onChange={(e) => onCategory(e.target.value)} className={selectCls}>
            <option value="">All categories</option>
            <option value="0">Uncategorized</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="filter-sort" className="sr-only">Sort expenses</label>
          <select id="filter-sort" value={sortValue} onChange={(e) => onSort(e.target.value)} className={selectCls}>
            {SORT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </select>
        </div>
        {isModified && (
          <button
            type="button"
            onClick={onReset}
            className="inline-flex items-center gap-1 px-3 py-2 text-sm text-gray-600 hover:text-gray-900 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
          >
            <X className="w-4 h-4" aria-hidden="true" /> Clear
          </button>
        )}
      </div>
    </div>
  )
}
